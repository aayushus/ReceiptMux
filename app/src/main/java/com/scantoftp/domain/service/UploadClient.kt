package com.scantoftp.domain.service

import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryListing
import com.scantoftp.domain.model.ScannedReceipt

interface UploadClient {
    suspend fun testConnection(settings: FtpSettings): Result<Unit>
    suspend fun upload(receipt: ScannedReceipt, settings: FtpSettings): Result<Unit>
    suspend fun browseDirectories(settings: FtpSettings, path: String): Result<RemoteDirectoryListing>
}
