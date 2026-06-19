package com.scantoftp.domain.service

data class ProcessingResult(
    val processedPath: String,
    val cropConfidence: Float,
)

interface ReceiptProcessor {
    suspend fun process(inputPath: String): ProcessingResult
}
