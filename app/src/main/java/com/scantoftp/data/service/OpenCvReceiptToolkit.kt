package com.scantoftp.data.service

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.ui.geometry.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class OpenCvReceiptDetection(
    val orderedCorners: List<PointF>,
    val boundingBox: Rect,
    val confidence: Float,
    val focusScore: Double,
    val meanLuminance: Double,
    // Laplacian variance of the region interior (border excluded). Receipts carry
    // dense text and stay high; blank/smooth rectangles (e.g. a mouse pad) stay low.
    val interiorTexture: Double = 0.0,
    // Std-dev of pixel intensity in the region; higher = stronger text/paper contrast.
    val contrast: Double = 0.0,
    // Fraction (0..1) of near-white blown-out pixels in the region; higher = more glare.
    val glare: Double = 0.0,
)

object OpenCvReceiptToolkit {
    init {
        runCatching { OpenCVLoader.initLocal() }
    }

    private fun releaseAll(vararg mats: Mat?) {
        mats.forEach { mat -> mat?.let { runCatching { it.release() } } }
    }

    fun detectFromBitmap(bitmap: Bitmap): OpenCvReceiptDetection? {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        return try {
            detectFromRgbaMat(rgba)
        } finally {
            releaseAll(rgba)
        }
    }

    fun detectFromLumaPlane(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): OpenCvReceiptDetection? {
        val grayscaleBytes = ByteArray(width * height)
        if (pixelStride == 1) {
            // Fast path (e.g. YUV_420_888 luma plane): copy each row in bulk.
            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (rowStart + width <= bytes.size) {
                    System.arraycopy(bytes, rowStart, grayscaleBytes, y * width, width)
                }
            }
        } else {
            for (y in 0 until height) {
                val rowStart = y * rowStride
                val dstRow = y * width
                for (x in 0 until width) {
                    val sourceIndex = rowStart + (x * pixelStride)
                    if (sourceIndex < bytes.size) {
                        grayscaleBytes[dstRow + x] = bytes[sourceIndex]
                    }
                }
            }
        }
        val grayscale = Mat(height, width, CvType.CV_8UC1)
        grayscale.put(0, 0, grayscaleBytes)
        return try {
            detectFromGrayMat(grayscale)
        } finally {
            releaseAll(grayscale)
        }
    }

    fun warpReceipt(bitmap: Bitmap, detection: OpenCvReceiptDetection): Bitmap {
        val source = Mat()
        Utils.bitmapToMat(bitmap, source)
        val ordered = detection.orderedCorners
        val targetWidth = max(
            distance(ordered[0], ordered[1]),
            distance(ordered[2], ordered[3]),
        ).toInt().coerceAtLeast(1)
        val targetHeight = max(
            distance(ordered[0], ordered[3]),
            distance(ordered[1], ordered[2]),
        ).toInt().coerceAtLeast(1)

        val sourceCorners = MatOfPoint2f(
            Point(ordered[0].x.toDouble(), ordered[0].y.toDouble()),
            Point(ordered[1].x.toDouble(), ordered[1].y.toDouble()),
            Point(ordered[2].x.toDouble(), ordered[2].y.toDouble()),
            Point(ordered[3].x.toDouble(), ordered[3].y.toDouble()),
        )
        val targetCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetWidth - 1.0, 0.0),
            Point(targetWidth - 1.0, targetHeight - 1.0),
            Point(0.0, targetHeight - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(sourceCorners, targetCorners)
        val warped = Mat(Size(targetWidth.toDouble(), targetHeight.toDouble()), source.type())
        try {
            Imgproc.warpPerspective(source, warped, transform, warped.size())
            val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, result)
            return result
        } finally {
            releaseAll(source, sourceCorners, targetCorners, transform, warped)
        }
    }

    private fun detectFromRgbaMat(rgba: Mat): OpenCvReceiptDetection? {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        return try {
            detectInternal(gray)
        } finally {
            releaseAll(gray)
        }
    }

    private fun detectFromGrayMat(gray: Mat): OpenCvReceiptDetection? = detectInternal(gray)

    private data class ContourDetectionResult(
        val orderedCorners: List<PointF>,
        val confidence: Double,
    )

    private fun detectInternal(gray: Mat): OpenCvReceiptDetection? {
        val originalWidth = gray.width()
        val originalHeight = gray.height()
        val maxDim = max(originalWidth, originalHeight).toDouble()

        val result960 = findBestContourAtScale(gray, 960.0)
        val bestContour = if ((result960 == null || result960.confidence < 0.60) && maxDim > 960.0) {
            val targetHigher = minOf(maxDim, 1440.0)
            val resultHigher = findBestContourAtScale(gray, targetHigher)
            listOfNotNull(result960, resultHigher).maxByOrNull { it.confidence }
        } else {
            result960
        } ?: return null

        val boundingRect = boundingRectForPoints(bestContour.orderedCorners, originalWidth, originalHeight)
        val focusRect = org.opencv.core.Rect(
            (boundingRect.left * originalWidth).toInt().coerceAtLeast(0),
            (boundingRect.top * originalHeight).toInt().coerceAtLeast(0),
            ((boundingRect.width * originalWidth).toInt()).coerceAtLeast(1),
            ((boundingRect.height * originalHeight).toInt()).coerceAtLeast(1),
        )
        val clampedFocusRect = focusRect.intersectWithin(originalWidth, originalHeight)
        val focusRoi = Mat(gray, clampedFocusRect)
        val focusScore: Double
        val luminance: Double
        val contrast: Double
        val glare: Double
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        val glareMask = Mat()
        try {
            focusScore = laplacianVariance(focusRoi)
            Core.meanStdDev(focusRoi, mean, stddev)
            luminance = mean.get(0, 0)?.firstOrNull() ?: 0.0
            contrast = stddev.get(0, 0)?.firstOrNull() ?: 0.0
            // Glare = fraction of near-blown-out (very bright) pixels in the region.
            Imgproc.threshold(focusRoi, glareMask, 245.0, 255.0, Imgproc.THRESH_BINARY)
            val pixelCount = (focusRoi.rows() * focusRoi.cols()).coerceAtLeast(1)
            glare = Core.countNonZero(glareMask).toDouble() / pixelCount
        } finally {
            releaseAll(focusRoi, mean, stddev, glareMask)
        }
        val interiorTexture = interiorTextureScore(gray, clampedFocusRect, originalWidth, originalHeight, focusScore)

        return OpenCvReceiptDetection(
            orderedCorners = bestContour.orderedCorners,
            boundingBox = boundingRect,
            confidence = bestContour.confidence.toFloat().coerceIn(0f, 1f),
            focusScore = focusScore,
            meanLuminance = luminance,
            interiorTexture = interiorTexture,
            contrast = contrast,
            glare = glare,
        )
    }

    /**
     * Laplacian variance measured on an inset of the detected region so the strong
     * edges of the receipt border don't dominate. Text-bearing receipts keep a high
     * value here; smooth surfaces (a mouse pad, a table, a card) collapse toward zero.
     */
    private fun interiorTextureScore(
        gray: Mat,
        focusRect: org.opencv.core.Rect,
        width: Int,
        height: Int,
        fallback: Double,
    ): Double {
        val insetX = (focusRect.width * 0.18).toInt()
        val insetY = (focusRect.height * 0.18).toInt()
        val interiorWidth = focusRect.width - insetX * 2
        val interiorHeight = focusRect.height - insetY * 2
        if (interiorWidth < 12 || interiorHeight < 12) return fallback
        val interiorRect = org.opencv.core.Rect(
            focusRect.x + insetX,
            focusRect.y + insetY,
            interiorWidth,
            interiorHeight,
        ).intersectWithin(width, height)
        val interiorRoi = Mat(gray, interiorRect)
        return try {
            laplacianVariance(interiorRoi)
        } finally {
            releaseAll(interiorRoi)
        }
    }

    private fun findBestContourAtScale(gray: Mat, targetMaxDimension: Double): ContourDetectionResult? {
        val originalWidth = gray.width()
        val originalHeight = gray.height()
        val maxDimension = max(originalWidth, originalHeight).toDouble()
        val scale = if (maxDimension > targetMaxDimension) targetMaxDimension / maxDimension else 1.0
        val workingGray = if (scale < 1.0) {
            Mat().also {
                Imgproc.resize(gray, it, Size(originalWidth * scale, originalHeight * scale))
            }
        } else {
            gray.clone()
        }

        val blurred = Mat()
        val cannyEdges = Mat()
        val adaptiveMask = Mat()
        val combinedEdges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.GaussianBlur(workingGray, blurred, Size(5.0, 5.0), 0.0)

            Imgproc.Canny(blurred, cannyEdges, 75.0, 200.0)

            Imgproc.adaptiveThreshold(
                blurred,
                adaptiveMask,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                10.0,
            )
            Core.bitwise_not(adaptiveMask, adaptiveMask)
            Imgproc.morphologyEx(
                adaptiveMask,
                adaptiveMask,
                Imgproc.MORPH_CLOSE,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(11.0, 11.0)),
            )

            Core.bitwise_or(cannyEdges, adaptiveMask, combinedEdges)
            Imgproc.dilate(
                combinedEdges,
                combinedEdges,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)),
            )

            Imgproc.findContours(combinedEdges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val imageArea = workingGray.width().toDouble() * workingGray.height().toDouble()
            val imageCenterX = workingGray.width() / 2.0
            val imageCenterY = workingGray.height() / 2.0
            var bestDetection: Pair<List<Point>, Double>? = null

            contours.forEach { contour ->
                val candidate = scoreContourCandidate(
                    contour = contour,
                    imageArea = imageArea,
                    imageCenterX = imageCenterX,
                    imageCenterY = imageCenterY,
                    workingGray = workingGray,
                ) ?: return@forEach

                if (bestDetection == null || candidate.second > bestDetection!!.second) {
                    bestDetection = candidate
                }
            }

            val (bestPoints, confidenceScore) = bestDetection ?: return null
            val refined = refineCorners(workingGray, bestPoints)
            val ordered = orderCorners(
                refined.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray(),
            ).map { point ->
                PointF((point.x / scale).toFloat(), (point.y / scale).toFloat())
            }

            return ContourDetectionResult(orderedCorners = ordered, confidence = confidenceScore)
        } finally {
            contours.forEach { releaseAll(it) }
            releaseAll(workingGray, blurred, cannyEdges, adaptiveMask, combinedEdges, hierarchy)
        }
    }

    private fun refineCorners(gray: Mat, corners: List<Point>): List<PointF> {
        if (corners.size != 4) return corners.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        val cornersMat = MatOfPoint2f(*corners.toTypedArray())
        return try {
            Imgproc.cornerSubPix(
                gray,
                cornersMat,
                Size(11.0, 11.0),
                Size(-1.0, -1.0),
                TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.001),
            )
            cornersMat.toList().map { PointF(it.x.toFloat(), it.y.toFloat()) }
        } catch (_: Exception) {
            corners.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        } finally {
            releaseAll(cornersMat)
        }
    }

    private fun scoreContourCandidate(
        contour: MatOfPoint,
        imageArea: Double,
        imageCenterX: Double,
        imageCenterY: Double,
        workingGray: Mat,
    ): Pair<List<Point>, Double>? {
        if (contour.total() < 4) return null

        val scratch = mutableListOf<Mat>()
        try {
            val contour2f = MatOfPoint2f(*contour.toArray()).also(scratch::add)
            val contourArea = Imgproc.contourArea(contour)
            if (contourArea < imageArea * 0.08) return null

            val perimeter = Imgproc.arcLength(contour2f, true)
            val approximated = MatOfPoint2f().also(scratch::add)
            Imgproc.approxPolyDP(contour2f, approximated, perimeter * 0.02, true)
            val approximatedPoints = approximated.toArray().toList()
            val useApproximatedQuad = approximatedPoints.size == 4 &&
                isConvex(approximatedPoints, scratch)

            val candidatePoints = when {
                useApproximatedQuad -> approximatedPoints
                approximatedPoints.size == 3 && isConvex(approximatedPoints, scratch) -> {
                    // Partial occlusion: infer 4th corner from 3 visible corners
                    inferFourthCorner(approximatedPoints)
                }
                approximatedPoints.size == 5 && isConvex(approximatedPoints, scratch) -> {
                    // Merge closest pair of corners to get a quad
                    mergeClosestCorners(approximatedPoints)
                }
                else -> {
                    val rotatedRect = Imgproc.minAreaRect(contour2f)
                    Array(4) { Point() }.also { rotatedRect.points(it) }.toList()
                }
            }

            val orderedCandidate = orderCorners(candidatePoints.toTypedArray()).map {
                Point(it.x.toDouble(), it.y.toDouble())
            }

            val quadArea = polygonArea(orderedCandidate)
            if (quadArea < imageArea * 0.08 || quadArea > imageArea * 0.98) return null

            val averageWidth = (distance(orderedCandidate[0], orderedCandidate[1]) + distance(orderedCandidate[2], orderedCandidate[3])) / 2.0
            val averageHeight = (distance(orderedCandidate[0], orderedCandidate[3]) + distance(orderedCandidate[1], orderedCandidate[2])) / 2.0
            if (averageWidth <= 0.0 || averageHeight <= 0.0) return null

            val aspectRatio = max(averageWidth, averageHeight) / minOf(averageWidth, averageHeight)
            if (aspectRatio !in 1.2..14.0) return null

            val rectangularity = (contourArea / quadArea).coerceIn(0.0, 1.0)
            if (rectangularity < 0.65) return null

            val centerX = orderedCandidate.map { it.x }.average()
            val centerY = orderedCandidate.map { it.y }.average()
            val centerPenalty = (
                abs(centerX - imageCenterX) / imageCenterX +
                    abs(centerY - imageCenterY) / imageCenterY
                ) / 2.0
            val centeredness = (1.0 - centerPenalty).coerceIn(0.0, 1.0)
            val areaRatio = (quadArea / imageArea).coerceIn(0.0, 1.0)
            val roi = boundingRectForMatPoints(orderedCandidate, workingGray.width(), workingGray.height())
            if (roi.width <= 0 || roi.height <= 0) return null
            val roiMat = Mat(workingGray, roi).also(scratch::add)
            val whiteness = (Core.mean(roiMat).`val`[0] / 255.0).coerceIn(0.0, 1.0)
            val quadBonus = if (useApproximatedQuad) 1.0 else 0.72

            val score = areaRatio * 0.38 +
                rectangularity * 0.24 +
                centeredness * 0.16 +
                whiteness * 0.10 +
                quadBonus * 0.12

            return orderedCandidate to score
        } finally {
            scratch.forEach { releaseAll(it) }
        }
    }

    private fun isConvex(points: List<Point>, scratch: MutableList<Mat>): Boolean {
        val mat = MatOfPoint(*points.toTypedArray()).also(scratch::add)
        return Imgproc.isContourConvex(mat)
    }

    private fun inferFourthCorner(threeCorners: List<Point>): List<Point> {
        val (a, b, c) = threeCorners
        // Try all 3 possible missing-corner positions using parallelogram identity
        val candidates = listOf(
            listOf(a, b, c, Point(a.x + c.x - b.x, a.y + c.y - b.y)),
            listOf(a, b, Point(a.x + b.x - c.x, a.y + b.y - c.y), c),
            listOf(Point(b.x + c.x - a.x, b.y + c.y - a.y), a, b, c),
        )
        // Pick the candidate that forms the most rectangular quad (closest to 90° angles)
        return candidates.minByOrNull { pts ->
            val ordered = orderCorners(pts.toTypedArray()).map { Point(it.x.toDouble(), it.y.toDouble()) }
            var totalDeviation = 0.0
            for (i in ordered.indices) {
                val prev = ordered[(i + 3) % 4]
                val curr = ordered[i]
                val next = ordered[(i + 1) % 4]
                val v1x = prev.x - curr.x; val v1y = prev.y - curr.y
                val v2x = next.x - curr.x; val v2y = next.y - curr.y
                val dot = v1x * v2x + v1y * v2y
                val mag = hypot(v1x, v1y) * hypot(v2x, v2y)
                if (mag > 1e-6) {
                    val angle = Math.toDegrees(kotlin.math.acos((dot / mag).coerceIn(-1.0, 1.0)))
                    totalDeviation += abs(angle - 90.0)
                }
            }
            totalDeviation
        } ?: threeCorners + Point(threeCorners[0].x, threeCorners[2].y)
    }

    private fun mergeClosestCorners(fiveCorners: List<Point>): List<Point> {
        var minDist = Double.MAX_VALUE
        var mergeI = 0
        var mergeJ = 1
        for (i in fiveCorners.indices) {
            for (j in i + 1 until fiveCorners.size) {
                val d = distance(fiveCorners[i], fiveCorners[j])
                if (d < minDist) {
                    minDist = d
                    mergeI = i
                    mergeJ = j
                }
            }
        }
        val merged = Point(
            (fiveCorners[mergeI].x + fiveCorners[mergeJ].x) / 2.0,
            (fiveCorners[mergeI].y + fiveCorners[mergeJ].y) / 2.0,
        )
        val result = fiveCorners.toMutableList()
        result.removeAt(mergeJ)
        result[mergeI] = merged
        return result
    }

    private fun orderCorners(points: Array<Point>): List<PointF> {
        val centroidX = points.map { it.x }.average()
        val centroidY = points.map { it.y }.average()
        val sorted = points
            .sortedBy { point -> kotlin.math.atan2(point.y - centroidY, point.x - centroidX) }
            .map { PointF(it.x.toFloat(), it.y.toFloat()) }
        val topLeftIndex = sorted.indices.minByOrNull { index ->
            sorted[index].x + sorted[index].y
        } ?: 0
        val rotated = List(sorted.size) { offset ->
            sorted[(topLeftIndex + offset) % sorted.size]
        }
        val clockwise = if (cross(rotated[0], rotated[1], rotated[2]) < 0f) {
            listOf(rotated[0], rotated[3], rotated[2], rotated[1])
        } else {
            rotated
        }
        return listOf(
            clockwise[0],
            clockwise[1],
            clockwise[2],
            clockwise[3],
        )
    }

    private fun polygonArea(points: List<Point>): Double {
        var area = 0.0
        points.indices.forEach { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            area += (current.x * next.y) - (next.x * current.y)
        }
        return abs(area) / 2.0
    }

    private fun laplacianVariance(gray: Mat): Double {
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        try {
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stddev)
            val sigma = stddev.get(0, 0).firstOrNull() ?: 0.0
            return sigma * sigma
        } finally {
            releaseAll(laplacian, mean, stddev)
        }
    }

    private fun distance(first: PointF, second: PointF): Double {
        return hypot((second.x - first.x).toDouble(), (second.y - first.y).toDouble())
    }

    private fun distance(first: Point, second: Point): Double {
        return hypot(second.x - first.x, second.y - first.y)
    }

    private fun cross(first: PointF, second: PointF, third: PointF): Float {
        return (second.x - first.x) * (third.y - first.y) - (second.y - first.y) * (third.x - first.x)
    }

    private fun boundingRectForPoints(
        points: List<PointF>,
        width: Int,
        height: Int,
    ): Rect {
        val minX = points.minOf { it.x }.coerceAtLeast(0f)
        val minY = points.minOf { it.y }.coerceAtLeast(0f)
        val maxX = points.maxOf { it.x }.coerceAtMost(width.toFloat())
        val maxY = points.maxOf { it.y }.coerceAtMost(height.toFloat())
        return Rect(
            left = minX / width,
            top = minY / height,
            right = maxX / width,
            bottom = maxY / height,
        )
    }

    private fun boundingRectForMatPoints(
        points: List<Point>,
        width: Int,
        height: Int,
    ): org.opencv.core.Rect {
        val minX = points.minOf { it.x }.toInt().coerceAtLeast(0)
        val minY = points.minOf { it.y }.toInt().coerceAtLeast(0)
        val maxX = points.maxOf { it.x }.toInt().coerceAtMost(width)
        val maxY = points.maxOf { it.y }.toInt().coerceAtMost(height)
        return org.opencv.core.Rect(
            minX,
            minY,
            (maxX - minX).coerceAtLeast(1),
            (maxY - minY).coerceAtLeast(1),
        )
    }

    private fun org.opencv.core.Rect.intersectWithin(width: Int, height: Int): org.opencv.core.Rect {
        val x = this.x.coerceIn(0, width - 1)
        val y = this.y.coerceIn(0, height - 1)
        val rectWidth = this.width.coerceIn(1, width - x)
        val rectHeight = this.height.coerceIn(1, height - y)
        return org.opencv.core.Rect(x, y, rectWidth, rectHeight)
    }

    fun maxCornerSkew(corners: List<PointF>): Float {
        if (corners.size != 4) return 0f
        var maxDeviation = 0f
        for (i in corners.indices) {
            val prev = corners[(i + 3) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            val v1x = (prev.x - curr.x).toDouble()
            val v1y = (prev.y - curr.y).toDouble()
            val v2x = (next.x - curr.x).toDouble()
            val v2y = (next.y - curr.y).toDouble()
            val dot = v1x * v2x + v1y * v2y
            val mag1 = hypot(v1x, v1y)
            val mag2 = hypot(v2x, v2y)
            if (mag1 < 1e-6 || mag2 < 1e-6) continue
            val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
            val angleDeg = Math.toDegrees(kotlin.math.acos(cosAngle)).toFloat()
            val deviation = abs(angleDeg - 90f)
            if (deviation > maxDeviation) maxDeviation = deviation
        }
        return maxDeviation
    }
}
