package com.scantoftp.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FtpPathNormalizerTest {
    @Test
    fun `normalizes base and trip into a stable remote path`() {
        val normalized = FtpPathNormalizer.normalize("\\receipts\\travel\\", "paris/june")

        assertEquals("/receipts/travel/paris/june", normalized)
    }

    @Test
    fun `preserves absolute root based paths`() {
        val normalized = FtpPathNormalizer.normalize("/receipts", "paris-june")

        assertEquals("/receipts/paris-june", normalized)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects traversal segments`() {
        FtpPathNormalizer.normalize("/receipts", "../escape")
    }
}
