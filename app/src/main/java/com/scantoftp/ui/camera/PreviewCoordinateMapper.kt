package com.scantoftp.ui.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

fun rotateNormalizedRect(
    rect: Rect,
    rotationDegrees: Int,
): Rect {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    if (normalizedRotation == 0) return rect

    val rotatedPoints = listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
        Offset(rect.left, rect.bottom),
    ).map { point ->
        when (normalizedRotation) {
            90 -> Offset(1f - point.y, point.x)
            180 -> Offset(1f - point.x, 1f - point.y)
            270 -> Offset(point.y, 1f - point.x)
            else -> point
        }
    }

    val minX = rotatedPoints.minOf { it.x }.coerceIn(0f, 1f)
    val minY = rotatedPoints.minOf { it.y }.coerceIn(0f, 1f)
    val maxX = rotatedPoints.maxOf { it.x }.coerceIn(0f, 1f)
    val maxY = rotatedPoints.maxOf { it.y }.coerceIn(0f, 1f)
    return Rect(minX, minY, maxX, maxY)
}

fun mapFrameRectToView(
    rect: Rect,
    frameWidth: Int,
    frameHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
): Rect {
    if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f ||
        viewWidth.isNaN() || viewHeight.isNaN() || viewWidth.isInfinite() || viewHeight.isInfinite()
    ) {
        return Rect.Zero
    }

    val scale = max(viewWidth / frameWidth.toFloat(), viewHeight / frameHeight.toFloat())
    val scaledWidth = frameWidth * scale
    val scaledHeight = frameHeight * scale
    val offsetX = (viewWidth - scaledWidth) / 2f
    val offsetY = (viewHeight - scaledHeight) / 2f

    return Rect(
        left = offsetX + (rect.left * frameWidth * scale),
        top = offsetY + (rect.top * frameHeight * scale),
        right = offsetX + (rect.right * frameWidth * scale),
        bottom = offsetY + (rect.bottom * frameHeight * scale),
    )
}
