package com.scantoftp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.ui.navigation.Destination
import com.scantoftp.worker.UploadWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptViewerUiState(
    val receipt: ScannedReceipt? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class ReceiptViewerViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val uploadWorkScheduler: UploadWorkScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val receiptId: Long =
        savedStateHandle.get<String>(Destination.ReceiptViewer.ARG_RECEIPT_ID)?.toLongOrNull() ?: -1L

    val uiState: StateFlow<ReceiptViewerUiState> = repository.observeQueue()
        .map { receipts ->
            ReceiptViewerUiState(
                receipt = receipts.firstOrNull { it.id == receiptId },
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptViewerUiState())

    fun retry() {
        if (receiptId <= 0) return
        viewModelScope.launch {
            repository.retry(receiptId)
            uploadWorkScheduler.enqueueUploadQueue()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (receiptId <= 0) return
        viewModelScope.launch {
            repository.delete(receiptId)
            onDeleted()
        }
    }
}
