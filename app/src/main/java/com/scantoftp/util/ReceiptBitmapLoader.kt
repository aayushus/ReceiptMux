package com.scantoftp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

object ReceiptBitmapLoader {
    fun decodeSampledBitmap(
        path: String,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, requestedWidth, requestedHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        var inSampleSize = 1
        val sourceHeight = options.outHeight
        val sourceWidth = options.outWidth
        if (sourceHeight <= requestedHeight && sourceWidth <= requestedWidth) return inSampleSize

        var halfHeight = sourceHeight / 2
        var halfWidth = sourceWidth / 2
        while (halfHeight / inSampleSize >= requestedHeight && halfWidth / inSampleSize >= requestedWidth) {
            inSampleSize *= 2
        }
        return max(inSampleSize, 1)
    }
}
