package com.duckmail.tempmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DuckMailException(message: String, val status: Int = -1) : Exception(message)

/**
 * Direct client for https://api.duckmail.sbs only.
 * No website scraping, no /api/mail or /api/sse web routes, no other provider.
 */
class DuckMailApi(private val apiKey: String = "") {

    private val baseUrl = "https://api.duckmail.sbs"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private fun headers(token: String? = null): okhttp3.Headers.Builder {
        val b = okhttp3.Headers.Builder()
            .add("Accept", "application/json")
            .add("Accept-Encoding", "gzip")
        val bearer = token?.takeIf { it.isNotBlank() } ?: apiKey.takeIf { it.isNotBlank() }
        if (bearer != null) b.add("Authorization", "Bearer $bearer")
        return b
    }

    private fun get(url: String, token: String? = null): Pair<Int, String> {
        val req = Request.Builder().url(url).headers(headers(token).build()).get().build()
        client.newCall(req).execute().use { res ->
            return res.code to (res.body?.string() ?: "")
        }
    }

    private fun post(url: String, json: String, token: String? = null): Pair<Int, String> {
        val req = Request.Builder().url(url).headers(headers(token).build())
            .post(json.toRequestBody(jsonMedia)).build()
        client.newCall(req).execute().use { res ->
            return res.code to (res.body?.string() ?: "")
        }
    }

    private fun patch(url: String, json: String, token: String): Int {
        val req = Request.Builder().url(url).headers(headers(token).build())
            .patch(json.toRequestBody(jsonMedia)).build()
        client.newCall(req).execute().use { res -> return res.code }
    }

    private fun delete(url: String, token: String) {
        val req = Request.Builder().url(url).headers(headers(token).build()).delete().build()
        try { client.newCall(req).execute().close() } catch (_: Exception) {}
    }

    private fun memberArray(body: String): JSONArray {
        if (body.isBlank()) return JSONArray()
        val obj = JSONObject(body)
        return when {
            obj.has("hydra:member") -> obj.getJSONArray("hydra:member")
            obj.has("messages") -> obj.optJSONArray("messages") ?: JSONArray()
            obj.has("domains") -> obj.optJSONArray("domains") ?: JSONArray()
            obj.has("data") && obj.opt("data") is JSONArray -> obj.getJSONArray("data")
            else -> JSONArray()
        }
    }

    suspend fun fetchDomains(): List<String> = withContext(Dispatchers.IO) {
        try {
            val (code, body) = get("$baseUrl/domains")
            if (code != 200) return@withContext listOf("duckmail.sbs")
            val arr = memberArray(body)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val d = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("domain", "duckmail.sbs")
                    else -> "duckmail.sbs"
                }
                if (d.isNotBlank()) out.add(d)
            }
            if (out.isEmpty()) listOf("duckmail.sbs") else out.distinct()
        } catch (_: Exception) {
            listOf("duckmail.sbs")
        }
    }

    suspend fun createAccount(address: String, password: String, label: String): Mailbox =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("address", address)
                put("password", password)
            }.toString()
            val (code, resp) = post("$baseUrl/accounts", body)
            if (code != 200 && code != 201) {
                throw DuckMailException("Create account failed (HTTP $code)", code)
            }
            val accId = try { JSONObject(resp).optString("id", "") } catch (_: Exception) { "" }
            login(address, password, label, accId)
        }

    suspend fun login(address: String, password: String, label: String = "Imported", knownId: String = ""): Mailbox =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("address", address)
                put("password", password)
            }.toString()
            val (code, resp) = post("$baseUrl/token", body)
            if (code != 200 && code != 201) {
                throw DuckMailException("Invalid credentials (HTTP $code)", code)
            }
            val obj = JSONObject(resp)
            val token = obj.optString("token", "")
            if (token.isBlank()) throw DuckMailException("Empty token from server", code)
            val id = knownId.ifBlank { obj.optString("id", System.currentTimeMillis().toString()) }
            Mailbox(id = id, address = address, password = password, token = token, label = label, createdAtMs = System.currentTimeMillis())
        }

    suspend fun fetchMessages(mailbox: Mailbox): List<EmailMessage> = withContext(Dispatchers.IO) {
        val (code, body) = get("$baseUrl/messages?page=1", mailbox.token)
        if (code != 200) throw DuckMailException("Messages failed (HTTP $code)", code)
        val arr = memberArray(body)
        val out = mutableListOf<EmailMessage>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val from = m.optJSONObject("from")
            val fromName = from?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: from?.optString("address", "Unknown") ?: "Unknown"
            val fromAddress = from?.optString("address", "unknown@duckmail.sbs") ?: "unknown@duckmail.sbs"
            val htmlRaw = m.opt("html")
            val html = when (htmlRaw) {
                is JSONArray -> (0 until htmlRaw.length()).joinToString("\n") { htmlRaw.optString(it) }
                is String -> htmlRaw
                else -> ""
            }
            out.add(
                EmailMessage(
                    id = m.optString("id"),
                    fromName = fromName,
                    fromAddress = fromAddress,
                    subject = m.optString("subject", "").ifBlank { "(No subject)" },
                    intro = m.optString("intro", ""),
                    textBody = m.optString("text", "").ifBlank { m.optString("intro", "") },
                    htmlBody = html,
                    receivedAtMs = parseIsoToMs(m.optString("createdAt", "").ifBlank { null }),
                    isRead = m.optBoolean("seen", false),
                ),
            )
        }
        out
    }

    suspend fun fetchDetail(mailbox: Mailbox, summary: EmailMessage): EmailMessage =
        withContext(Dispatchers.IO) {
            try {
                patch("$baseUrl/messages/${summary.id}", "{\"seen\": true}", mailbox.token)
            } catch (_: Exception) {}
            try {
                val (code, body) = get("$baseUrl/messages/${summary.id}", mailbox.token)
                if (code == 200) {
                    val m = JSONObject(body)
                    summary.textBody = m.optString("text", summary.textBody.ifBlank { summary.intro })
                    val htmlRaw = m.opt("html")
                    summary.htmlBody = when (htmlRaw) {
                        is JSONArray -> (0 until htmlRaw.length()).joinToString("\n") { htmlRaw.optString(it) }
                        is String -> htmlRaw
                        else -> summary.htmlBody
                    }
                    summary.isFullLoaded = true
                }
            } catch (_: Exception) {}
            summary.isRead = true
            summary
        }

    suspend fun deleteMessage(mailbox: Mailbox, messageId: String) = withContext(Dispatchers.IO) {
        delete("$baseUrl/messages/$messageId", mailbox.token)
    }

    suspend fun deleteAccount(mailbox: Mailbox) = withContext(Dispatchers.IO) {
        delete("$baseUrl/accounts/${mailbox.id}", mailbox.token)
    }
}
