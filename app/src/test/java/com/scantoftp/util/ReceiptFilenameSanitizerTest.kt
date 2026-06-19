package com.scantoftp.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptFilenameSanitizerTest {
    @Test
    fun `keeps standard receipt filename intact`() {
        val sanitized = ReceiptFilenameSanitizer.sanitize("Mar_29_Starbucks_4.75.jpg", 0L)

        assertEquals("Mar_29_Starbucks_4.75.jpg", sanitized)
    }

    @Test
    fun `replaces unsafe characters and appends jpg extension`() {
        val sanitized = ReceiptFilenameSanitizer.sanitize(" Mar 29 / Lounge:Receipt? ", 0L)

        assertEquals("Mar_29_Lounge_Receipt.jpg", sanitized)
    }

    @Test
    fun `falls back when the edited filename is blank`() {
        val timestamp = LocalDate.of(2026, 1, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val sanitized = ReceiptFilenameSanitizer.sanitize("   ", timestamp)

        assertEquals("${ReceiptFileStore.createFileNamePrefix(timestamp)}_UnknownVendor_0.00.jpg", sanitized)
    }
}
