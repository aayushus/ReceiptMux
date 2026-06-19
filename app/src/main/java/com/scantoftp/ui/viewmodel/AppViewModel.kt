package com.scantoftp.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    receiptRepository: ReceiptRepository,
) : ViewModel() {
    val pendingUploadCount: StateFlow<Int> = receiptRepository.observeQueue()
        .map { receipts ->
            receipts.count { it.status == UploadStatus.Pending || it.status == UploadStatus.Uploading }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
