package com.scantoftp.security

import com.scantoftp.ui.camera.AutoCaptureDecider
import com.scantoftp.ui.camera.FrameAnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoCaptureDeciderExtremeTest {

    private lateinit var decider: AutoCaptureDecider

    @Before
    fun setUp() {
        decider = AutoCaptureDecider()
    }

    private fun createBaseFrame(): FrameAnalysisResult {
        return FrameAnalysisResult(
            receiptDetected = true,
            stable = true,
            focusLocked = true,
            exposureStable = true,
            deviceStill = true,
            torchRecommended = false,
            boundingBox = null,
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
    fun `does not crash with NaN and Infinity metrics`() {
        // Feed malicious FrameAnalysisResults with NaN and Infinity values
        val nanFrame = createBaseFrame().copy(
            focusScore = Float.NaN,
            luminance = Float.POSITIVE_INFINITY,
            detectionConfidence = Float.NEGATIVE_INFINITY,
            centeredness = Float.NaN,
            boxArea = Float.NaN,
            stabilityScore = Float.NaN
        )

        // Evaluate should execute without throwing arithmetic exception or getting stuck
        val decision = decider.evaluate(
            result = nanFrame,
            motionStill = false,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 1000L
        )

        assertFalse(decision.shouldTrigger)
        assertTrue(decision.progress >= 0f)
    }

    @Test
    fun `handles extreme negative metric values safely`() {
        val negativeFrame = createBaseFrame().copy(
            focusScore = -1000f,
            luminance = -50f,
            detectionConfidence = -1.5f,
            boxArea = -0.5f,
            centeredness = -2.0f,
            stabilityScore = -0.1f,
            interiorTexture = -20f
        )

        val decision = decider.evaluate(
            result = negativeFrame,
            motionStill = false,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 1000L
        )

        assertFalse(decision.shouldTrigger)
        assertEquals(0f, decision.quality, 0.001f)
    }

    @Test
    fun `enforces absolute limits on progress values`() {
        val frameworkQualityFrame = createBaseFrame().copy(
            detectionConfidence = 2.0f, // impossible high values
            focusScore = 5000f,
            boxArea = 0.42f,
            centeredness = 1.5f,
            stabilityScore = 2.5f
        )

        // Evaluate several times to build progress
        var lastProgress = 0f
        repeat(10) { index ->
            val decision = decider.evaluate(
                result = frameworkQualityFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = 1000L + index * 100L
            )
            lastProgress = decision.progress
        }

        // Progress must never exceed 1f regardless of excessive quality scores
        assertTrue(lastProgress <= 1f)
    }

    @Test
    fun `respects autoCaptureEnabled and isProcessing flags`() {
        val strongFrame = createBaseFrame()

        // 1. When autoCaptureEnabled is false, progress and trigger must remain zeroed
        val decisionDisabled = decider.evaluate(
            result = strongFrame,
            motionStill = true,
            autoCaptureEnabled = false,
            isProcessing = false
        )
        assertFalse(decisionDisabled.shouldTrigger)
        assertEquals(0f, decisionDisabled.progress, 0f)

        // 2. When isProcessing is true, progress and trigger must remain zeroed
        val decisionProcessing = decider.evaluate(
            result = strongFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = true
        )
        assertFalse(decisionProcessing.shouldTrigger)
        assertEquals(0f, decisionProcessing.progress, 0f)
    }

    @Test
    fun `mitigates time overflow and negative timestamp anomalies`() {
        val strongFrame = createBaseFrame()

        // Evaluate to qualify and build progress
        var decision = decider.evaluate(
            result = strongFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = Long.MIN_VALUE // Extreme negative timestamp (e.g. system clock error)
        )
        assertFalse(decision.shouldTrigger)

        // Reset and test overflow with extreme large value
        decider.reset()
        // Run multiple qualified frames to force shouldTrigger = true
        repeat(5) { index ->
            decision = decider.evaluate(
                result = strongFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = Long.MAX_VALUE - 10000L + (index * 100L)
            )
        }
        
        // Cooldown behavior shouldn't block normal lifecycle operations
        decider.reset()
        val decisionCooldownReset = decider.evaluate(
            result = strongFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 1000L
        )
        assertFalse(decisionCooldownReset.shouldTrigger)
    }
}
