package com.scantoftp.data.repository

import com.scantoftp.domain.model.CaptureSession
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.util.ReceiptFileCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryCaptureSessionRepository @Inject constructor() : CaptureSessionRepository {
    private val mutableCurrentCapture = MutableStateFlow<CaptureSession?>(null)

    override val currentCapture: StateFlow<CaptureSession?> = mutableCurrentCapture

    override suspend fun setCurrentCapture(capture: CaptureSession) {
        deleteCaptureFiles(mutableCurrentCapture.value)
        mutableCurrentCapture.value = capture
    }

    override suspend fun updateFilename(fileName: String) {
        mutableCurrentCapture.value = mutableCurrentCapture.value?.copy(suggestedFileName = fileName)
    }

    override suspend fun updateProcessedCrop(processedPath: String, cropConfidence: Float) {
        val current = mutableCurrentCapture.value ?: return
        ReceiptFileCleaner.deletePaths(current.processedPath)
        mutableCurrentCapture.value = current.copy(
            processedPath = processedPath,
            cropConfidence = cropConfidence,
        )
    }

    override suspend fun discardCurrentCapture() {
        deleteCaptureFiles(mutableCurrentCapture.value)
        mutableCurrentCapture.value = null
    }

    override suspend fun clear() {
        mutableCurrentCapture.value = null
    }

    private fun deleteCaptureFiles(capture: CaptureSession?) {
        ReceiptFileCleaner.deletePaths(capture?.originalPath, capture?.processedPath)
    }
}
