package com.scantoftp.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.exifinterface.media.ExifInterface
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.util.ReceiptFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class ManualCropDraft(
    val imagePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val corners: List<Offset>,
)

class ManualCropService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun createDraft(inputPath: String): ManualCropDraft {
        val bitmap = decodeRotated(inputPath)
        val detection = OpenCvReceiptToolkit.detectFromBitmap(bitmap)
        val corners = detection?.orderedCorners?.map { point ->
            Offset(
                x = (point.x / bitmap.width).coerceIn(0f, 1f),
                y = (point.y / bitmap.height).coerceIn(0f, 1f),
            )
        } ?: defaultCorners()

        return ManualCropDraft(
            imagePath = inputPath,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            corners = corners,
        )
    }

    suspend fun applyCrop(inputPath: String, normalizedCorners: List<Offset>): String {
        require(normalizedCorners.size == 4) { "Four crop corners are required." }
        val bitmap = decodeRotated(inputPath)
        val pixelCorners = normalizedCorners.map { corner ->
            PointF(
                corner.x.coerceIn(0f, 1f) * bitmap.width,
                corner.y.coerceIn(0f, 1f) * bitmap.height,
            )
        }
        val warped = OpenCvReceiptToolkit.warpReceipt(
            bitmap = bitmap,
            detection = OpenCvReceiptDetection(
                orderedCorners = pixelCorners,
                boundingBox = boundingBoxFor(pixelCorners, bitmap.width, bitmap.height),
                confidence = 1f,
                focusScore = 0.0,
                meanLuminance = 0.0,
            ),
        )
        val outputFile = ReceiptFileStore.createProcessedFile(context)
        val quality = settingsRepository.settingsFlow().first().imageQuality.coerceIn(50, 100)
        FileOutputStream(outputFile).use { out ->
            warped.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return outputFile.absolutePath
    }

    private fun decodeRotated(path: String): Bitmap {
        val decoded = BitmapFactory.decodeFile(path) ?: error("Unable to decode receipt image.")
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return decoded
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private fun defaultCorners(): List<Offset> = listOf(
        Offset(0.08f, 0.08f),
        Offset(0.92f, 0.08f),
        Offset(0.92f, 0.92f),
        Offset(0.08f, 0.92f),
    )

    private fun boundingBoxFor(corners: List<PointF>, width: Int, height: Int): Rect {
        val minX = corners.minOf { it.x }.coerceIn(0f, width.toFloat()) / width
        val minY = corners.minOf { it.y }.coerceIn(0f, height.toFloat()) / height
        val maxX = corners.maxOf { it.x }.coerceIn(0f, width.toFloat()) / width
        val maxY = corners.maxOf { it.y }.coerceIn(0f, height.toFloat()) / height
        return Rect(minX, minY, maxX, maxY)
    }
}
