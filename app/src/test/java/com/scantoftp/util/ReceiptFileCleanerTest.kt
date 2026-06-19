package com.scantoftp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReceiptFileCleanerTest {
    @Test
    fun `deletePaths removes existing files`() {
        val file1 = File.createTempFile("cleaner_test_1", ".txt")
        val file2 = File.createTempFile("cleaner_test_2", ".txt")

        assertTrue(file1.exists())
        assertTrue(file2.exists())

        ReceiptFileCleaner.deletePaths(file1.absolutePath, file2.absolutePath)

        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun `deletePaths ignores null or blank paths`() {
        // No exception should be thrown
        ReceiptFileCleaner.deletePaths(null, "", "   ")
    }

    @Test
    fun `deletePaths handles non-existent file paths gracefully`() {
        val file = File("some_random_non_existent_file_path_xyz.txt")
        assertFalse(file.exists())

        // No exception should be thrown
        ReceiptFileCleaner.deletePaths(file.absolutePath)
    }
}
