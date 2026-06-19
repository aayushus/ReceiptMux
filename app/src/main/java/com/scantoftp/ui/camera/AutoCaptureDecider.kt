package com.scantoftp.ui.camera

import kotlin.math.abs
import kotlin.math.max

data class AutoCaptureDecision(
    val progress: Float,
    val shouldTrigger: Boolean,
    val quality: Float = 0f,
    val qualifiedFrameCount: Int = 0,
)

private const val MIN_INTERIOR_TEXTURE = 45f

class AutoCaptureDecider {
    private var progress = 0f
    private var qualifiedFrames = 0
    private var cooldownUntilMillis = 0L
    private var consecutiveMissedFrames = 0
    private val focusHistory = ArrayDeque<Float>()
    private var focusPeakDetected = false

    fun evaluate(
        result: FrameAnalysisResult,
        motionStill: Boolean,
        autoCaptureEnabled: Boolean,
        isProcessing: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): AutoCaptureDecision {
        if (!autoCaptureEnabled || isProcessing) {
            progress = 0f
            qualifiedFrames = 0
            consecutiveMissedFrames = 0
            return AutoCaptureDecision(progress = 0f, shouldTrigger = false)
        }

        val quality = frameQuality(result, motionStill)
        focusPeakDetected = detectFocusPeak(result.focusScore)
        val qualified = result.receiptDetected &&
            result.focusLocked &&
            result.exposureStable &&
            result.interiorTexture >= MIN_INTERIOR_TEXTURE &&
            quality >= 0.70f &&
            (motionStill || result.stabilityScore >= 0.82f)

        if (result.receiptDetected) {
            consecutiveMissedFrames = 0
        } else {
            consecutiveMissedFrames++
        }

        progress = when {
            !result.receiptDetected && consecutiveMissedFrames >= 3 -> max(progress - 0.10f, 0f)
            !result.receiptDetected -> progress
            quality >= 0.80f -> (progress + 0.24f + (quality * 0.08f)).coerceAtMost(1f)
            quality >= 0.70f -> (progress + 0.16f).coerceAtMost(1f)
            quality >= 0.55f -> (progress + 0.08f).coerceAtMost(0.88f)
            else -> max(progress - 0.06f, 0.10f)
        }

        qualifiedFrames = when {
            qualified -> (qualifiedFrames + 1).coerceAtMost(10)
            result.receiptDetected -> max(qualifiedFrames - 1, 0)
            else -> 0
        }

        val shouldTrigger = qualified &&
            qualifiedFrames >= 4 &&
            progress >= 1f &&
            nowMillis >= cooldownUntilMillis &&
            (focusPeakDetected || qualifiedFrames >= 6)

        if (shouldTrigger) {
            progress = 0f
            qualifiedFrames = 0
            cooldownUntilMillis = nowMillis + 2_500L
        }

        return AutoCaptureDecision(
            progress = progress.coerceIn(0f, 1f),
            shouldTrigger = shouldTrigger,
            quality = quality,
            qualifiedFrameCount = qualifiedFrames,
        )
    }

    fun reset() {
        progress = 0f
        qualifiedFrames = 0
        cooldownUntilMillis = 0L
        consecutiveMissedFrames = 0
        focusHistory.clear()
        focusPeakDetected = false
    }

    private fun detectFocusPeak(focusScore: Float): Boolean {
        focusHistory.addLast(focusScore)
        if (focusHistory.size > 8) focusHistory.removeFirst()
        if (focusHistory.size < 4) return false

        val scores = focusHistory.toList()
        val midpoint = scores.size / 2
        val firstHalfAvg = scores.subList(0, midpoint).average().toFloat()
        val secondHalfAvg = scores.subList(midpoint, scores.size).average().toFloat()
        val peak = scores.max()
        val current = scores.last()

        // Peak detected: first half was rising, second half plateaued or dropped slightly,
        // and current is still near the peak (within 15%)
        return peak > 50f &&
            secondHalfAvg >= firstHalfAvg * 0.95f &&
            current >= peak * 0.85f &&
            (secondHalfAvg - firstHalfAvg) < firstHalfAvg * 0.10f
    }

    private fun frameQuality(result: FrameAnalysisResult, motionStill: Boolean): Float {
        val focusScore = ((result.focusScore - 40f) / 70f).coerceIn(0f, 1f)
        val exposureScore = when {
            result.luminance in 95f..210f -> 1f
            result.luminance in 80f..228f -> 0.72f
            else -> 0f
        }
        val geometryScore = (result.centeredness * 0.65f + idealAreaScore(result.boxArea) * 0.35f).coerceIn(0f, 1f)
        val stillnessScore = when {
            motionStill -> 1f
            result.stabilityScore >= 0.86f -> 0.82f
            result.stable -> 0.72f
            else -> 0.25f
        }

        return (
            result.detectionConfidence * 0.24f +
                focusScore * 0.22f +
                exposureScore * 0.14f +
                result.stabilityScore * 0.18f +
                stillnessScore * 0.12f +
                geometryScore * 0.10f
            ).coerceIn(0f, 1f)
    }

    private fun idealAreaScore(boxArea: Float): Float {
        if (boxArea <= 0f) return 0f
        val target = 0.42f
        return (1f - (abs(boxArea - target) / target)).coerceIn(0f, 1f)
    }
}
