package com.scantoftp.security

import com.scantoftp.util.FtpPathNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathNormalizerSecurityTest {

    @Test
    fun `normalizes base and trip directories with typical inputs`() {
        val result = FtpPathNormalizer.normalize("/receipts", "paris-2026")
        assertEquals("/receipts/paris-2026", result)
    }

    @Test
    fun `normalizes multiple consecutive slashes and backslashes`() {
        val result = FtpPathNormalizer.normalize("\\\\scans///receipts\\\\\\", "///trip//june///")
        // Backslashes should be mapped to slashes, duplicates consolidated, and trailing slashes trimmed safely
        assertEquals("/scans/receipts/trip/june", result)
    }

    @Test
    fun `handles empty or blank directories gracefully`() {
        assertEquals("", FtpPathNormalizer.normalize("   ", "   "))
        assertEquals("/receipts", FtpPathNormalizer.normalize("/receipts", ""))
        assertEquals("trip-sub", FtpPathNormalizer.normalize("", "trip-sub"))
    }

    @Test
    fun `segments extracts non-blank segments correctly`() {
        val segments = FtpPathNormalizer.segments("/scans///receipts/trip/")
        assertEquals(listOf("scans", "receipts", "trip"), segments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects base directory containing dot-dot traversal`() {
        FtpPathNormalizer.normalize("../escape", "trip")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects trip subfolder containing dot-dot traversal`() {
        FtpPathNormalizer.normalize("/receipts", "../../passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects path with single dot directory segments`() {
        FtpPathNormalizer.normalize("/receipts/./scans", "trip")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects path containing hidden dot-dot segment in the middle`() {
        FtpPathNormalizer.normalize("/scans/sub/../etc", "trip")
    }

    @Test
    fun `handles root base directory correctly`() {
        val result = FtpPathNormalizer.normalize("/", "trip")
        assertEquals("/trip", result)
    }
}
