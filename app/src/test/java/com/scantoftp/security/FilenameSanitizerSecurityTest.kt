package com.scantoftp.security

import com.scantoftp.util.ReceiptFilenameSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameSanitizerSecurityTest {

    @Test
    fun `handles simple empty or whitespace-only inputs`() {
        val fallbackTimestamp = 1718820000000L // arbitrary fixed timestamp
        val sanitized = ReceiptFilenameSanitizer.sanitize("    ", fallbackTimestamp)
        
        // Should fall back to the default UnknownVendor filename based on the timestamp
        assertTrue(sanitized.endsWith("_UnknownVendor_0.00.jpg"))
        assertFalse(sanitized.contains(" "))
    }

    @Test
    fun `prevents path traversal directory escape`() {
        val traversals = listOf(
            "../../../../etc/passwd",
            "..\\..\\..\\..\\Windows\\System32\\cmd.exe",
            "a/../../b",
            "/absolute/path/to/malicious/file.jpg",
            "C:\\Users\\admin\\Desktop\\hack.exe"
        )

        traversals.forEach { input ->
            val sanitized = ReceiptFilenameSanitizer.sanitize(input)
            // Verify path separators are stripped or replaced with safe underscores
            assertFalse("Failed / check for: $input", sanitized.contains("/"))
            assertFalse("Failed \\ check for: $input", sanitized.contains("\\"))
            // And thus, it cannot escape the containing directory because it's treated as a single filename
            assertTrue("Failed extension check for: $input", sanitized.endsWith(".jpg"))
        }
    }

    @Test
    fun `strips or replaces control characters and null bytes`() {
        val malicious = "receipt\u0000with\u001Fcontrol\ncharacters\rhere\t.jpg"
        val sanitized = ReceiptFilenameSanitizer.sanitize(malicious)

        assertFalse(sanitized.contains("\u0000"))
        assertFalse(sanitized.contains("\u001F"))
        assertFalse(sanitized.contains("\n"))
        assertFalse(sanitized.contains("\r"))
        assertFalse(sanitized.contains("\t"))
        assertTrue(sanitized.endsWith(".jpg"))
        // Consecutive unsafe characters should be replaced and compacted to single underscores
        assertTrue(sanitized.contains("receipt_with_control_characters_here"))
    }

    @Test
    fun `neutralizes SQL injection payloads in filenames`() {
        val sqliPayloads = listOf(
            "' OR '1'='1",
            "1; DROP TABLE receipts;--",
            "admin' --",
            "UNION SELECT username, password FROM users"
        )

        sqliPayloads.forEach { input ->
            val sanitized = ReceiptFilenameSanitizer.sanitize(input)
            // SQL injection characters like quotes, semicolons, and spaces must be neutralized
            assertFalse("Failed SQLi quote check: $input", sanitized.contains("'"))
            assertFalse("Failed SQLi semicolon check: $input", sanitized.contains(";"))
            assertFalse("Failed SQLi space check: $input", sanitized.contains(" "))
            assertTrue("Failed SQLi extension check: $input", sanitized.endsWith(".jpg"))
        }
    }

    @Test
    fun `neutralizes shell injection and command execution characters`() {
        val shellPayloads = listOf(
            "receipt_&_rm_-rf_/",
            "receipt_$(whoami)",
            "receipt_`reboot`",
            "receipt_&&_ls",
            "receipt_|_cat_passwd"
        )

        shellPayloads.forEach { input ->
            val sanitized = ReceiptFilenameSanitizer.sanitize(input)
            // Shell metacharacters must be safely removed or mapped to underscores
            assertFalse("Failed Shell & check: $input", sanitized.contains("&"))
            assertFalse("Failed Shell \$ check: $input", sanitized.contains("$"))
            assertFalse("Failed Shell ` check: $input", sanitized.contains("`"))
            assertFalse("Failed Shell | check: $input", sanitized.contains("|"))
            assertTrue("Failed Shell extension check: $input", sanitized.endsWith(".jpg"))
        }
    }

    @Test
    fun `safely handles cross-site scripting HTML injection`() {
        val xssPayloads = listOf(
            "<script>alert('hack')</script>",
            "<img src=x onerror=alert(1)>",
            "javascript:alert(1)"
        )

        xssPayloads.forEach { input ->
            val sanitized = ReceiptFilenameSanitizer.sanitize(input)
            // Tags (<, >) and colons must be fully neutralized
            assertFalse("Failed XSS < check: $input", sanitized.contains("<"))
            assertFalse("Failed XSS > check: $input", sanitized.contains(">"))
            assertFalse("Failed XSS : check: $input", sanitized.contains(":"))
            assertTrue("Failed XSS extension check: $input", sanitized.endsWith(".jpg"))
        }
    }

    @Test
    fun `truncates extremely long names to protect against buffer overflow or filename limits`() {
        val superLongName = "a".repeat(1000)
        val sanitized = ReceiptFilenameSanitizer.sanitize(superLongName)

        // Filename base should be capped to 80 characters + 4 characters for ".jpg" = 84 characters total
        assertEquals(84, sanitized.length)
        assertTrue(sanitized.endsWith(".jpg"))
    }

    @Test
    fun `preserves valid letters numbers and safe symbols including international names`() {
        // Cyrillic, accented, and safe symbols should be resolved safely without breaking
        val input = "Café_München_Receipt-2026_№42.jpg"
        val sanitized = ReceiptFilenameSanitizer.sanitize(input)

        assertTrue(sanitized.endsWith(".jpg"))
        // Café and München are preserved as valid letter sequences!
        assertTrue(sanitized.contains("Café"))
        assertTrue(sanitized.contains("München"))
    }

    @Test
    fun `handles dotted dot-dot directory files securely`() {
        val dots = listOf(".", "..")
        val fallbackTimestamp = 1718820000000L

        dots.forEach { input ->
            val sanitized = ReceiptFilenameSanitizer.sanitize(input, fallbackTimestamp)
            // Dots alone represent directories, sanitizer must fallback
            assertTrue(sanitized.contains("UnknownVendor"))
            assertTrue(sanitized.endsWith(".jpg"))
        }
    }
}
