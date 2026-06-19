package com.scantoftp.domain.model

data class ReceiptDraft(
    val originalPath: String,
    val processedPath: String,
    val suggestedFileName: String,
    val vendor: String,
    val amount: String,
    val receiptDate: String,
)
