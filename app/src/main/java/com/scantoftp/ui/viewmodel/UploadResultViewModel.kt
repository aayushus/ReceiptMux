package com.scantoftp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
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

data class UploadResultUiState(
    val receipt: ScannedReceipt? = null,
    val status: UploadStatus = UploadStatus.Pending,
)

@HiltViewModel
class UploadResultViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val uploadWorkScheduler: UploadWorkScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val receiptId: Long = savedStateHandle.get<String>(Destination.UploadResult.ARG_RECEIPT_ID)?.toLongOrNull() ?: -1L

    val uiState: StateFlow<UploadResultUiState> = repository.observeQueue()
        .map { receipts ->
            val receipt = receipts.firstOrNull { it.id == receiptId }
            UploadResultUiState(
                receipt = receipt,
                status = receipt?.status ?: UploadStatus.Pending,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UploadResultUiState())

    fun retry() {
        if (receiptId <= 0) return
        viewModelScope.launch {
            repository.retry(receiptId)
            uploadWorkScheduler.enqueueUploadQueue()
        }
    }
}
