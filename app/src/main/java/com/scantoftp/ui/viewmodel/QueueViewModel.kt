package com.scantoftp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.worker.UploadWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val uploadWorkScheduler: UploadWorkScheduler,
) : ViewModel() {
    val receipts: StateFlow<List<ScannedReceipt>> = repository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = repository.observeQueue()
        .map { list -> list.count { it.status == UploadStatus.Pending || it.status == UploadStatus.Uploading } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val failedCount: StateFlow<Int> = repository.observeQueue()
        .map { list -> list.count { it.status == UploadStatus.Failed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryFailed()
            uploadWorkScheduler.enqueueUploadQueue()
        }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            repository.retry(id)
            uploadWorkScheduler.enqueueUploadQueue()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
