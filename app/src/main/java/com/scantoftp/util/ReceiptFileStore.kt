package com.scantoftp.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptFileStore {
    fun createRawCaptureFile(context: Context, timestamp: Long = System.currentTimeMillis()): File {
        return createFile(context = context, folder = "raw", prefix = "receipt_$timestamp", suffix = ".jpg")
    }

    fun createProcessedFile(context: Context, timestamp: Long = System.currentTimeMillis()): File {
        return createFile(context = context, folder = "processed", prefix = "receipt_$timestamp", suffix = ".jpg")
    }

    fun createFileNamePrefix(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM_dd", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun createFile(context: Context, folder: String, prefix: String, suffix: String): File {
        val directory = File(context.filesDir, folder).apply { mkdirs() }
        return File(directory, "$prefix$suffix")
    }
}
