package com.scantoftp.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.scantoftp.BuildConfig
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.ProcessingResult
import com.scantoftp.domain.service.ReceiptProcessor
import com.scantoftp.util.ReceiptFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc

private enum class ReceiptType { THERMAL, COLOR, LONG, STANDARD }

@Singleton
class BitmapReceiptProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ReceiptProcessor {
    override suspend fun process(inputPath: String): ProcessingResult {
        val decoded = BitmapFactory.decodeFile(inputPath)
            ?: error("Unable to decode captured receipt image.")
        val quality = settingsRepository.settingsFlow().first().imageQuality.coerceIn(50, 100)

        val rotated = rotateIfNeeded(decoded, inputPath)
        val quad = OpenCvReceiptToolkit.detectFromBitmap(rotated)
        val flattened = when {
            quad != null && quad.confidence >= 0.65f -> {
                debugLog("Using perspective warp confidence=${quad.confidence}")
                OpenCvReceiptToolkit.warpReceipt(rotated, quad)
            }
            quad != null -> {
                debugLog("Using bounding-box crop confidence=${quad.confidence}")
                cropToBoundingBox(rotated, quad.boundingBox)
            }
            else -> rotated
        }
        val firstPassConfidence = quad?.confidence ?: 0f
        val receiptType = classifyReceipt(flattened)
        debugLog("Receipt classified as $receiptType")

        val portrait = when (receiptType) {
            ReceiptType.LONG -> flattened // Long receipts: don't force portrait
            else -> if (flattened.width > flattened.height) flattened.rotate(90f) else flattened
        }
        val tightened = tightenCropAfterWarp(portrait, firstPassConfidence)
        val trimmed = trimDocumentMargins(tightened, receiptType)
        val enhanced = enhanceReceipt(trimmed, receiptType)
        val outputFile = ReceiptFileStore.createProcessedFile(context)

        FileOutputStream(outputFile).use { out ->
            enhanced.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        return ProcessingResult(
            processedPath = outputFile.absolutePath,
            cropConfidence = quad?.confidence ?: 0.55f,
        )
    }

    private fun releaseAll(vararg mats: Mat?) {
        mats.forEach { mat -> mat?.let { runCatching { it.release() } } }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d("ReceiptMuxScanner", message)
    }

    private fun classifyReceipt(bitmap: Bitmap): ReceiptType {
        val aspectRatio = maxOf(bitmap.width, bitmap.height).toFloat() /
            minOf(bitmap.width, bitmap.height).toFloat()
        val source = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val gray = Mat()
        val channels = ArrayList<Mat>()
        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            val meanSaturation = if (channels.size >= 2) Core.mean(channels[1]).`val`[0] else 0.0
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            val meanLuminance = Core.mean(gray).`val`[0]

            return when {
                aspectRatio > 3.0f -> ReceiptType.LONG
                meanSaturation > 40.0 -> ReceiptType.COLOR
                meanSaturation <= 20.0 && meanLuminance > 160.0 -> ReceiptType.THERMAL
                else -> ReceiptType.STANDARD
            }
        } finally {
            channels.forEach { releaseAll(it) }
            releaseAll(source, rgb, hsv, gray)
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, inputPath: String): Bitmap {
        val exif = ExifInterface(inputPath)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> bitmap.rotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotate(270f)
            else -> bitmap
        }
    }

    private fun cropToBoundingBox(
        bitmap: Bitmap,
        boundingBox: androidx.compose.ui.geometry.Rect,
    ): Bitmap {
        val cropLeft = (boundingBox.left * bitmap.width).toInt().coerceAtLeast(0)
        val cropTop = (boundingBox.top * bitmap.height).toInt().coerceAtLeast(0)
        val cropRight = (boundingBox.right * bitmap.width).toInt().coerceAtMost(bitmap.width)
        val cropBottom = (boundingBox.bottom * bitmap.height).toInt().coerceAtMost(bitmap.height)
        if (cropRight <= cropLeft || cropBottom <= cropTop) return bitmap
        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
    }

