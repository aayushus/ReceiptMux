package com.scantoftp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.setupIssues
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.domain.model.ReceiptDraft
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.util.runCatchingCancellable
import com.scantoftp.worker.UploadWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewUiState(
    val beforePath: String = "",
    val afterPath: String = "",
    val fileName: String = "Mar_29_UnknownVendor_0.00.jpg",
    val vendor: String = "UnknownVendor",
    val amount: String = "0.00",
    val receiptDate: String = "Mar_29",
    val cropConfidence: Float = 0f,
    val hasCapture: Boolean = false,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val setupIssues: List<String> = emptyList(),
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val captureSessionRepository: CaptureSessionRepository,
    private val receiptRepository: ReceiptRepository,
    private val settingsRepository: SettingsRepository,
    private val uploadWorkScheduler: UploadWorkScheduler,
) : ViewModel() {
    private val uploadingState = MutableStateFlow(false)
    private val uploadErrorState = MutableStateFlow<String?>(null)
    val uiState: StateFlow<PreviewUiState> = combine(
        captureSessionRepository.currentCapture,
        uploadingState,
        uploadErrorState,
        settingsRepository.settingsFlow(),
    ) { capture, isUploading, errorMessage, settings ->
        val setupIssues = settings.setupIssues()
        if (capture == null) {
            PreviewUiState(isUploading = isUploading, errorMessage = errorMessage, setupIssues = setupIssues)
        } else {
            PreviewUiState(
                beforePath = capture.originalPath,
                afterPath = capture.processedPath,
                fileName = capture.suggestedFileName,
                vendor = capture.vendor,
                amount = capture.amount,
                receiptDate = capture.receiptDate,
                cropConfidence = capture.cropConfidence,
                hasCapture = true,
                isUploading = isUploading,
                errorMessage = errorMessage,
                setupIssues = setupIssues,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState())

    fun updateFileName(value: String) {
        viewModelScope.launch { captureSessionRepository.updateFilename(value) }
    }

    fun confirmUpload(onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            if (uploadingState.value) return@launch
            val capture = captureSessionRepository.currentCapture.value ?: return@launch
            val setupIssues = settingsRepository.settingsFlow().first().setupIssues()
            if (setupIssues.isNotEmpty()) {
                uploadErrorState.value = "Finish setup before uploading: ${setupIssues.first()}"
                return@launch
            }
            uploadingState.value = true
            uploadErrorState.value = null
            val result = runCatchingCancellable {
                val receiptId = receiptRepository.enqueueReceipt(
                    ReceiptDraft(
                        originalPath = capture.originalPath,
                        processedPath = capture.processedPath,
                        suggestedFileName = capture.suggestedFileName,
                        vendor = capture.vendor,
                        amount = capture.amount,
                        receiptDate = capture.receiptDate,
                    ),
                )
                uploadWorkScheduler.enqueueUploadQueue()
                captureSessionRepository.clear()
                onComplete(receiptId)
            }
            result.exceptionOrNull()?.let { uploadErrorState.value = it.message ?: "Unable to queue receipt." }
            uploadingState.value = false
        }
    }

    fun discardCapture(onComplete: () -> Unit) {
        viewModelScope.launch {
            uploadErrorState.value = null
            captureSessionRepository.discardCurrentCapture()
            onComplete()
        }
    }
}
