package com.scantoftp.ui.camera

import androidx.compose.ui.geometry.Rect

data class FrameAnalysisResult(
    val receiptDetected: Boolean,
    val stable: Boolean,
    val focusLocked: Boolean,
    val exposureStable: Boolean,
    val deviceStill: Boolean,
    val torchRecommended: Boolean,
    val boundingBox: Rect?,
    val frameWidth: Int,
    val frameHeight: Int,
    val detectionConfidence: Float = 0f,
    val focusScore: Float = 0f,
    val luminance: Float = 0f,
    val boxArea: Float = 0f,
    val centeredness: Float = 0f,
    val stabilityScore: Float = 0f,
    val geometrySkew: Float = 0f, // Max deviation from 90° at any quad corner, in degrees
    val interiorTexture: Float = 0f, // Interior Laplacian variance; high for text-bearing receipts
    val contrast: Float = 0f, // Std-dev of intensity in region; higher = stronger contrast
    val glare: Float = 0f, // Fraction 0..1 of blown-out pixels; higher = more glare
)
