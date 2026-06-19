package com.scantoftp.domain.model

data class ScannedReceipt(
    val id: Long = 0,
    val localPath: String,
    val processedPath: String,
    val fileName: String,
    val vendor: String,
    val amount: String,
    val receiptDate: String,
    val createdAt: Long,
    val status: UploadStatus,
    val errorMessage: String? = null,
)
