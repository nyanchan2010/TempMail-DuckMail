package com.duckmail.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun extractsKeywordAnchoredCode() {
        assertEquals(
            "482913",
            OtpExtractor.extract("Your verification code", "", "Your verification code is 482913"),
        )
    }

    @Test
    fun rejectsYears() {
        assertNull(
            OtpExtractor.extract("Welcome", "", "Copyright 2025, your code is not here"),
        )
        // "2025" alone with a code keyword is still a year, not a code.
        assertNull(
            OtpExtractor.extract("Code", "", "Your code is 2025"),
        )
    }

    @Test
    fun rejectsRandomNumbersWithoutKeyword() {
        assertNull(
            OtpExtractor.extract("Order update", "", "Your order 123456 shipped today"),
        )
    }

    @Test
    fun extractsVerificationLinkOnlyWhenGenuine() {
        val link = "https://example.com/verify?token=abcdef123456&email=x@y.com"
        assertEquals(
            link,
            OtpExtractor.extractVerificationLink("Click here: $link", ""),
        )
        assertNull(
            OtpExtractor.extractVerificationLink("See https://example.com/logo.png", ""),
        )
    }
}
