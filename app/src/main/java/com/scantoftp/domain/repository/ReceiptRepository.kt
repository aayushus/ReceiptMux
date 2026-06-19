package com.scantoftp.domain.repository

import com.scantoftp.domain.model.ReceiptDraft
import com.scantoftp.domain.model.ScannedReceipt
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    fun observeQueue(): Flow<List<ScannedReceipt>>
    suspend fun enqueueReceipt(draft: ReceiptDraft): Long
    suspend fun updateFilename(receiptId: Long, newFilename: String)
    suspend fun markUploading(receiptId: Long)
    suspend fun markCompleted(receiptId: Long)
    suspend fun markFailed(receiptId: Long, message: String)
    suspend fun retryFailed()
    suspend fun retry(receiptId: Long)
    suspend fun clearCompleted()
    suspend fun delete(receiptId: Long)
    suspend fun getPendingCount(): Int
}
