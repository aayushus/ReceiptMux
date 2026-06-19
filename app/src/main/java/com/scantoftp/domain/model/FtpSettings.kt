package com.scantoftp.domain.model

data class FtpSettings(
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val uploadProtocol: UploadProtocol = UploadProtocol.Ftp,
    val remoteDirectory: String = "/receipts",
    val useFtps: Boolean = false,
    val smbShareName: String = "",
    val smbDomain: String = "",
    val tripSubfolder: String = "",
    val imageQuality: Int = 90,
    val autoCaptureEnabled: Boolean = true,
    val flashMode: FlashMode = FlashMode.Auto,
    val ocrRenamingEnabled: Boolean = true,
    val uploadOnWifiOnly: Boolean = false,
)
