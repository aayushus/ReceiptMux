package com.scantoftp.data.service

import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryEntry
import com.scantoftp.domain.model.RemoteDirectoryListing
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.service.UploadClient
import com.scantoftp.util.FtpPathNormalizer
import com.scantoftp.util.runCatchingCancellable
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbUploadClient @Inject constructor() : UploadClient {
    override suspend fun testConnection(settings: FtpSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            validateSettings(settings)
            // Listing forces a real connection + NTLM auth. At the server root this
            // returns the available shares; inside a folder it confirms the path.
            val target = resolveDirectory(settings, settings.remoteDirectory.ifBlank { "/" })
            target.listFiles()
            Unit
        }
    }

    override suspend fun upload(receipt: ScannedReceipt, settings: FtpSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            validateSettings(settings)
            val file = File(receipt.processedPath)
            require(file.exists()) { "Local file not found." }

            require(FtpPathNormalizer.segments(settings.remoteDirectory).isNotEmpty()) {
                "Choose a destination folder on the NAS first."
            }
            val targetDirectory = resolveDirectory(
                settings = settings,
                relativePath = FtpPathNormalizer.normalize(settings.remoteDirectory, settings.tripSubfolder),
            )
            ensureDirectoryExists(targetDirectory)
            val remoteName = resolveRemoteFileName(targetDirectory, receipt.fileName)
            val remoteFile = SmbFile(targetDirectory, remoteName)
            FileInputStream(file).use { input ->
                SmbFileOutputStream(remoteFile).use { output ->
                    input.copyTo(output)
                }
            }
            Unit
        }
    }

    override suspend fun browseDirectories(settings: FtpSettings, path: String): Result<RemoteDirectoryListing> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            validateSettings(settings)
            val normalizedPath = normalizeBrowserPath(path)
            val directory = resolveDirectory(settings, normalizedPath)
            // listFiles() forces auth and surfaces connection/credential errors.
            // At the server root ("/") the children are the server's shares.
            val directories = directory.listFiles()
                ?.filter { child -> !isHiddenShare(child) && isDirectorySafe(child) }
                ?.sortedBy { it.name.trimEnd('/').lowercase() }
                ?.map { child ->
                    val childName = child.name.trimEnd('/')
                    RemoteDirectoryEntry(
                        name = childName,
                        path = childPath(normalizedPath, childName),
                    )
                }
                .orEmpty()

            RemoteDirectoryListing(
                currentPath = normalizedPath,
                directories = directories,
            )
        }
    }

    private fun validateSettings(settings: FtpSettings) {
        require(settings.host.isNotBlank()) { "NAS host is required." }
        require(settings.port in 1..65535) { "Port must be between 1 and 65535." }
    }

    /**
     * Resolves a path relative to the server root, where the first path segment is
     * the SMB share. e.g. "/Media/receipts" -> smb://host/Media/receipts/.
     */
    private fun resolveDirectory(settings: FtpSettings, relativePath: String): SmbFile {
        val auth = NtlmPasswordAuthenticator(settings.smbDomain, settings.username, settings.password)
        val context = buildContext().withCredentials(auth)
        val serverRoot = SmbFile(serverUrl(settings), context)
        return FtpPathNormalizer.segments(relativePath).fold(serverRoot) { current, segment ->
            SmbFile(current, "$segment/")
        }
    }

    private fun isDirectorySafe(file: SmbFile): Boolean = try {
        file.isDirectory
    } catch (_: SmbException) {
        false
    }

    /** Skip administrative/hidden shares such as IPC$, ADMIN$, C$, print$. */
    private fun isHiddenShare(file: SmbFile): Boolean = file.name.trimEnd('/').endsWith("$")

    private fun buildContext(): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.connTimeout", CONNECT_TIMEOUT_MS.toString())
            setProperty("jcifs.smb.client.soTimeout", READ_TIMEOUT_MS.toString())
            setProperty("jcifs.smb.client.responseTimeout", READ_TIMEOUT_MS.toString())
        }
        return BaseContext(PropertyConfiguration(properties))
    }

    private fun ensureDirectoryExists(directory: SmbFile) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        check(directory.isDirectory) { "SMB target path is not a directory." }
    }

    private fun resolveRemoteFileName(directory: SmbFile, requestedFileName: String): String {
        val extensionIndex = requestedFileName.lastIndexOf('.')
        val base = if (extensionIndex > 0) requestedFileName.substring(0, extensionIndex) else requestedFileName
        val extension = if (extensionIndex > 0) requestedFileName.substring(extensionIndex) else ""
        var candidate = requestedFileName
        var suffix = 2
        while (exists(SmbFile(directory, candidate))) {
            candidate = "${base}_$suffix$extension"
            suffix += 1
        }
        return candidate
    }

    private fun exists(file: SmbFile): Boolean = try {
        file.exists()
    } catch (_: SmbException) {
        false
    }

    private fun serverUrl(settings: FtpSettings): String {
        val host = settings.host.trim()
        val authority = if (settings.port == 445) host else "$host:${settings.port}"
        return "smb://$authority/"
    }

    private fun normalizeBrowserPath(path: String): String {
        return FtpPathNormalizer.normalize(path.ifBlank { "/" }, "").ifBlank { "/" }
    }

    private fun childPath(currentPath: String, childName: String): String {
        return when (currentPath) {
            "", "/" -> "/$childName"
            else -> "$currentPath/$childName"
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
