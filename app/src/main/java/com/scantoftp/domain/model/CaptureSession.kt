package com.scantoftp.domain.model

data class CaptureSession(
    val originalPath: String,
    val processedPath: String,
    val suggestedFileName: String,
    val vendor: String,
    val amount: String,
    val receiptDate: String,
    val captureTimestamp: Long,
    val cropConfidence: Float,
)
