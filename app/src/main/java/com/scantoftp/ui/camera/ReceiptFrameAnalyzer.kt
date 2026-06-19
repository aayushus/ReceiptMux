package com.scantoftp.ui.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.scantoftp.BuildConfig
import com.scantoftp.data.service.OpenCvReceiptToolkit

class ReceiptFrameAnalyzer(
    private val onResult: (FrameAnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {
    private var previousLuminance = -1.0
    private var previousFocusScore = -1.0
    private var previousBoundingBox: androidx.compose.ui.geometry.Rect? = null
    private var lastDebugLogAtMillis = 0L
    private var previousDetected = false
    private val detectionHistory = ArrayDeque<Boolean>()

    // Hot-path profiling: total analyze() occupancy + effective analysis FPS.
    private var lastTimingLogAtMillis = 0L
    private var timingWindowStartMillis = 0L
    private var framesSinceTimingLog = 0
    private var analyzeNanosSinceTimingLog = 0L
    private var maxAnalyzeNanosSinceTimingLog = 0L

    override fun analyze(image: ImageProxy) {
        val frameStartNanos = System.nanoTime()
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val detection = OpenCvReceiptToolkit.detectFromLumaPlane(
                bytes = data,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
            )
            val rotationDegrees = image.imageInfo.rotationDegrees
            val frameWidth = if (rotationDegrees % 180 == 0) image.width else image.height
            val frameHeight = if (rotationDegrees % 180 == 0) image.height else image.width
            val boundingBox = detection?.boundingBox?.let { rotateNormalizedRect(it, rotationDegrees) }
            val luminance = detection?.meanLuminance ?: 0.0
            val focusScore = detection?.focusScore ?: 0.0
            val interiorTexture = detection?.interiorTexture ?: 0.0
            val contrast = detection?.contrast ?: 0.0
            val glare = detection?.glare ?: 0.0
            val confidence = detection?.confidence ?: 0f
            val boxArea = boundingBox?.run { width * height } ?: 0f
            val centeredness = boundingBox?.let { rect ->
                val centerX = (rect.left + rect.right) / 2f
                val centerY = (rect.top + rect.bottom) / 2f
                val xPenalty = kotlin.math.abs(centerX - 0.5f) / 0.5f
                val yPenalty = kotlin.math.abs(centerY - 0.5f) / 0.5f
                (1f - ((xPenalty + yPenalty) / 2f)).coerceIn(0f, 1f)
            } ?: 0f
            val stabilityScore = if (boundingBox != null && previousBoundingBox != null) {
                val iou = calculateIntersectionOverUnion(previousBoundingBox!!, boundingBox)
                val centerShift = centerShift(previousBoundingBox!!, boundingBox)
                val spatialStability = (iou * 0.72f + (1f - (centerShift / 0.18f)).coerceIn(0f, 1f) * 0.28f)
                    .coerceIn(0f, 1f)
                val luminanceStability = (1f - (kotlin.math.abs(luminance - previousLuminance) / 38f).toFloat())
                    .coerceIn(0f, 1f)
                val focusStability = (1f - (kotlin.math.abs(focusScore - previousFocusScore) / 260f).toFloat())
                    .coerceIn(0f, 1f)
                (spatialStability * 0.66f + luminanceStability * 0.14f + focusStability * 0.20f)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val rawDetected = detection != null &&
                confidence >= 0.35f &&
                boxArea >= 0.08f &&
                centeredness >= 0.35f
            val hysteresisDetected = if (previousDetected) {
                detection != null && confidence >= 0.25f && boxArea >= 0.06f && centeredness >= 0.25f
            } else {
                detection != null && confidence >= 0.40f && boxArea >= 0.10f && centeredness >= 0.40f
            }
            detectionHistory.addLast(hysteresisDetected)
            if (detectionHistory.size > 6) detectionHistory.removeFirst()
            val detected = if (detectionHistory.size >= 4) {
                val positiveCount = detectionHistory.count { it }
                if (previousDetected) positiveCount >= 3 else positiveCount >= 4
            } else {
                hysteresisDetected
            }
            previousDetected = detected
            val exposureStable = luminance in 78.0..225.0
            val focusLocked = focusScore > 46.0
            val stable = detected && stabilityScore >= 0.82f
            val geometrySkew = detection?.orderedCorners?.let {
                OpenCvReceiptToolkit.maxCornerSkew(it)
            } ?: 0f

            val now = System.currentTimeMillis()
            if (BuildConfig.DEBUG && now - lastDebugLogAtMillis >= 1200L && (detected || confidence > 0.18f)) {
                lastDebugLogAtMillis = now
                Log.d(
                    "ReceiptMuxScanner",
                    "Analyzer detected=$detected raw=$rawDetected conf=${"%.2f".format(confidence)} area=${"%.2f".format(boxArea)} focus=${"%.0f".format(focusScore)} exposure=${"%.0f".format(luminance)} center=${"%.2f".format(centeredness)} stable=${"%.2f".format(stabilityScore)} history=${detectionHistory.count { it }}/${detectionHistory.size}",
                )
            }

            onResult(
                FrameAnalysisResult(
                    receiptDetected = detected,
                    stable = detected && stable,
                    focusLocked = focusLocked,
                    exposureStable = exposureStable,
                    deviceStill = false, // Set by ViewModel from AccelerometerStillnessMonitor
                    torchRecommended = luminance < 95,
                    boundingBox = boundingBox,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    detectionConfidence = confidence,
                    focusScore = focusScore.toFloat(),
                    luminance = luminance.toFloat(),
                    boxArea = boxArea,
                    centeredness = centeredness,
                    stabilityScore = stabilityScore,
                    geometrySkew = geometrySkew,
                    interiorTexture = interiorTexture.toFloat(),
                    contrast = contrast.toFloat(),
                    glare = glare.toFloat(),
                ),
            )

            previousLuminance = luminance
            previousFocusScore = focusScore
            previousBoundingBox = boundingBox
        } finally {
            logFrameTiming(frameStartNanos, image.width, image.height)
            image.close()
        }
    }

    private fun logFrameTiming(frameStartNanos: Long, frameWidth: Int, frameHeight: Int) {
        val analyzeNanos = System.nanoTime() - frameStartNanos
        analyzeNanosSinceTimingLog += analyzeNanos
        if (analyzeNanos > maxAnalyzeNanosSinceTimingLog) maxAnalyzeNanosSinceTimingLog = analyzeNanos
        framesSinceTimingLog++

        val now = System.currentTimeMillis()
        if (lastTimingLogAtMillis == 0L) {
            lastTimingLogAtMillis = now
            timingWindowStartMillis = now
            return
        }
        if (now - lastTimingLogAtMillis < 2000L) return

        val windowMillis = (now - timingWindowStartMillis).coerceAtLeast(1L)
        val fps = framesSinceTimingLog * 1000f / windowMillis
        val avgMs = (analyzeNanosSinceTimingLog / framesSinceTimingLog) / 1_000_000f
        val maxMs = maxAnalyzeNanosSinceTimingLog / 1_000_000f
        if (BuildConfig.DEBUG) {
            Log.d(
                "ReceiptMuxScanner",
                "Analyzer timing res=${frameWidth}x$frameHeight avg=${"%.1f".format(avgMs)}ms max=${"%.1f".format(maxMs)}ms fps=${"%.1f".format(fps)} frames=$framesSinceTimingLog",
            )
        }
        lastTimingLogAtMillis = now
        timingWindowStartMillis = now
        framesSinceTimingLog = 0
        analyzeNanosSinceTimingLog = 0L
        maxAnalyzeNanosSinceTimingLog = 0L
    }

    private fun calculateIntersectionOverUnion(
        first: androidx.compose.ui.geometry.Rect,
        second: androidx.compose.ui.geometry.Rect,
    ): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        if (right <= left || bottom <= top) return 0f

        val intersection = (right - left) * (bottom - top)
        val union = first.width * first.height + second.width * second.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun centerShift(
        first: androidx.compose.ui.geometry.Rect,
        second: androidx.compose.ui.geometry.Rect,
    ): Float {
        val firstCenterX = (first.left + first.right) / 2f
        val firstCenterY = (first.top + first.bottom) / 2f
        val secondCenterX = (second.left + second.right) / 2f
        val secondCenterY = (second.top + second.bottom) / 2f
        return kotlin.math.abs(firstCenterX - secondCenterX) + kotlin.math.abs(firstCenterY - secondCenterY)
    }
}
