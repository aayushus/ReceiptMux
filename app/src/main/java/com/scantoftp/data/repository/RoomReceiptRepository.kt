package com.scantoftp.data.repository

import com.scantoftp.data.local.ReceiptDao
import com.scantoftp.data.local.ReceiptEntity
import com.scantoftp.data.local.toDomain
import com.scantoftp.domain.model.ReceiptDraft
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.notification.UploadNotificationManager
import com.scantoftp.util.ReceiptFileCleaner
import com.scantoftp.util.ReceiptFilenameSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReceiptRepository @Inject constructor(
    private val dao: ReceiptDao,
    private val uploadNotificationManager: UploadNotificationManager,
) : ReceiptRepository {

    override fun observeQueue(): Flow<List<ScannedReceipt>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun enqueueReceipt(draft: ReceiptDraft): Long {
        val createdAt = System.currentTimeMillis()
        val sanitizedFileName = uniqueFileName(
            ReceiptFilenameSanitizer.sanitize(draft.suggestedFileName, createdAt),
        )
        val id = dao.insert(
            ReceiptEntity(
                localPath = draft.originalPath,
                processedPath = draft.processedPath,
                fileName = sanitizedFileName,
                vendor = draft.vendor,
                amount = draft.amount,
                receiptDate = draft.receiptDate,
                createdAt = createdAt,
                status = UploadStatus.Pending,
                errorMessage = null,
            ),
        )
        refreshBadge()
        return id
    }

    override suspend fun updateFilename(receiptId: Long, newFilename: String) {
        val existing = dao.getById(receiptId) ?: return
        val sanitized = uniqueFileName(
            ReceiptFilenameSanitizer.sanitize(newFilename, existing.createdAt),
            excludedReceiptId = receiptId,
        )
        dao.update(existing.copy(fileName = sanitized))
    }

    override suspend fun markUploading(receiptId: Long) {
        val existing = dao.getById(receiptId) ?: return
        dao.update(existing.copy(status = UploadStatus.Uploading, errorMessage = null))
        refreshBadge()
    }

    override suspend fun markCompleted(receiptId: Long) {
        val existing = dao.getById(receiptId) ?: return
        dao.update(existing.copy(status = UploadStatus.Completed, errorMessage = null))
        refreshBadge()
    }

    override suspend fun markFailed(receiptId: Long, message: String) {
        val existing = dao.getById(receiptId) ?: return
        dao.update(existing.copy(status = UploadStatus.Failed, errorMessage = message))
        refreshBadge()
    }

    override suspend fun retryFailed() {
        dao.getRetryable()
            .filter { it.status == UploadStatus.Failed || it.status == UploadStatus.Uploading }
            .forEach { dao.update(it.copy(status = UploadStatus.Pending, errorMessage = null)) }
        refreshBadge()
    }

    override suspend fun retry(receiptId: Long) {
        val existing = dao.getById(receiptId) ?: return
        if (existing.status != UploadStatus.Failed && existing.status != UploadStatus.Uploading) return
        dao.update(existing.copy(status = UploadStatus.Pending, errorMessage = null))
        refreshBadge()
    }

    override suspend fun clearCompleted() {
        dao.getCompleted().forEach { receipt ->
            ReceiptFileCleaner.deletePaths(receipt.localPath, receipt.processedPath)
            dao.deleteById(receipt.id)
        }
        refreshBadge()
    }

    override suspend fun delete(receiptId: Long) {
        dao.getById(receiptId)?.let { receipt ->
            ReceiptFileCleaner.deletePaths(receipt.localPath, receipt.processedPath)
        }
        dao.deleteById(receiptId)
        refreshBadge()
    }

    override suspend fun getPendingCount(): Int = dao.getPendingCount()

    private suspend fun refreshBadge() {
        uploadNotificationManager.refreshQueueBadge(dao.getPendingCount())
    }

    private suspend fun uniqueFileName(fileName: String, excludedReceiptId: Long? = null): String {
        val extensionIndex = fileName.lastIndexOf('.')
        val base = if (extensionIndex > 0) fileName.substring(0, extensionIndex) else fileName
        val extension = if (extensionIndex > 0) fileName.substring(extensionIndex) else ""
        val existingFileName = excludedReceiptId?.let { dao.getById(it)?.fileName }
        var candidate = fileName
        var suffix = 2
        while (dao.countByFileName(candidate) > if (candidate == existingFileName) 1 else 0) {
            candidate = "${base}_$suffix$extension"
            suffix += 1
        }
        return candidate
    }
}
