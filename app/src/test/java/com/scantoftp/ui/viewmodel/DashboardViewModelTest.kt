package com.scantoftp.ui.viewmodel

import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.model.ReceiptDraft
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeReceiptRepository = FakeReceiptRepository()
    private val fakeSettingsRepository = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has default values`() = runTest {
        val viewModel = DashboardViewModel(fakeReceiptRepository, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        
        // Let the flow combine logic execute
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.pending)
        assertEquals(0, state.failed)
        assertEquals(0, state.completed)
        assertTrue(state.recent.isEmpty())
        assertFalse(state.setupComplete)
        assertEquals("", state.destinationLabel)

        collectJob.cancel()
    }

    @Test
    fun `computes pending, failed, and completed counts correctly`() = runTest {
        val viewModel = DashboardViewModel(fakeReceiptRepository, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        
        val receipts = listOf(
            createReceipt(id = 1, status = UploadStatus.Pending),
            createReceipt(id = 2, status = UploadStatus.Uploading),
            createReceipt(id = 3, status = UploadStatus.Failed),
            createReceipt(id = 4, status = UploadStatus.Completed),
            createReceipt(id = 5, status = UploadStatus.Completed),
        )
        fakeReceiptRepository.queueFlow.value = receipts
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pending) // Pending + Uploading
        assertEquals(1, state.failed)
        assertEquals(2, state.completed)

        collectJob.cancel()
    }

    @Test
    fun `recent list contains up to 6 receipts sorted by descending creation time`() = runTest {
        val viewModel = DashboardViewModel(fakeReceiptRepository, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        
        val receipts = (1..10).map { index ->
            createReceipt(id = index.toLong(), createdAt = index * 1000L)
        }
        fakeReceiptRepository.queueFlow.value = receipts
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(6, state.recent.size)
        // Check sorted by descending (newest first: 10, 9, 8, 7, 6, 5)
        assertEquals(10L, state.recent[0].id)
        assertEquals(5L, state.recent[5].id)

        collectJob.cancel()
    }

    @Test
    fun `destinationLabel and setupComplete are set based on settings`() = runTest {
        val viewModel = DashboardViewModel(fakeReceiptRepository, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        
        fakeSettingsRepository.settingsFlow.value = FtpSettings(
            host = "192.168.1.50",
            username = "admin",
            password = "password",
            uploadProtocol = UploadProtocol.Smb,
            remoteDirectory = "/scans"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.setupComplete)
        assertEquals("SMB · 192.168.1.50/scans", state.destinationLabel)

        collectJob.cancel()
    }

    @Test
    fun `destinationLabel handles FTP and FTPS protocols appropriately`() = runTest {
        val viewModel = DashboardViewModel(fakeReceiptRepository, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }

        // FTP protocol
        fakeSettingsRepository.settingsFlow.value = FtpSettings(
            host = "ftp.server.com",
            username = "user",
            password = "pwd",
            uploadProtocol = UploadProtocol.Ftp,
            useFtps = false,
            remoteDirectory = "/incoming"
        )
        advanceUntilIdle()
        assertEquals("FTP · ftp.server.com/incoming", viewModel.uiState.value.destinationLabel)

        // FTP with FTPS
        fakeSettingsRepository.settingsFlow.value = FtpSettings(
            host = "ftp.server.com",
            username = "user",
            password = "pwd",
            uploadProtocol = UploadProtocol.Ftp,
            useFtps = true,
            remoteDirectory = "/incoming"
        )
        advanceUntilIdle()
        assertEquals("FTPS · ftp.server.com/incoming", viewModel.uiState.value.destinationLabel)

        // Explicit FTPS protocol
        fakeSettingsRepository.settingsFlow.value = FtpSettings(
            host = "ftps.server.com",
            username = "user",
            password = "pwd",
            uploadProtocol = UploadProtocol.Ftps,
            remoteDirectory = "/incoming"
        )
        advanceUntilIdle()
        assertEquals("FTPS · ftps.server.com/incoming", viewModel.uiState.value.destinationLabel)

        collectJob.cancel()
    }

    private fun createReceipt(
        id: Long,
        createdAt: Long = 0L,
        status: UploadStatus = UploadStatus.Pending
    ): ScannedReceipt {
        return ScannedReceipt(
            id = id,
            localPath = "local/path/$id",
            processedPath = "processed/path/$id",
            fileName = "receipt_$id.jpg",
            vendor = "Vendor $id",
            amount = "10.00",
            receiptDate = "2026-06-19",
            createdAt = createdAt,
            status = status
        )
    }

    private class FakeReceiptRepository : ReceiptRepository {
        val queueFlow = MutableStateFlow<List<ScannedReceipt>>(emptyList())
        override fun observeQueue(): Flow<List<ScannedReceipt>> = queueFlow
        override suspend fun enqueueReceipt(draft: ReceiptDraft): Long = 0
        override suspend fun updateFilename(receiptId: Long, newFilename: String) {}
        override suspend fun markUploading(receiptId: Long) {}
        override suspend fun markCompleted(receiptId: Long) {}
        override suspend fun markFailed(receiptId: Long, message: String) {}
        override suspend fun retryFailed() {}
        override suspend fun retry(receiptId: Long) {}
        override suspend fun clearCompleted() {}
        override suspend fun delete(receiptId: Long) {}
        override suspend fun getPendingCount(): Int = 0
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settingsFlow = MutableStateFlow(FtpSettings())
        override fun settingsFlow(): Flow<FtpSettings> = settingsFlow
        override fun profilesFlow(): Flow<List<ServerProfile>> = flowOf(emptyList())
        override fun activeProfileIdFlow(): Flow<String?> = flowOf(null)
        override suspend fun upsertProfile(profile: ServerProfile) {}
        override suspend fun deleteProfile(id: String) {}
        override suspend fun setActiveProfile(id: String) {}
        override suspend fun setImageQuality(quality: Int) {}
        override suspend fun setAutoCapture(enabled: Boolean) {}
        override suspend fun setFlashMode(mode: FlashMode) {}
        override suspend fun setOcrRenaming(enabled: Boolean) {}
        override suspend fun setTripSubfolder(path: String) {}
        override suspend fun setUploadOnWifiOnly(enabled: Boolean) {}
    }
}