    private fun tightenCropAfterWarp(bitmap: Bitmap, firstPassConfidence: Float): Bitmap {
        if (firstPassConfidence >= 0.80f) {
            debugLog("Skipping second-pass: first-pass confidence=${firstPassConfidence}")
            return bitmap
        }
        val refinedDetection = OpenCvReceiptToolkit.detectFromBitmap(bitmap) ?: return bitmap
        val boxArea = refinedDetection.boundingBox.width * refinedDetection.boundingBox.height
        if (refinedDetection.confidence < 0.40f || boxArea < 0.25f || boxArea > 0.93f) {
            debugLog("Skipping second-pass: confidence=${refinedDetection.confidence} area=$boxArea")
            return bitmap
        }

        return when {
            refinedDetection.confidence >= 0.58f || boxArea < 0.86f -> {
                debugLog("Tightening crop with second-pass warp confidence=${refinedDetection.confidence} area=$boxArea")
                OpenCvReceiptToolkit.warpReceipt(bitmap, refinedDetection)
            }
            else -> {
                debugLog("Tightening crop with second-pass bounding box confidence=${refinedDetection.confidence} area=$boxArea")
                cropToBoundingBox(bitmap, refinedDetection.boundingBox)
            }
        }
    }

    private fun enhanceReceipt(bitmap: Bitmap, receiptType: ReceiptType): Bitmap {
        val source = Mat()
        val grayCheck = Mat()
        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, source)

            // Check mean luminance for low-light CLAHE enhancement
            Imgproc.cvtColor(source, grayCheck, Imgproc.COLOR_RGBA2GRAY)
            val meanLum = Core.mean(grayCheck).`val`[0]
            val needsClahe = meanLum < 95.0

