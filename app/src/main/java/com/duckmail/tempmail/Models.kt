package com.duckmail.tempmail

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Mailbox(
    val id: String,
    val address: String,
    val password: String,
    val token: String,
    val label: String,
    val createdAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("address", address)
        put("password", password)
        put("token", token)
        put("label", label)
        put("createdAtMs", createdAtMs)
    }

    companion object {
        fun fromJson(o: JSONObject): Mailbox = Mailbox(
            id = o.optString("id"),
            address = o.optString("address"),
            password = o.optString("password"),
            token = o.optString("token"),
            label = o.optString("label", "Primary"),
            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
        )
    }
}

data class EmailMessage(
    val id: String,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val intro: String,
    var textBody: String,
    var htmlBody: String,
    val receivedAtMs: Long,
    var isRead: Boolean,
    var isFullLoaded: Boolean = false,
) {
    val otp: String? get() = OtpExtractor.extract(subject, intro, textBody)
    val verificationLink: String? get() = OtpExtractor.extractVerificationLink(textBody, htmlBody)

    fun fingerprint(): String = "$id|${if (isRead) 1 else 0}|${otp ?: ""}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fromName", fromName)
        put("fromAddress", fromAddress)
        put("subject", subject)
        put("intro", intro)
        put("textBody", textBody)
        put("htmlBody", htmlBody)
        put("receivedAtMs", receivedAtMs)
        put("isRead", isRead)
        put("isFullLoaded", isFullLoaded)
    }

    companion object {
        fun fromJson(o: JSONObject): EmailMessage = EmailMessage(
            id = o.optString("id"),
            fromName = o.optString("fromName"),
            fromAddress = o.optString("fromAddress"),
            subject = o.optString("subject"),
            intro = o.optString("intro"),
            textBody = o.optString("textBody"),
            htmlBody = o.optString("htmlBody"),
            receivedAtMs = o.optLong("receivedAtMs", System.currentTimeMillis()),
            isRead = o.optBoolean("isRead", false),
            isFullLoaded = o.optBoolean("isFullLoaded", false),
        )
    }
}

data class AppSettings(
    val pollSeconds: Int = 3,
    val autoCopyOtp: Boolean = true,
    val backgroundWatching: Boolean = false,
    val hideOtpOnLockScreen: Boolean = true,
    val darkTheme: Int = 0, // 0 system, 1 light, 2 dark
    val dynamicColor: Boolean = true,
    val language: String = "en", // en | ar
    val apiKey: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pollSeconds", pollSeconds)
        put("autoCopyOtp", autoCopyOtp)
        put("backgroundWatching", backgroundWatching)
        put("hideOtpOnLockScreen", hideOtpOnLockScreen)
        put("darkTheme", darkTheme)
        put("dynamicColor", dynamicColor)
        put("language", language)
        put("apiKey", apiKey)
    }

    companion object {
        fun fromJson(o: JSONObject?): AppSettings {
            if (o == null) return AppSettings()
            return AppSettings(
                pollSeconds = o.optInt("pollSeconds", 3).coerceIn(2, 10),
                autoCopyOtp = o.optBoolean("autoCopyOtp", true),
                backgroundWatching = o.optBoolean("backgroundWatching", false),
                hideOtpOnLockScreen = o.optBoolean("hideOtpOnLockScreen", true),
                darkTheme = o.optInt("darkTheme", 0),
                dynamicColor = o.optBoolean("dynamicColor", true),
                language = o.optString("language", "en"),
                apiKey = o.optString("apiKey", ""),
            )
        }
    }
}

fun parseIsoToMs(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )
    for (f in formats) {
        try {
            val sdf = SimpleDateFormat(f, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(value)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
        }
    }
    return System.currentTimeMillis()
}

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0)
    val min = diff / 60_000
    val hr = diff / 3_600_000
    val day = diff / 86_400_000
    return when {
        min < 1 -> "now"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day < 7 -> "${day}d ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))
    }
}

fun jsonArrayToMailboxes(arr: JSONArray): List<Mailbox> {
    val out = mutableListOf<Mailbox>()
    for (i in 0 until arr.length()) {
        try { out.add(Mailbox.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
    }
    return out
}

fun jsonArrayToMessages(arr: JSONArray): List<EmailMessage> {
    val out = mutableListOf<EmailMessage>()
    for (i in 0 until arr.length()) {
        try { out.add(EmailMessage.fromJson(arr.getJSONObject(i))) } catch (_: Exception) {}
    }
    return out
}
