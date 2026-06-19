package com.scantoftp.ui.viewmodel

import androidx.compose.ui.geometry.Rect
import com.scantoftp.domain.model.CaptureSession
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.ProcessingResult
import com.scantoftp.domain.service.OcrResult
import com.scantoftp.domain.service.ReceiptProcessor
import com.scantoftp.domain.service.ReceiptTextRecognizer
import com.scantoftp.ui.camera.FrameAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    
    private val settingsRepository: SettingsRepository = mock()
    private val captureSessionRepository: CaptureSessionRepository = mock()
    private val receiptProcessor: ReceiptProcessor = mock()
    private val receiptTextRecognizer: ReceiptTextRecognizer = mock()

    private val settingsFlow = MutableStateFlow(FtpSettings())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(settingsRepository.settingsFlow()).thenReturn(settingsFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createBaseFrame(): FrameAnalysisResult {
        return FrameAnalysisResult(
            receiptDetected = true,
            stable = true,
            focusLocked = true,
            exposureStable = true,
            deviceStill = true,
            torchRecommended = false,
            boundingBox = Rect(0.1f, 0.1f, 0.9f, 0.9f),
            frameWidth = 1080,
            frameHeight = 1920,
            detectionConfidence = 0.9f,
            focusScore = 120f,
            luminance = 148f,
            boxArea = 0.40f,
            centeredness = 0.94f,
            stabilityScore = 0.91f,
            interiorTexture = 80f
        )
    }

    @Test
    fun `initial states are correctly instantiated`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )
        
        advanceUntilIdle()

        assertFalse(viewModel.isProcessingCapture.value)
        assertEquals(0L, viewModel.captureRequestToken.value)
        assertEquals(0L, viewModel.shutterCueToken.value)
        assertFalse(viewModel.setupReady.value)
        assertNull(viewModel.detectionState.value.errorMessage)
    }

    @Test
    fun `cycleFlashMode loops from Auto to AlwaysOn to AlwaysOff and updates repository`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )
        val collectJob = launch { viewModel.settings.collect {} }
        
        // Starts with default Auto
        settingsFlow.value = FtpSettings(flashMode = FlashMode.Auto)
        advanceUntilIdle()

        // 1st Cycle -> AlwaysOn
        viewModel.cycleFlashMode()
        advanceUntilIdle()
        verify(settingsRepository).setFlashMode(FlashMode.AlwaysOn)

        // 2nd Cycle -> AlwaysOff
        settingsFlow.value = FtpSettings(flashMode = FlashMode.AlwaysOn)
        viewModel.cycleFlashMode()
        advanceUntilIdle()
        verify(settingsRepository).setFlashMode(FlashMode.AlwaysOff)

        // 3rd Cycle -> Auto
        settingsFlow.value = FtpSettings(flashMode = FlashMode.AlwaysOff)
        viewModel.cycleFlashMode()
        advanceUntilIdle()
        verify(settingsRepository).setFlashMode(FlashMode.Auto)

        collectJob.cancel()
    }

    @Test
    fun `smoothBoundingBox implements exponential smoothing correctly`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )

        // First frame: smoothed bounding box is initialized to the frame box
        val frame1 = createBaseFrame().copy(boundingBox = Rect(10f, 20f, 30f, 40f))
        viewModel.onFrameAnalyzed(frame1)
        
        var currentBox = viewModel.detectionState.value.boundingBox
        assertNotNull(currentBox)
        assertEquals(10f, currentBox!!.left, 0.001f)
        assertEquals(20f, currentBox.top, 0.001f)

        // Second frame: interpolated with alpha = 0.28
        // Next left: 10 + (20 - 10) * 0.28 = 12.8f
        // Next top: 20 + (40 - 20) * 0.28 = 25.6f
        val frame2 = createBaseFrame().copy(boundingBox = Rect(20f, 40f, 60f, 80f))
        viewModel.onFrameAnalyzed(frame2)
        
        currentBox = viewModel.detectionState.value.boundingBox
        assertNotNull(currentBox)
        assertEquals(12.8f, currentBox!!.left, 0.01f)
        assertEquals(25.6f, currentBox.top, 0.01f)

        // Third frame: passing null resets the smoothed box
        val frameNull = createBaseFrame().copy(boundingBox = null)
        viewModel.onFrameAnalyzed(frameNull)
        assertNull(viewModel.detectionState.value.boundingBox)
    }

    @Test
    fun `throttles subsequent capture requests when already processing`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )

        viewModel.requestManualCapture()
        assertTrue(viewModel.isProcessingCapture.value)
        val originalToken = viewModel.captureRequestToken.value

        // Try requesting manual capture again immediately
        viewModel.requestManualCapture()
        // Capture request token should remain identical (not updated)
        assertEquals(originalToken, viewModel.captureRequestToken.value)
    }

    @Test
    fun `onImageCaptured processes image on happy path and saves to capture session`() = runBlocking {
        // Use Unconfined dispatcher for main-thread tasks inside runBlocking to execute eagerly
        Dispatchers.setMain(Dispatchers.Unconfined)
        
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )
        
        settingsFlow.value = FtpSettings(ocrRenamingEnabled = true)
        
        val tempRawFile = File.createTempFile("raw_receipt", ".jpg")
        val processedPath = "/processed/file.jpg"
        
        whenever(receiptProcessor.process(tempRawFile.absolutePath))
            .thenReturn(ProcessingResult(processedPath, 0.95f))
        whenever(receiptTextRecognizer.recognize(eq(processedPath), any()))
            .thenReturn(OcrResult("Starbucks", "4.75", "Jun_19", "Jun_19_Starbucks_4.75.jpg"))

        // Set processing capture to true BEFORE trigger callback
        viewModel.requestManualCapture()
        assertTrue(viewModel.isProcessingCapture.value)

        var onReadyCalled = false
        viewModel.onImageCaptured(tempRawFile.absolutePath) {
            onReadyCalled = true
        }

        // Suspend until isProcessingCapture becomes false (background thread has finished)
        viewModel.isProcessingCapture.first { !it }

        assertTrue(onReadyCalled)
        assertFalse(viewModel.isProcessingCapture.value)
        assertNull(viewModel.detectionState.value.errorMessage)
        
        // Verify captureSessionRepository was updated correctly
        verify(captureSessionRepository).setCurrentCapture(any())

        tempRawFile.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `onImageCaptured handles processing timeouts safely`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )
        
        val tempRawFile = File.createTempFile("raw_timeout", ".jpg")
        
        // Simulate a slow processing pipeline exceeding 30 seconds
        whenever(receiptProcessor.process(tempRawFile.absolutePath)).thenAnswer {
            Thread.sleep(100) // Small sleep to allow test main thread to schedule timeout
            ProcessingResult("/processed/file.jpg", 0.95f)
        }
        
        // Set processing capture to true before trigger callback
        viewModel.requestManualCapture()
        assertTrue(viewModel.isProcessingCapture.value)

        var onReadyCalled = false
        val job = launch {
            viewModel.isProcessingCapture.first { !it }
        }

        viewModel.onImageCaptured(tempRawFile.absolutePath) {
            onReadyCalled = true
        }
        
        // Advance virtual coroutine clock past 30 seconds to trigger timeout
        advanceTimeBy(31_000L)
        advanceUntilIdle()
        job.join()

        // Ready callback should not be invoked, state updated to not processing, and error shown
        assertFalse(onReadyCalled)
        assertFalse(viewModel.isProcessingCapture.value)
        assertEquals("Processing took too long. Try again.", viewModel.detectionState.value.errorMessage)

        tempRawFile.delete()
    }

    @Test
    fun `onImageCaptured handles exceptions safely by cleaning up and setting error`() = runBlocking {
        // Use Unconfined dispatcher for main-thread tasks inside runBlocking to execute eagerly
        Dispatchers.setMain(Dispatchers.Unconfined)
        
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )

        val tempRawFile = File.createTempFile("raw_fail", ".jpg")
        
        // Force an exception during processing
        whenever(receiptProcessor.process(tempRawFile.absolutePath)).thenAnswer {
            throw RuntimeException("OpenCV warp failed")
        }

        // Set processing capture to true before trigger callback
        viewModel.requestManualCapture()
        assertTrue(viewModel.isProcessingCapture.value)

        var onReadyCalled = false
        viewModel.onImageCaptured(tempRawFile.absolutePath) {
            onReadyCalled = true
        }
        
        // Suspend until completed
        viewModel.isProcessingCapture.first { !it }

        assertFalse(onReadyCalled)
        assertFalse(viewModel.isProcessingCapture.value)
        assertEquals("OpenCV warp failed", viewModel.detectionState.value.errorMessage)

        // Capture session should NEVER be saved
        verify(captureSessionRepository, never()).setCurrentCapture(any())

        tempRawFile.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `onCaptureFailed resets progress and updates detectionState with error`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )

        viewModel.requestManualCapture()
        assertTrue(viewModel.isProcessingCapture.value)

        viewModel.onCaptureFailed()
        
        assertFalse(viewModel.isProcessingCapture.value)
        assertEquals(0f, viewModel.detectionState.value.progress, 0f)
        assertEquals("Unable to capture receipt. Try again.", viewModel.detectionState.value.errorMessage)
    }

    @Test
    fun `clearError clears existing errorMessage on detectionState`() = runTest {
        val viewModel = ScannerViewModel(
            settingsRepository,
            captureSessionRepository,
            receiptProcessor,
            receiptTextRecognizer
        )

        viewModel.onCaptureFailed()
        assertEquals("Unable to capture receipt. Try again.", viewModel.detectionState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.detectionState.value.errorMessage)
    }
}
