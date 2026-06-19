package com.scantoftp.domain.repository

import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Effective upload config = active profile merged with global capture/image settings. */
    fun settingsFlow(): Flow<FtpSettings>

    /** All stored server profiles (SMB + FTP/FTPS). */
    fun profilesFlow(): Flow<List<ServerProfile>>

    /** Id of the profile scans currently upload to (null when none configured). */
    fun activeProfileIdFlow(): Flow<String?>

    suspend fun upsertProfile(profile: ServerProfile)
    suspend fun deleteProfile(id: String)
    suspend fun setActiveProfile(id: String)

    suspend fun setImageQuality(quality: Int)
    suspend fun setAutoCapture(enabled: Boolean)
    suspend fun setFlashMode(mode: FlashMode)
    suspend fun setOcrRenaming(enabled: Boolean)
    suspend fun setTripSubfolder(path: String)
    suspend fun setUploadOnWifiOnly(enabled: Boolean)
}
