package com.scantoftp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.model.isReadyForUpload
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val pending: Int = 0,
    val failed: Int = 0,
    val completed: Int = 0,
    val recent: List<ScannedReceipt> = emptyList(),
    val setupComplete: Boolean = false,
    val destinationLabel: String = "",
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    receiptRepository: ReceiptRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        receiptRepository.observeQueue(),
        settingsRepository.settingsFlow(),
    ) { receipts, settings ->
        val sorted = receipts.sortedByDescending { it.createdAt }
        DashboardUiState(
            pending = receipts.count { it.status == UploadStatus.Pending || it.status == UploadStatus.Uploading },
            failed = receipts.count { it.status == UploadStatus.Failed },
            completed = receipts.count { it.status == UploadStatus.Completed },
            recent = sorted.take(6),
            setupComplete = settings.isReadyForUpload(),
            destinationLabel = settings.destinationLabel(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

private fun com.scantoftp.domain.model.FtpSettings.destinationLabel(): String {
    if (host.isBlank()) return ""
    val protocol = when (uploadProtocol) {
        UploadProtocol.Smb -> "SMB"
        UploadProtocol.Ftps -> "FTPS"
        UploadProtocol.Ftp -> if (useFtps) "FTPS" else "FTP"
    }
    val folder = remoteDirectory.trim().ifBlank { "/" }
    return "$protocol · $host$folder"
}
