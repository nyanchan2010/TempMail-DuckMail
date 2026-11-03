package com.duckmail.tempmail

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Mailbox passwords + tokens are stored as ONE AES/GCM blob in Android Keystore.
 * Nothing secret is stored in plain SharedPreferences, logs or Room.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tempmail_vault", Context.MODE_PRIVATE)
    private val alias = "tempmail_key"

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = iv + out
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = combined.copyOfRange(0, 12)
            val data = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveState(mailboxes: List<Mailbox>, messagesByMailbox: Map<String, List<EmailMessage>>, settings: AppSettings, activeId: String?) {
        val root = JSONObject().apply {
            val mArr = JSONArray()
            mailboxes.forEach { mArr.put(it.toJson()) }
            put("mailboxes", mArr)
            val msgObj = JSONObject()
            messagesByMailbox.forEach { (k, v) ->
                val arr = JSONArray()
                v.forEach { arr.put(it.toJson()) }
                msgObj.put(k, arr)
            }
            put("messages", msgObj)
            put("settings", settings.toJson())
            put("activeId", activeId ?: JSONObject.NULL)
        }
        val enc = encrypt(root.toString())
        if (enc != null) prefs.edit().putString("vault", enc).apply()
    }

    data class LoadedState(
        val mailboxes: List<Mailbox>,
        val messagesByMailbox: Map<String, List<EmailMessage>>,
        val settings: AppSettings,
        val activeId: String?,
    )

    fun loadState(): LoadedState {
        val enc = prefs.getString("vault", null) ?: return LoadedState(emptyList(), emptyMap(), AppSettings(), null)
        val plain = decrypt(enc) ?: return LoadedState(emptyList(), emptyMap(), AppSettings(), null)
        return try {
            val root = JSONObject(plain)
            val mailboxes = root.optJSONArray("mailboxes")?.let { jsonArrayToMailboxes(it) } ?: emptyList()
            val msgMap = mutableMapOf<String, List<EmailMessage>>()
            val msgObj = root.optJSONObject("messages")
            if (msgObj != null) {
                val keys = msgObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    msgMap[k] = msgObj.optJSONArray(k)?.let { jsonArrayToMessages(it) } ?: emptyList()
                }
            }
            LoadedState(
                mailboxes = mailboxes,
                messagesByMailbox = msgMap,
                settings = AppSettings.fromJson(root.optJSONObject("settings")),
                activeId = root.optString("activeId", "").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            LoadedState(emptyList(), emptyMap(), AppSettings(), null)
        }
    }
}
