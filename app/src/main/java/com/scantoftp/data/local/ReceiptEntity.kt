package com.scantoftp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,
    val processedPath: String,
    val fileName: String,
    val vendor: String,
    val amount: String,
    val receiptDate: String,
    val createdAt: Long,
    val status: UploadStatus,
    val errorMessage: String?,
)

fun ReceiptEntity.toDomain(): ScannedReceipt = ScannedReceipt(
    id = id,
    localPath = localPath,
    processedPath = processedPath,
    fileName = fileName,
    vendor = vendor,
    amount = amount,
    receiptDate = receiptDate,
    createdAt = createdAt,
    status = status,
    errorMessage = errorMessage,
)
