package com.scantoftp.domain.model

import java.util.UUID

/**
 * A single, named upload destination. The app can store many of these and
 * the user picks one as the active destination that scans upload to.
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val protocol: UploadProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val remoteDirectory: String,
    val smbDomain: String = "",
) {
    val isSmb: Boolean get() = protocol == UploadProtocol.Smb
    val isFtpFamily: Boolean get() = protocol == UploadProtocol.Ftp || protocol == UploadProtocol.Ftps

    companion object {
        fun blank(protocol: UploadProtocol): ServerProfile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            protocol = protocol,
            host = "",
            port = if (protocol == UploadProtocol.Smb) 445 else 21,
            username = "",
            password = "",
            remoteDirectory = if (protocol == UploadProtocol.Smb) "" else "/receipts",
            smbDomain = "",
        )
    }
}

/** Maps a profile onto the effective [FtpSettings] used by the uploader/browser. */
fun ServerProfile.toUploadConfig(base: FtpSettings = FtpSettings()): FtpSettings = base.copy(
    host = host,
    port = port,
    username = username,
    password = password,
    uploadProtocol = protocol,
    remoteDirectory = remoteDirectory,
    useFtps = protocol == UploadProtocol.Ftps,
    smbDomain = smbDomain,
    smbShareName = "",
)

fun ServerProfile.validationIssues(): List<String> = buildList {
    if (name.isBlank()) add("Name is required.")
    if (host.isBlank()) add("Host is required.")
    if (port !in 1..65535) add("Port must be between 1 and 65535.")
    if (username.isBlank()) add("Username is required.")
    if (password.isBlank()) add("Password is required.")
    if (remoteDirectory.trim().trim('/').isBlank()) {
        add(if (isSmb) "Destination folder is required." else "Remote folder is required.")
    }
}

fun ServerProfile.isValid(): Boolean = validationIssues().isEmpty()
