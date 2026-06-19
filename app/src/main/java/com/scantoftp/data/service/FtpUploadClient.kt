package com.scantoftp.data.service

import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryListing
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.service.UploadClient
import com.scantoftp.util.FtpPathNormalizer
import com.scantoftp.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpUploadClient @Inject constructor() : UploadClient {
    override suspend fun testConnection(settings: FtpSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            validateSettings(settings)
            withConnectedClient(settings) { client ->
                client.printWorkingDirectory()
                Unit
            }
        }
    }

    override suspend fun upload(receipt: ScannedReceipt, settings: FtpSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            validateSettings(settings)
            val file = File(receipt.processedPath)
            require(file.exists()) { "Local file not found." }

            withConnectedClient(settings) { client ->
                val remoteDir = buildRemoteDirectory(settings)
                if (remoteDir.isNotBlank()) {
                    ensureRemoteDirectory(client, remoteDir)
                }
                val remoteFileName = resolveRemoteFileName(client, receipt.fileName)
                FileInputStream(file).use { input ->
                    check(client.storeFile(remoteFileName, input)) { "Failed to upload receipt." }
                }
            }
        }
    }

    override suspend fun browseDirectories(settings: FtpSettings, path: String): Result<RemoteDirectoryListing> {
        return Result.failure(UnsupportedOperationException("Folder browsing is currently available for SMB connections."))
    }

    private fun createClient(settings: FtpSettings): FTPClient {
        return if (settings.uploadProtocol == UploadProtocol.Ftps || settings.useFtps) FTPSClient(false) else FTPClient()
    }

    private fun validateSettings(settings: FtpSettings) {
        require(settings.host.isNotBlank()) { "FTP host is required." }
        require(settings.username.isNotBlank()) { "FTP username is required." }
        require(settings.port in 1..65535) { "FTP port must be between 1 and 65535." }
    }

    private fun <T> withConnectedClient(settings: FtpSettings, block: (FTPClient) -> T): T {
        val client = createClient(settings)
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.setDefaultTimeout(CONNECT_TIMEOUT_MS)
        try {
            client.connect(settings.host, settings.port)
            client.soTimeout = READ_TIMEOUT_MS
            client.setDataTimeout(Duration.ofMillis(READ_TIMEOUT_MS.toLong()))
            check(client.login(settings.username, settings.password)) { "FTP login failed." }
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            return block(client)
        } finally {
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
        }
    }

    private fun buildRemoteDirectory(settings: FtpSettings): String {
        return FtpPathNormalizer.normalize(settings.remoteDirectory, settings.tripSubfolder)
    }

    private fun ensureRemoteDirectory(client: FTPClient, remoteDir: String) {
        val normalized = remoteDir.trim()
        if (normalized.isBlank()) return

        if (normalized.startsWith("/")) {
            check(client.changeWorkingDirectory("/")) { "Unable to access FTP root." }
        }

        FtpPathNormalizer.segments(normalized).forEach { segment ->
            if (!client.changeWorkingDirectory(segment)) {
                check(client.makeDirectory(segment)) { "Unable to create remote directory '$segment'." }
                check(client.changeWorkingDirectory(segment)) { "Unable to open remote directory '$segment'." }
            }
        }
    }

    private fun resolveRemoteFileName(client: FTPClient, requestedFileName: String): String {
        val extensionIndex = requestedFileName.lastIndexOf('.')
        val base = if (extensionIndex > 0) requestedFileName.substring(0, extensionIndex) else requestedFileName
        val extension = if (extensionIndex > 0) requestedFileName.substring(extensionIndex) else ""
        var candidate = requestedFileName
        var suffix = 2
        while (client.listFiles(candidate).isNotEmpty()) {
            candidate = "${base}_$suffix$extension"
            suffix += 1
        }
        return candidate
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
