package com.scantoftp.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.data.service.ManualCropService
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CropAdjustUiState(
    val imagePath: String = "",
    val corners: List<Offset> = emptyList(),
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CropAdjustViewModel @Inject constructor(
    private val captureSessionRepository: CaptureSessionRepository,
    private val manualCropService: ManualCropService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CropAdjustUiState())
    val uiState: StateFlow<CropAdjustUiState> = _uiState.asStateFlow()

    init {
        loadDraft()
    }

    fun updateCorner(index: Int, corner: Offset) {
        val currentCorners = _uiState.value.corners
        if (index !in currentCorners.indices) return
        _uiState.value = _uiState.value.copy(
            corners = currentCorners.mapIndexed { cornerIndex, existing ->
                if (cornerIndex == index) {
                    Offset(corner.x.coerceIn(0f, 1f), corner.y.coerceIn(0f, 1f))
                } else {
                    existing
                }
            },
        )
    }

    fun reset() {
        loadDraft()
    }

    fun apply(onComplete: () -> Unit) {
        viewModelScope.launch {
            val capture = captureSessionRepository.currentCapture.value ?: return@launch
            val corners = _uiState.value.corners
            if (corners.size != 4 || _uiState.value.isApplying) return@launch
            _uiState.value = _uiState.value.copy(isApplying = true, errorMessage = null)
            runCatchingCancellable {
                val outputPath = withContext(Dispatchers.IO) {
                    manualCropService.applyCrop(capture.originalPath, corners)
                }
                captureSessionRepository.updateProcessedCrop(outputPath, 1f)
                onComplete()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    errorMessage = throwable.message ?: "Unable to adjust crop.",
                )
            }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val capture = captureSessionRepository.currentCapture.value
            if (capture == null) {
                _uiState.value = CropAdjustUiState(
                    isLoading = false,
                    errorMessage = "No receipt is ready to adjust.",
                )
                return@launch
            }

            _uiState.value = CropAdjustUiState(isLoading = true)
            runCatchingCancellable {
                withContext(Dispatchers.IO) { manualCropService.createDraft(capture.originalPath) }
            }.fold(
                onSuccess = { draft ->
                    _uiState.value = CropAdjustUiState(
                        imagePath = draft.imagePath,
                        corners = draft.corners,
                        isLoading = false,
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = CropAdjustUiState(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load receipt for adjustment.",
                    )
                },
            )
        }
    }
}
