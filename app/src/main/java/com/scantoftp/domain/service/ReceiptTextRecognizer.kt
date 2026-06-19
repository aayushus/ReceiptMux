package com.scantoftp.domain.service

data class OcrResult(
    val vendor: String,
    val amount: String,
    val receiptDate: String,
    val suggestedFilename: String,
)

interface ReceiptTextRecognizer {
    suspend fun recognize(processedPath: String, captureTimestamp: Long): OcrResult
}
