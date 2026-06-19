package com.scantoftp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.UploadClient
import com.scantoftp.notification.UploadNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val receiptRepository: ReceiptRepository,
    private val settingsRepository: SettingsRepository,
    private val uploadClient: UploadClient,
    private val uploadNotificationManager: UploadNotificationManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settingsFlow().first()
        receiptRepository.retryFailed()
        val items = receiptRepository.observeQueue().first()
            .filter {
                it.status == UploadStatus.Pending ||
                    it.status == UploadStatus.Failed ||
                    it.status == UploadStatus.Uploading
            }
        if (items.isEmpty()) {
            uploadNotificationManager.refreshQueueBadge(0)
            return Result.success()
        }

        setForeground(uploadNotificationManager.createForegroundInfo(id, items.size, null))
        var hadFailure = false
        items.forEach { receipt ->
            val pendingCount = receiptRepository.getPendingCount()
            setForeground(uploadNotificationManager.createForegroundInfo(id, pendingCount, receipt.fileName))
            receiptRepository.markUploading(receipt.id)
            val result = uploadClient.upload(receipt, settings)
            result.fold(
                onSuccess = { receiptRepository.markCompleted(receipt.id) },
                onFailure = {
                    hadFailure = true
                    receiptRepository.markFailed(receipt.id, it.message ?: "Upload failed")
                },
            )
        }

        val pendingCount = receiptRepository.getPendingCount()
        uploadNotificationManager.refreshQueueBadge(pendingCount)
        return if (hadFailure && pendingCount > 0) Result.retry() else Result.success()
    }
}
