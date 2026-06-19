package com.scantoftp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryEntry
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.UploadClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteBrowserState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val currentPath: String = "/",
    val directories: List<RemoteDirectoryEntry> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val uploadClient: UploadClient,
) : ViewModel() {
    val settings: StateFlow<FtpSettings> = settingsRepository.settingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FtpSettings())

    val profiles: StateFlow<List<ServerProfile>> = settingsRepository.profilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProfileId: StateFlow<String?> = settingsRepository.activeProfileIdFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _remoteBrowser = MutableStateFlow(RemoteBrowserState())
    val remoteBrowser: StateFlow<RemoteBrowserState> = _remoteBrowser.asStateFlow()

    private var browseConfig: FtpSettings = FtpSettings()

    fun profileById(id: String?): ServerProfile? =
        id?.let { target -> profiles.value.firstOrNull { it.id == target } }

    fun saveProfile(profile: ServerProfile) = launch { settingsRepository.upsertProfile(profile) }
    fun deleteProfile(id: String) = launch { settingsRepository.deleteProfile(id) }
    fun setActiveProfile(id: String) = launch { settingsRepository.setActiveProfile(id) }

    fun setImageQuality(value: Float) = launch { settingsRepository.setImageQuality(value.toInt()) }
    fun setAutoCapture(enabled: Boolean) = launch { settingsRepository.setAutoCapture(enabled) }
    fun setFlashMode(mode: FlashMode) = launch { settingsRepository.setFlashMode(mode) }
    fun setOcrRenaming(enabled: Boolean) = launch { settingsRepository.setOcrRenaming(enabled) }
    fun setTripSubfolder(path: String) = launch { settingsRepository.setTripSubfolder(path) }
    fun setUploadOnWifiOnly(enabled: Boolean) = launch { settingsRepository.setUploadOnWifiOnly(enabled) }

    fun openRemoteBrowser(config: FtpSettings) {
        browseConfig = config
        browseRemoteDirectory("/", visible = true)
    }

    fun browseInto(path: String) = browseRemoteDirectory(path, visible = true)

    fun browseUp() {
        val parentPath = _remoteBrowser.value.currentPath
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .dropLast(1)
            .joinToString(separator = "/", prefix = "/")
            .ifBlank { "/" }
        browseRemoteDirectory(parentPath, visible = true)
    }

    fun selectBrowsedFolder(onPicked: (String) -> Unit) {
        onPicked(_remoteBrowser.value.currentPath)
        dismissRemoteBrowser()
    }

    fun dismissRemoteBrowser() {
        _remoteBrowser.value = _remoteBrowser.value.copy(isVisible = false)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun browseRemoteDirectory(path: String, visible: Boolean) {
        viewModelScope.launch {
            val normalizedPath = path.ifBlank { "/" }
            _remoteBrowser.value = _remoteBrowser.value.copy(
                isVisible = visible,
                isLoading = true,
                currentPath = normalizedPath,
                errorMessage = null,
            )
            val result = uploadClient.browseDirectories(browseConfig, normalizedPath)
            _remoteBrowser.value = result.fold(
                onSuccess = {
                    RemoteBrowserState(
                        isVisible = visible,
                        isLoading = false,
                        currentPath = it.currentPath,
                        directories = it.directories,
                        errorMessage = null,
                    )
                },
                onFailure = {
                    _remoteBrowser.value.copy(
                        isVisible = visible,
                        isLoading = false,
                        currentPath = normalizedPath,
                        directories = emptyList(),
                        errorMessage = it.message ?: "Unable to browse folders.",
                    )
                },
            )
        }
    }
}