            return when (receiptType) {
                ReceiptType.COLOR -> enhanceColor(source, needsClahe)
                ReceiptType.THERMAL -> enhanceThermal(source, needsClahe)
                ReceiptType.LONG -> enhanceThermal(source, needsClahe) // Long receipts are usually thermal
                ReceiptType.STANDARD -> enhanceStandard(source, needsClahe)
            }
        } finally {
            releaseAll(source, grayCheck)
        }
    }

    private fun enhanceColor(source: Mat, applyClahe: Boolean): Bitmap {
        val working = if (applyClahe) applyClaheColor(source) else source
        val shadowRemoved = removeShadowsColor(working)
        val blurred = Mat()
        val sharpened = Mat()
        try {
            debugLog("Color receipt: shadow removal + sharpening${if (applyClahe) " + CLAHE" else ""}")
            Imgproc.GaussianBlur(shadowRemoved, blurred, CvSize(0.0, 0.0), 1.5)
            Core.addWeighted(shadowRemoved, 1.25, blurred, -0.25, 0.0, sharpened)
            val result = Bitmap.createBitmap(sharpened.width(), sharpened.height(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(sharpened, result)
            return result
        } finally {
            if (working !== source) releaseAll(working)
            releaseAll(shadowRemoved, blurred, sharpened)
        }
    }

    private fun enhanceThermal(source: Mat, applyClahe: Boolean): Bitmap {
        val gray = Mat()
        var shadowFree: Mat? = null
        val sharpened = Mat()
        val softened = Mat()
        val normalized = Mat()
        val thresholded = Mat()
        val blended = Mat()
        val rgba = Mat()
        var enhanced: Mat? = null
        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            enhanced = if (applyClahe) {
                debugLog("Thermal receipt: shadow removal + strong threshold + CLAHE")
                applyClaheGray(gray)
            } else {
                debugLog("Thermal receipt: shadow removal + strong threshold")
                gray
            }
            val cleaned = removeShadowsGray(enhanced)
            shadowFree = cleaned
            Imgproc.GaussianBlur(cleaned, softened, CvSize(0.0, 0.0), 2.0)
            Core.addWeighted(cleaned, 1.4, softened, -0.4, 0.0, sharpened)
            Core.normalize(sharpened, normalized, 0.0, 255.0, Core.NORM_MINMAX)
            Imgproc.adaptiveThreshold(
                normalized, thresholded, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                31, 9.0,
            )
            // Stronger blend: more threshold = whiter background, crisper text
            Core.addWeighted(normalized, 0.45, thresholded, 0.55, 0.0, blended)
            Imgproc.cvtColor(blended, rgba, Imgproc.COLOR_GRAY2RGBA)
            val result = Bitmap.createBitmap(rgba.width(), rgba.height(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(rgba, result)
            return result
        } finally {
            if (enhanced != null && enhanced !== gray) releaseAll(enhanced)
            releaseAll(gray, shadowFree, sharpened, softened, normalized, thresholded, blended, rgba)
        }
    }

    private fun enhanceStandard(source: Mat, applyClahe: Boolean): Bitmap {
        val gray = Mat()
        var shadowFree: Mat? = null
        val softened = Mat()
        val sharpened = Mat()
        val normalized = Mat()
        val thresholded = Mat()
        val blended = Mat()
        val rgba = Mat()
        var enhanced: Mat? = null
        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            enhanced = if (applyClahe) applyClaheGray(gray) else gray
            debugLog("Standard receipt: shadow removal + balanced threshold")
            val cleaned = removeShadowsGray(enhanced)
            shadowFree = cleaned
            Imgproc.GaussianBlur(cleaned, softened, CvSize(0.0, 0.0), 2.0)
            Core.addWeighted(cleaned, 1.3, softened, -0.3, 0.0, sharpened)
            Core.normalize(sharpened, normalized, 0.0, 255.0, Core.NORM_MINMAX)
            Imgproc.adaptiveThreshold(
                normalized, thresholded, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                31, 9.0,
            )
            Core.addWeighted(normalized, 0.40, thresholded, 0.60, 0.0, blended)
            Imgproc.cvtColor(blended, rgba, Imgproc.COLOR_GRAY2RGBA)
            val result = Bitmap.createBitmap(rgba.width(), rgba.height(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(rgba, result)
            return result
        } finally {
            if (enhanced != null && enhanced !== gray) releaseAll(enhanced)
            releaseAll(gray, shadowFree, softened, sharpened, normalized, thresholded, blended, rgba)
        }
    }

    /** Estimate background illumination via large morphological closing, then divide to normalize. */
    private fun removeShadowsGray(gray: Mat): Mat {
        val dilated = Mat()
        val bg = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, CvSize(27.0, 27.0))
        try {
            Imgproc.morphologyEx(gray, dilated, Imgproc.MORPH_CLOSE, kernel)
            // Smooth the background estimate
            Imgproc.medianBlur(dilated, bg, 21)
            // Divide: pixel / background * 255 — normalizes uneven lighting
            val result = Mat()
            Core.divide(gray, bg, result, 255.0)
            return result
        } finally {
            releaseAll(dilated, bg, kernel)
        }
    }

    /** Shadow removal for color images: process each channel independently. */
    private fun removeShadowsColor(source: Mat): Mat {
        val channels = ArrayList<Mat>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, CvSize(27.0, 27.0))
        try {
            Core.split(source, channels)
            for (i in 0 until minOf(channels.size, 3)) {
                val dilated = Mat()
                val bg = Mat()
                try {
                    Imgproc.morphologyEx(channels[i], dilated, Imgproc.MORPH_CLOSE, kernel)
                    Imgproc.medianBlur(dilated, bg, 21)
                    Core.divide(channels[i], bg, channels[i], 255.0)
                } finally {
                    releaseAll(dilated, bg)
                }
            }
            val result = Mat()
            Core.merge(channels, result)
            return result
        } finally {
            channels.forEach { releaseAll(it) }
            releaseAll(kernel)
        }
    }

    private fun applyClaheGray(gray: Mat): Mat {
        val clahe = Imgproc.createCLAHE(3.0, CvSize(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)
        return enhanced
    }

    private fun applyClaheColor(source: Mat): Mat {
        val rgb = Mat()
        val lab = Mat()
        val channels = ArrayList<Mat>()
        val merged = Mat()
        val enhancedRgb = Mat()
        val enhancedRgba = Mat()
        try {
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            val clahe = Imgproc.createCLAHE(2.5, CvSize(8.0, 8.0))
            clahe.apply(channels[0], channels[0]) // Apply CLAHE to L channel
            Core.merge(channels, merged)
            Imgproc.cvtColor(merged, enhancedRgb, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(enhancedRgb, enhancedRgba, Imgproc.COLOR_RGB2RGBA)
            return enhancedRgba
        } finally {
            channels.forEach { releaseAll(it) }
            releaseAll(rgb, lab, merged, enhancedRgb)
        }
    }

    private fun trimDocumentMargins(bitmap: Bitmap, receiptType: ReceiptType = ReceiptType.STANDARD): Bitmap {
        val source = Mat()
        val gray = Mat()
        val normalized = Mat()
        val binary = Mat()
        val adaptive = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(17.0, 17.0))
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(7.0, 7.0))
        val hierarchy = Mat()
        val contours = ArrayList<org.opencv.core.MatOfPoint>()
        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Core.normalize(gray, normalized, 0.0, 255.0, Core.NORM_MINMAX)
            Imgproc.threshold(
                normalized,
                binary,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
            )
            Imgproc.adaptiveThreshold(
                normalized,
                adaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                35,
                6.0,
            )
            Core.max(binary, adaptive, binary)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.findContours(
                binary,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )
            val imageArea = bitmap.width.toDouble() * bitmap.height.toDouble()
            val imageCenterX = bitmap.width / 2.0
            val imageCenterY = bitmap.height / 2.0
            val bestContour = contours.maxByOrNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < imageArea * 0.30) {
                    Double.NEGATIVE_INFINITY
                } else {
                    val rect = Imgproc.boundingRect(contour)
                    val rectArea = rect.width.toDouble() * rect.height.toDouble()
                    if (rectArea <= 0.0) {
                        Double.NEGATIVE_INFINITY
                    } else {
                        val aspectRatio = maxOf(rect.width.toDouble(), rect.height.toDouble()) /
                            minOf(rect.width.toDouble(), rect.height.toDouble())
                        val fillRatio = (area / rectArea).coerceIn(0.0, 1.0)
                        val areaRatio = (rectArea / imageArea).coerceIn(0.0, 1.0)
                        val centerX = rect.x + rect.width / 2.0
                        val centerY = rect.y + rect.height / 2.0
                        val centeredness = 1.0 - (
                            (kotlin.math.abs(centerX - imageCenterX) / imageCenterX) +
                                (kotlin.math.abs(centerY - imageCenterY) / imageCenterY)
                            ) / 2.0
                        if (aspectRatio !in (if (receiptType == ReceiptType.LONG) 1.1..20.0 else 1.3..14.0) || centeredness <= 0.0) {
                            Double.NEGATIVE_INFINITY
                        } else {
                            areaRatio * 0.52 + fillRatio * 0.30 + centeredness.coerceIn(0.0, 1.0) * 0.18
                        }
                    }
                }
            } ?: return bitmap
            val rect = Imgproc.boundingRect(bestContour)
            if (rect.width <= 0 || rect.height <= 0) return bitmap
            val rectArea = rect.width * rect.height
            if (rectArea < bitmap.width * bitmap.height * 0.30f) return bitmap

            val insetX = (rect.width * 0.025f).toInt()
            val insetY = (rect.height * 0.025f).toInt()
            val left = (rect.x + insetX).coerceIn(0, bitmap.width - 1)
            val top = (rect.y + insetY).coerceIn(0, bitmap.height - 1)
            val right = (rect.x + rect.width - insetX).coerceIn(left + 1, bitmap.width)
            val bottom = (rect.y + rect.height - insetY).coerceIn(top + 1, bitmap.height)
            val croppedArea = (right - left).toLong() * (bottom - top).toLong()
            val originalArea = bitmap.width.toLong() * bitmap.height.toLong()
            if (croppedArea < originalArea * 0.82) {
                debugLog("Margin trim would remove too much (${(croppedArea * 100 / originalArea)}%), skipping")
                return bitmap
            }
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } finally {
            contours.forEach { releaseAll(it) }
            releaseAll(source, gray, normalized, binary, adaptive, closeKernel, openKernel, hierarchy)
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
