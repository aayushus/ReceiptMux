package com.scantoftp.data.repository

import com.scantoftp.domain.model.CaptureSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class InMemoryCaptureSessionRepositoryTest {
    @Test
    fun `discard current capture deletes raw and processed files`() {
        runBlocking {
            val repository = InMemoryCaptureSessionRepository()
            val raw = File.createTempFile("receiptmux_raw", ".jpg")
            val processed = File.createTempFile("receiptmux_processed", ".jpg")

            repository.setCurrentCapture(
                CaptureSession(
                    originalPath = raw.absolutePath,
                    processedPath = processed.absolutePath,
                    suggestedFileName = "Mar_29_Test_1.00.jpg",
                    vendor = "Test",
                    amount = "1.00",
                    receiptDate = "Mar_29",
                    captureTimestamp = 1L,
                    cropConfidence = 1f,
                ),
            )

            repository.discardCurrentCapture()

            assertFalse(raw.exists())
            assertFalse(processed.exists())
            assertNull(repository.currentCapture.value)
        }
    }
}
