package com.scantoftp.domain.repository

import com.scantoftp.domain.model.CaptureSession
import kotlinx.coroutines.flow.StateFlow

interface CaptureSessionRepository {
    val currentCapture: StateFlow<CaptureSession?>

    suspend fun setCurrentCapture(capture: CaptureSession)
    suspend fun updateFilename(fileName: String)
    suspend fun updateProcessedCrop(processedPath: String, cropConfidence: Float)
    suspend fun discardCurrentCapture()
    suspend fun clear()
}
