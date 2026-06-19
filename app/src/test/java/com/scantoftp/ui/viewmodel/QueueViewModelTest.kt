package com.scantoftp.ui.viewmodel

import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.worker.UploadWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: ReceiptRepository = mock()
    private val uploadWorkScheduler: UploadWorkScheduler = mock()
    private val queueFlow = MutableStateFlow<List<ScannedReceipt>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(repository.observeQueue()).thenReturn(queueFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial states are correctly mapped from repository flow`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        val collectReceipts = launch { viewModel.receipts.collect {} }
        val collectPending = launch { viewModel.pendingCount.collect {} }
        val collectFailed = launch { viewModel.failedCount.collect {} }
        
        advanceUntilIdle()

        assertEquals(0, viewModel.receipts.value.size)
        assertEquals(0, viewModel.pendingCount.value)
        assertEquals(0, viewModel.failedCount.value)

        collectReceipts.cancel()
        collectPending.cancel()
        collectFailed.cancel()
    }

    @Test
    fun `state values update dynamically when repository queue changes`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        val collectReceipts = launch { viewModel.receipts.collect {} }
        val collectPending = launch { viewModel.pendingCount.collect {} }
        val collectFailed = launch { viewModel.failedCount.collect {} }

        val testReceipts = listOf(
            createReceipt(id = 1, status = UploadStatus.Pending),
            createReceipt(id = 2, status = UploadStatus.Uploading),
            createReceipt(id = 3, status = UploadStatus.Failed),
            createReceipt(id = 4, status = UploadStatus.Completed)
        )
        queueFlow.value = testReceipts
        advanceUntilIdle()

        assertEquals(4, viewModel.receipts.value.size)
        assertEquals(2, viewModel.pendingCount.value) // Pending + Uploading
        assertEquals(1, viewModel.failedCount.value)  // Failed

        collectReceipts.cancel()
        collectPending.cancel()
        collectFailed.cancel()
    }

    @Test
    fun `retryAllFailed triggers repository retry and schedules work`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        viewModel.retryAllFailed()
        advanceUntilIdle()

        verify(repository).retryFailed()
        verify(uploadWorkScheduler).enqueueUploadQueue()
    }

    @Test
    fun `retry triggers repository single retry and schedules work`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        viewModel.retry(42L)
        advanceUntilIdle()

        verify(repository).retry(42L)
        verify(uploadWorkScheduler).enqueueUploadQueue()
    }

    @Test
    fun `clearCompleted triggers repository clear`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        viewModel.clearCompleted()
        advanceUntilIdle()

        verify(repository).clearCompleted()
    }

    @Test
    fun `delete triggers repository delete`() = runTest {
        val viewModel = QueueViewModel(repository, uploadWorkScheduler)
        
        viewModel.delete(100L)
        advanceUntilIdle()

        verify(repository).delete(100L)
    }

    private fun createReceipt(id: Long, status: UploadStatus): ScannedReceipt {
        return ScannedReceipt(
            id = id,
            localPath = "local/path/$id",
            processedPath = "processed/path/$id",
            fileName = "receipt_$id.jpg",
            vendor = "Vendor $id",
            amount = "10.00",
            receiptDate = "2026-06-19",
            createdAt = System.currentTimeMillis(),
            status = status
        )
    }
}
