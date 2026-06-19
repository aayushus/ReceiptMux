package com.scantoftp.ui.viewmodel

import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.BuildConfig
import com.scantoftp.domain.model.CaptureSession
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.ReceiptDetectionState
import com.scantoftp.domain.model.isReadyForUpload
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.ReceiptProcessor
import com.scantoftp.domain.service.ReceiptTextRecognizer
import com.scantoftp.ui.camera.AutoCaptureDecider
import com.scantoftp.ui.camera.FrameAnalysisResult
import com.scantoftp.util.ReceiptFileCleaner
import com.scantoftp.util.ReceiptFileStore
import com.scantoftp.util.ReceiptFilenameSanitizer
import com.scantoftp.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val captureSessionRepository: CaptureSessionRepository,
    private val receiptProcessor: ReceiptProcessor,
    private val receiptTextRecognizer: ReceiptTextRecognizer,
) : ViewModel() {
    private val _detectionState = MutableStateFlow(ReceiptDetectionState())
    val detectionState: StateFlow<ReceiptDetectionState> = _detectionState.asStateFlow()

    val settings: StateFlow<FtpSettings> = settingsRepository.settingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FtpSettings())

    private val _captureRequestToken = MutableStateFlow(0L)
    val captureRequestToken: StateFlow<Long> = _captureRequestToken.asStateFlow()

    private val _shutterCueToken = MutableStateFlow(0L)
    val shutterCueToken: StateFlow<Long> = _shutterCueToken.asStateFlow()

    private val _isProcessingCapture = MutableStateFlow(false)
    val isProcessingCapture: StateFlow<Boolean> = _isProcessingCapture.asStateFlow()

    val setupReady: StateFlow<Boolean> = settings
        .map { it.isReadyForUpload() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var autoCaptureProgress = 0f
    private var motionStill = true
    private var latestFrameResult: FrameAnalysisResult? = null
    private var lastDiagnosticLogAtMillis = 0L
    private var smoothedBoundingBox: Rect? = null
    private val autoCaptureDecider = AutoCaptureDecider()

    fun cycleFlashMode() {
        viewModelScope.launch {
            val next = when (settings.value.flashMode) {
                FlashMode.Auto -> FlashMode.AlwaysOn
                FlashMode.AlwaysOn -> FlashMode.AlwaysOff
                FlashMode.AlwaysOff -> FlashMode.Auto
            }
            settingsRepository.setFlashMode(next)
        }
    }

    fun onFrameAnalyzed(result: FrameAnalysisResult) {
        latestFrameResult = result
        applyFrameState(result)
    }

    fun onMotionStillnessChanged(isStill: Boolean) {
        motionStill = isStill
        latestFrameResult?.let(::applyFrameState)
    }

    private fun applyFrameState(result: FrameAnalysisResult) {
        val now = System.currentTimeMillis()
        val decision = autoCaptureDecider.evaluate(
            result = result,
            motionStill = motionStill,
            autoCaptureEnabled = settings.value.autoCaptureEnabled,
            isProcessing = _isProcessingCapture.value,
            nowMillis = now,
        )
        autoCaptureProgress = decision.progress
        val progress = decision.progress

        _detectionState.value = ReceiptDetectionState(
            detected = result.receiptDetected,
            stable = result.stable,
            focusLocked = result.focusLocked,
            exposureStable = result.exposureStable,
            deviceStill = motionStill,
            torchEnabled = _detectionState.value.torchEnabled,
            progress = progress,
            boundingBox = smoothBoundingBox(result.boundingBox),
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight,
            geometrySkew = result.geometrySkew,
            luminance = result.luminance,
            boxArea = result.boxArea,
            centeredness = result.centeredness,
            confidence = result.detectionConfidence,
            sharpness = result.focusScore,
            contrast = result.contrast,
            glare = result.glare,
            errorMessage = _detectionState.value.errorMessage,
        )

        if (BuildConfig.DEBUG && now - lastDiagnosticLogAtMillis >= 1200L && (result.receiptDetected || progress > 0f)) {
            lastDiagnosticLogAtMillis = now
            Log.d(
                "ReceiptMuxScanner",
                "AutoGate detected=${result.receiptDetected} stable=${result.stable} focus=${result.focusLocked} exposure=${result.exposureStable} motionStill=$motionStill conf=${"%.2f".format(result.detectionConfidence)} area=${"%.2f".format(result.boxArea)} quality=${"%.2f".format(decision.quality)} qualified=${decision.qualifiedFrameCount} progress=${"%.2f".format(progress)}",
            )
        }

        if (decision.shouldTrigger) {
            if (BuildConfig.DEBUG) Log.d("ReceiptMuxScanner", "Auto-capture triggered")
            requestCapture(autoTriggered = true)
        }
    }

    fun onTorchChanged(enabled: Boolean) {
        _detectionState.value = _detectionState.value.copy(torchEnabled = enabled)
    }

    fun requestManualCapture() {
        requestCapture(autoTriggered = false)
    }

    private fun requestCapture(autoTriggered: Boolean) {
        if (_isProcessingCapture.value) return
        if (BuildConfig.DEBUG) Log.d("ReceiptMuxScanner", "Capture requested")
        _isProcessingCapture.value = true
        if (autoTriggered) {
            _shutterCueToken.value = System.currentTimeMillis()
        }
        _captureRequestToken.value = System.currentTimeMillis()
    }

    fun onImageCaptured(path: String, onReady: () -> Unit) {
        viewModelScope.launch {
            var processedPath: String? = null
            var failureMessage: String? = null
            // Watchdog: never let a slow/hung processing pipeline (OpenCV, OCR,
            // first-run ML Kit model load) trap the user on the spinner forever.
            val outcome: Boolean? = withTimeoutOrNull(PROCESSING_TIMEOUT_MS) {
                runCatchingCancellable {
                    val captureTimestamp = System.currentTimeMillis()
                    if (BuildConfig.DEBUG) Log.d("ReceiptMuxScanner", "Processing capture from $path")
                    val processing = withContext(Dispatchers.IO) {
                        receiptProcessor.process(path)
                    }
                    processedPath = processing.processedPath
                    val ocr = if (settings.value.ocrRenamingEnabled) {
                        withContext(Dispatchers.IO) {
                            receiptTextRecognizer.recognize(processing.processedPath, captureTimestamp)
                        }
                    } else {
                        val fallbackDate = ReceiptFileStore.createFileNamePrefix(captureTimestamp)
                        com.scantoftp.domain.service.OcrResult(
                            vendor = "UnknownVendor",
                            amount = "0.00",
                            receiptDate = fallbackDate,
                            suggestedFilename = "${fallbackDate}_UnknownVendor_0.00.jpg",
                        )
                    }

                    captureSessionRepository.setCurrentCapture(
                        CaptureSession(
                            originalPath = path,
                            processedPath = processing.processedPath,
                            suggestedFileName = ReceiptFilenameSanitizer.sanitize(ocr.suggestedFilename, captureTimestamp),
                            vendor = ocr.vendor,
                            amount = ocr.amount,
                            receiptDate = ocr.receiptDate,
                            captureTimestamp = captureTimestamp,
                            cropConfidence = processing.cropConfidence,
                        ),
                    )
                    true
                }.getOrElse { error ->
                    Log.e("ReceiptMuxScanner", "Capture processing failed", error)
                    failureMessage = error.message ?: "Unable to process receipt. Try again."
                    false
                }
            }

            if (outcome == true) {
                onReady()
            } else {
                if (outcome == null) {
                    Log.e("ReceiptMuxScanner", "Capture processing timed out after ${PROCESSING_TIMEOUT_MS}ms")
                    failureMessage = "Processing took too long. Try again."
                }
                ReceiptFileCleaner.deletePaths(path, processedPath)
                autoCaptureProgress = 0f
                autoCaptureDecider.reset()
                _detectionState.value = _detectionState.value.copy(
                    progress = 0f,
                    errorMessage = failureMessage ?: "Unable to process receipt. Try again.",
                )
            }
            _isProcessingCapture.value = false
        }
    }

    fun onCaptureFailed() {
        autoCaptureProgress = 0f
        autoCaptureDecider.reset()
        _isProcessingCapture.value = false
        _detectionState.value = _detectionState.value.copy(
            progress = 0f,
            errorMessage = "Unable to capture receipt. Try again.",
        )
    }

    fun clearError() {
        _detectionState.value = _detectionState.value.copy(errorMessage = null)
    }

    private companion object {
        private const val PROCESSING_TIMEOUT_MS = 30_000L
    }

    private fun smoothBoundingBox(next: Rect?): Rect? {
        if (next == null) {
            smoothedBoundingBox = null
            return null
        }
        val previous = smoothedBoundingBox
        val smoothed = if (previous == null) {
            next
        } else {
            val alpha = 0.28f
            Rect(
                left = previous.left + (next.left - previous.left) * alpha,
                top = previous.top + (next.top - previous.top) * alpha,
                right = previous.right + (next.right - previous.right) * alpha,
                bottom = previous.bottom + (next.bottom - previous.bottom) * alpha,
            )
        }
        smoothedBoundingBox = smoothed
        return smoothed
    }
}
