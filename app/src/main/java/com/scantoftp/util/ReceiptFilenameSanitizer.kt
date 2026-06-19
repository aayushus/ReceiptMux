package com.scantoftp.util

object ReceiptFilenameSanitizer {
    private val whitespaceRegex = Regex("\\s+")
    private val invalidCharacterRegex = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val unsupportedCharacterRegex = Regex("""[^\p{L}\p{N}._-]""")
    private val repeatedUnderscoreRegex = Regex("_+")

    fun sanitize(input: String, fallbackTimestamp: Long = System.currentTimeMillis()): String {
        val fallbackBase = ReceiptFileStore.createFileNamePrefix(fallbackTimestamp) + "_UnknownVendor_0.00"
        val baseName = input
            .trim()
            .removeSuffix(".jpg")
            .removeSuffix(".JPG")
            .removeSuffix(".jpeg")
            .removeSuffix(".JPEG")
            .ifBlank { fallbackBase }

        val sanitized = baseName
            .replace(invalidCharacterRegex, "_")
            .replace(whitespaceRegex, "_")
            .replace(unsupportedCharacterRegex, "_")
            .replace(repeatedUnderscoreRegex, "_")
            .trim('_', '.', ' ')
            .take(80)
            .ifBlank { fallbackBase }
            .let {
                when (it) {
                    ".", ".." -> fallbackBase
                    else -> it
                }
            }

        return "$sanitized.jpg"
    }
}
