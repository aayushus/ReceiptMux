package com.scantoftp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationTest {
    @Test
    fun `default FtpSettings has setup issues`() {
        val settings = FtpSettings()
        val issues = settings.setupIssues()

        assertTrue(issues.contains("Host is required."))
        assertTrue(issues.contains("Username is required."))
        assertTrue(issues.contains("Password is required."))
        assertFalse(settings.isReadyForUpload())
    }

    @Test
    fun `fully configured FtpSettings has no setup issues`() {
        val settings = FtpSettings(
            host = "ftp.example.com",
            port = 21,
            username = "user",
            password = "pwd",
            remoteDirectory = "/receipts",
            uploadProtocol = UploadProtocol.Ftp
        )
        val issues = settings.setupIssues()

        assertTrue(issues.isEmpty())
        assertTrue(settings.isReadyForUpload())
    }

    @Test
    fun `invalid port numbers produce setup issues`() {
        val baseSettings = FtpSettings(
            host = "ftp.example.com",
            username = "user",
            password = "pwd",
            remoteDirectory = "/receipts"
        )

        val settingsWithZeroPort = baseSettings.copy(port = 0)
        assertTrue(settingsWithZeroPort.setupIssues().contains("Port must be between 1 and 65535."))
        assertFalse(settingsWithZeroPort.isReadyForUpload())

        val settingsWithNegativePort = baseSettings.copy(port = -5)
        assertTrue(settingsWithNegativePort.setupIssues().contains("Port must be between 1 and 65535."))

        val settingsWithTooHighPort = baseSettings.copy(port = 65536)
        assertTrue(settingsWithTooHighPort.setupIssues().contains("Port must be between 1 and 65535."))
    }

    @Test
    fun `SMB protocol validates remote folder uniquely`() {
        val baseSmbSettings = FtpSettings(
            host = "192.168.1.100",
            username = "user",
            password = "pwd",
            uploadProtocol = UploadProtocol.Smb
        )

        // Trim/trim('/') is blank, i.e., empty folder or just slashes
        val invalidSmbSettings = baseSmbSettings.copy(remoteDirectory = "  ///  ")
        val issues = invalidSmbSettings.setupIssues()
        assertTrue(issues.contains("NAS destination folder is required."))
        assertFalse(invalidSmbSettings.isReadyForUpload())

        // Valid SMB remote folder
        val validSmbSettings = baseSmbSettings.copy(remoteDirectory = "/shared/receipts")
        assertTrue(validSmbSettings.setupIssues().isEmpty())
        assertTrue(validSmbSettings.isReadyForUpload())
    }

    @Test
    fun `FTP protocol requires remote directory to not be blank`() {
        val baseFtpSettings = FtpSettings(
            host = "ftp.example.com",
            username = "user",
            password = "pwd",
            uploadProtocol = UploadProtocol.Ftp
        )

        val invalidFtpSettings = baseFtpSettings.copy(remoteDirectory = "")
        val issues = invalidFtpSettings.setupIssues()
        assertTrue(issues.contains("Remote folder is required."))
        assertFalse(invalidFtpSettings.isReadyForUpload())
    }
}
