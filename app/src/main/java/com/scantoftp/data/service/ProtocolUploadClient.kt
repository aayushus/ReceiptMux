package com.scantoftp.data.service

import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryListing
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.service.UploadClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtocolUploadClient @Inject constructor(
    private val ftpUploadClient: FtpUploadClient,
    private val smbUploadClient: SmbUploadClient,
) : UploadClient {
    override suspend fun testConnection(settings: FtpSettings): Result<Unit> {
        return clientFor(settings).testConnection(settings)
    }

    override suspend fun upload(receipt: ScannedReceipt, settings: FtpSettings): Result<Unit> {
        return clientFor(settings).upload(receipt, settings)
    }

    override suspend fun browseDirectories(settings: FtpSettings, path: String): Result<RemoteDirectoryListing> {
        return clientFor(settings).browseDirectories(settings, path)
    }

    private fun clientFor(settings: FtpSettings): UploadClient {
        return when (settings.uploadProtocol) {
            UploadProtocol.Ftp,
            UploadProtocol.Ftps -> ftpUploadClient
            UploadProtocol.Smb -> smbUploadClient
        }
    }
}
