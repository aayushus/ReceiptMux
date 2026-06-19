package com.scantoftp.ui.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureDeciderTest {
    @Test
    fun `triggers after several strong stable frames`() {
        val decider = AutoCaptureDecider()
        val strongFrame = FrameAnalysisResult(
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
            interiorTexture = 80f,
        )

        var triggered = false
        repeat(5) { index ->
            triggered = triggered || decider.evaluate(
                result = strongFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = 1_000L + (index * 100L),
            ).shouldTrigger
        }

        assertTrue(triggered)
    }

    @Test
    fun `does not trigger when frames stay blurry`() {
        val decider = AutoCaptureDecider()
        val blurryFrame = FrameAnalysisResult(
            receiptDetected = true,
            stable = true,
            focusLocked = false,
            exposureStable = true,
            deviceStill = true,
            torchRecommended = false,
            boundingBox = null,
            frameWidth = 1080,
            frameHeight = 1920,
            detectionConfidence = 0.82f,
            focusScore = 18f,
            luminance = 150f,
            boxArea = 0.38f,
            centeredness = 0.92f,
            stabilityScore = 0.88f,
            interiorTexture = 80f,
        )

        var triggered = false
        repeat(8) { index ->
            triggered = decider.evaluate(
                result = blurryFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = 2_000L + (index * 100L),
            ).shouldTrigger
        }

        assertFalse(triggered)
    }

    @Test
    fun `triggers with consistent mid-quality frames`() {
        val decider = AutoCaptureDecider()
        val midFrame = FrameAnalysisResult(
            receiptDetected = true,
            stable = true,
            focusLocked = true,
            exposureStable = true,
            deviceStill = true,
            torchRecommended = false,
            boundingBox = null,
            frameWidth = 1080,
            frameHeight = 1920,
            detectionConfidence = 0.78f,
            focusScore = 95f,
            luminance = 140f,
            boxArea = 0.38f,
            centeredness = 0.88f,
            stabilityScore = 0.85f,
            interiorTexture = 80f,
        )

        var triggered = false
        repeat(12) { index ->
            triggered = triggered || decider.evaluate(
                result = midFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = 3_000L + (index * 100L),
            ).shouldTrigger
        }

        assertTrue("mid-quality frames should eventually trigger auto-capture", triggered)
    }

    @Test
    fun `grace period holds progress during brief detection drop`() {
        val decider = AutoCaptureDecider()
        // Use a frame that builds progress but won't trigger (focusLocked=false prevents qualified)
        val progressFrame = FrameAnalysisResult(
            receiptDetected = true,
            stable = true,
            focusLocked = false,
            exposureStable = true,
            deviceStill = true,
            torchRecommended = false,
            boundingBox = null,
            frameWidth = 1080,
            frameHeight = 1920,
            detectionConfidence = 0.85f,
            focusScore = 110f,
            luminance = 148f,
            boxArea = 0.40f,
            centeredness = 0.92f,
            stabilityScore = 0.90f,
            interiorTexture = 80f,
        )
        val missedFrame = progressFrame.copy(receiptDetected = false, detectionConfidence = 0f)

        // Build up progress with 3 good frames
        repeat(3) { index ->
            decider.evaluate(
                result = progressFrame,
                motionStill = true,
                autoCaptureEnabled = true,
                isProcessing = false,
                nowMillis = 4_000L + (index * 100L),
            )
        }
        val progressBeforeDrop = decider.evaluate(
            result = progressFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 4_300L,
        ).progress

        // Two missed frames should be held by grace period (no decay)
        val progressAfterOneMiss = decider.evaluate(
            result = missedFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 4_400L,
        ).progress

        val progressAfterTwoMisses = decider.evaluate(
            result = missedFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 4_500L,
        ).progress

        assertTrue(
            "progress should not decay during grace period (1 miss), was $progressBeforeDrop -> $progressAfterOneMiss",
            progressAfterOneMiss == progressBeforeDrop,
        )
        assertTrue(
            "progress should not decay during grace period (2 misses), was $progressBeforeDrop -> $progressAfterTwoMisses",
            progressAfterTwoMisses == progressBeforeDrop,
        )

        // Third miss should start decaying
        val progressAfterThreeMisses = decider.evaluate(
            result = missedFrame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 4_600L,
        ).progress

        assertTrue(
            "progress should decay after grace period exhausted, was $progressBeforeDrop -> $progressAfterThreeMisses",
            progressAfterThreeMisses < progressBeforeDrop,
        )
    }

    @Test
    fun `decision includes quality and qualified frame count`() {
        val decider = AutoCaptureDecider()
        val frame = FrameAnalysisResult(
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
            interiorTexture = 80f,
        )

        val decision = decider.evaluate(
            result = frame,
            motionStill = true,
            autoCaptureEnabled = true,
            isProcessing = false,
            nowMillis = 5_000L,
        )

        assertTrue("quality should be populated", decision.quality > 0f)
        assertTrue("qualifiedFrameCount should be at least 1", decision.qualifiedFrameCount >= 1)
    }
}
