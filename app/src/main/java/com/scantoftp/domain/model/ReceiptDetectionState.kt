package com.scantoftp.domain.model

import androidx.compose.ui.geometry.Rect

data class ReceiptDetectionState(
    val detected: Boolean = false,
    val stable: Boolean = false,
    val focusLocked: Boolean = false,
    val exposureStable: Boolean = false,
    val deviceStill: Boolean = false,
    val torchEnabled: Boolean = false,
    val progress: Float = 0f,
    val boundingBox: Rect? = null, // Normalized 0..1 coordinates in the rotated analysis frame.
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val geometrySkew: Float = 0f,
    val luminance: Float = 0f,
    val boxArea: Float = 0f,
    val centeredness: Float = 0f,
    val confidence: Float = 0f, // Receipt-identification confidence, 0..1
    val sharpness: Float = 0f, // Laplacian focus score (raw); higher = sharper
    val contrast: Float = 0f, // Std-dev of intensity in region; higher = stronger contrast
    val glare: Float = 0f, // Fraction 0..1 of blown-out pixels; higher = more glare
    val errorMessage: String? = null,
) {
    val readyToCapture: Boolean
        get() = detected && focusLocked && exposureStable && deviceStill
    val geometryWarning: Boolean
        get() = detected && geometrySkew > 15f
}
