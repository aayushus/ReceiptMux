package com.scantoftp.domain.model

fun FtpSettings.setupIssues(): List<String> = buildList {
    if (host.isBlank()) add("Host is required.")
    if (port !in 1..65535) add("Port must be between 1 and 65535.")
    if (username.isBlank()) add("Username is required.")
    if (password.isBlank()) add("Password is required.")
    if (uploadProtocol == UploadProtocol.Smb) {
        if (remoteDirectory.trim().trim('/').isBlank()) add("NAS destination folder is required.")
    } else if (remoteDirectory.isBlank()) {
        add("Remote folder is required.")
    }
}

fun FtpSettings.isReadyForUpload(): Boolean = setupIssues().isEmpty()
