package com.duckmail.tempmail

/**
 * Strict OTP / verification-code extractor.
 * Sources: subject + intro + plain text body only — never raw HTML markup.
 * Keyword-anchored first; years and CSS hex values are rejected.
 */
object OtpExtractor {

    private val keywordRegex = Regex(
        "(?:verification|verify|code|otp|pin|passcode|security\\s+code|confirmation\\s+code)" +
            "\\s*(?:is|:|\\-)?\\s*\\b(\\d{4,8})\\b",
        RegexOption.IGNORE_CASE,
    )

    private val urlRegex = Regex(
        "https?://[^\\s\"'<>]+",
        RegexOption.IGNORE_CASE,
    )

    private val securityParamRegex = Regex(
        "[?&](token|code|key|hash|ticket|otp|signature|verification_code)=[A-Za-z0-9\\-_\\.]{6,}",
    )

    private val verifyKeywordRegex = Regex(
        "verif|confirm|activate|reset|login|sign",
        RegexOption.IGNORE_CASE,
    )

    private val staticAssetRegex = Regex(
        "\\.(png|jpg|jpeg|gif|webp|svg|css|js|ico|woff2?)(\\?|$)",
        RegexOption.IGNORE_CASE,
    )

    fun extract(subject: String, intro: String, textBody: String): String? {
        val source = "$subject\n$intro\n$textBody"
        val match = keywordRegex.find(source) ?: return null
        val code = match.groupValues[1]
        if (isYear(code)) return null
        if (code.length == 4 && isLikelyYearFragment(source, match.range.first)) return null
        return code
    }

    private fun isYear(code: String): Boolean {
        if (code.length != 4) return false
        val year = code.toIntOrNull() ?: return false
        return year in 1900..2099
    }

    private fun isLikelyYearFragment(source: String, index: Int): Boolean {
        // A 4-digit code directly preceded by a date separator is a date, not a code.
        if (index <= 0) return false
        val before = source.getOrNull(index - 1)
        return before == '/' || before == '-'
    }

    fun extractVerificationLink(textBody: String, htmlBody: String): String? {
        val combined = "$textBody\n$htmlBody"
        val candidates = urlRegex.findAll(combined).map { it.value.trimEnd('.', ',', ')') }.toList()
        for (url in candidates) {
            val lower = url.lowercase()
            if (staticAssetRegex.containsMatchIn(lower)) continue
            if (lower.length <= 36) continue
            if (!verifyKeywordRegex.containsMatchIn(lower)) continue
            if (!securityParamRegex.containsMatchIn(lower)) continue
            return url
        }
        return null
    }
}
