package com.scantoftp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.ReceiptDetectionState
import com.scantoftp.ui.camera.CameraScannerView
import com.scantoftp.ui.camera.mapFrameRectToView
import com.scantoftp.ui.sensor.AccelerometerStillnessMonitor
import com.scantoftp.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay

private val QualityGood = Color(0xFF34D399)
private val QualityWarn = Color(0xFFFBBF24)
private val QualityPoor = Color(0xFFF87171)
private val CaptureBlue = Color(0xFF8FB8FF)
private val CaptureIcon = Color(0xFF0B1B3A)

@Composable
fun ScannerScreen(
    onPreviewRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onClose: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.detectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val setupReady by viewModel.setupReady.collectAsStateWithLifecycle()
    val captureRequestToken by viewModel.captureRequestToken.collectAsStateWithLifecycle()
    val shutterCueToken by viewModel.shutterCueToken.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingCapture.collectAsStateWithLifecycle()
    var showShutterFlash by remember { mutableStateOf(false) }
    var audioEnabled by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "overlayAlpha",
    )
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayColor = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission.value = granted
    }

    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) {
        onDispose { shutterSound.release() }
    }

    LaunchedEffect(shutterCueToken) {
        if (shutterCueToken == 0L) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (audioEnabled) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        showShutterFlash = true
        delay(120)
        showShutterFlash = false
    }

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(4500)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission.value) {
            AccelerometerStillnessMonitor(
                context = context,
                onStillnessChanged = viewModel::onMotionStillnessChanged,
            )
            // Keep the camera bound while a capture is processing. Tearing the
            // preview down at the same instant we request a capture cancels the
            // in-flight takePicture() and leaves the UI stuck on the spinner.
            CameraScannerView(
                modifier = Modifier.fillMaxSize(),
                context = context,
                lifecycleOwner = lifecycleOwner,
                flashMode = settings.flashMode,
                captureRequestToken = captureRequestToken,
                onFrameResult = viewModel::onFrameAnalyzed,
                onTorchChanged = viewModel::onTorchChanged,
                onImageCaptured = { path ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.onImageCaptured(path) { onPreviewRequested() }
                },
                onCaptureError = {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    viewModel.onCaptureFailed()
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scanner_permission_required), color = Color.White)
                    OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scanner_grant_camera_access))
                    }
                }
            }
        }

        DetectionOverlay(state = state, overlayColor = overlayColor)

        if (hasCameraPermission.value) {
            // Top + bottom scrims keep the chrome legible over a bright preview.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
            )
        }

        // Top cluster: header + floating quality panel.
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            CaptureHeader(
                flashMode = settings.flashMode,
                audioEnabled = audioEnabled,
                onClose = onClose,
                onToggleFlash = viewModel::cycleFlashMode,
                onToggleAudio = { audioEnabled = !audioEnabled },
            )
            if (hasCameraPermission.value && !isProcessing) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                    QualityPanel(state = state)
                }
            }
        }

        // Bottom cluster: setup banner, status pill, capture control.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!setupReady) {
                SetupBanner(onSettingsRequested = onSettingsRequested)
            }

            StatusBanner(state = state, isProcessing = isProcessing, onClearError = viewModel::clearError)

            CaptureButton(
                progress = state.progress,
                ready = state.readyToCapture && !isProcessing,
                enabled = !isProcessing,
                onCapture = {
                    if (isProcessing) return@CaptureButton
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.requestManualCapture()
                },
            )
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 4.dp,
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.scanner_processing),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
        if (showShutterFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .alpha(0.35f),
            )
        }
    }
}

@Composable
private fun CaptureHeader(
    flashMode: FlashMode,
    audioEnabled: Boolean,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleAudio: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.scanner_header_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.scanner_close),
                onClick = onClose,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (flashIcon, flashActive) = when (flashMode) {
                    FlashMode.Auto -> Icons.Filled.FlashAuto to false
                    FlashMode.AlwaysOn -> Icons.Filled.FlashOn to true
                    FlashMode.AlwaysOff -> Icons.Filled.FlashOff to false
                }
                CircleIconButton(
                    icon = flashIcon,
                    contentDescription = stringResource(R.string.scanner_flash),
                    onClick = onToggleFlash,
                    active = flashActive,
                )
                CircleIconButton(
                    icon = if (audioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(R.string.scanner_audio_feedback),
                    onClick = onToggleAudio,
                    active = audioEnabled,
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) CaptureBlue.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) CaptureIcon else Color.White,
            )
        }
    }
}

@Composable
private fun SetupBanner(onSettingsRequested: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.scanner_setup_incomplete_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.scanner_setup_incomplete_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = onSettingsRequested) {
                Text(stringResource(R.string.scanner_open_settings))
            }
        }
    }
}

@Composable
private fun StatusBanner(
    state: ReceiptDetectionState,
    isProcessing: Boolean,
    onClearError: () -> Unit,
) {
    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
            modifier = Modifier.clickable(onClick = onClearError),
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        return
    }

    val ready = state.readyToCapture && !isProcessing
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (ready) QualityGood.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = statusText(state, isProcessing),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun statusText(state: ReceiptDetectionState, isProcessing: Boolean): String = when {
    isProcessing -> stringResource(R.string.scanner_status_processing)
    state.progress >= 1f -> stringResource(R.string.scanner_status_captured)
    !state.detected -> stringResource(R.string.scanner_status_searching)
    state.boxArea in 0.01f..0.18f -> stringResource(R.string.scanner_status_move_closer)
    state.centeredness in 0.01f..0.58f -> stringResource(R.string.scanner_status_center_receipt)
    state.geometryWarning -> stringResource(R.string.scanner_status_adjust_angle)
    state.progress >= 0.7f -> stringResource(R.string.scanner_status_locking_receipt)
    !state.focusLocked -> stringResource(R.string.scanner_status_focusing)
    !state.exposureStable || state.luminance in 0.01f..85f -> stringResource(R.string.scanner_status_adjust_lighting)
    !state.deviceStill -> stringResource(R.string.scanner_status_hold_steady)
    state.readyToCapture -> stringResource(R.string.scanner_status_ready)
    else -> stringResource(R.string.scanner_status_searching)
}

@Composable
private fun CaptureButton(
    progress: Float,
    ready: Boolean,
    enabled: Boolean,
    onCapture: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        val glowTransition = rememberInfiniteTransition(label = "shutterGlow")
        val glowScale by glowTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowScale",
        )
        val glowAlpha by glowTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
            label = "glowAlpha",
        )
        if (ready) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(glowScale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(CaptureBlue),
            )
        }
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(96.dp),
            strokeWidth = 5.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.28f),
            strokeCap = StrokeCap.Round,
        )
        Surface(
            onClick = onCapture,
            enabled = enabled,
            shape = CircleShape,
            color = CaptureBlue,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.scanner_capture),
                    tint = CaptureIcon,
                )
            }
        }
    }
}

@Composable
private fun DetectionOverlay(state: ReceiptDetectionState, overlayColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val rect = state.boundingBox ?: return@Canvas
        val previewRect = mapFrameRectToView(
            rect = rect,
            frameWidth = state.frameWidth,
            frameHeight = state.frameHeight,
            viewWidth = size.width,
            viewHeight = size.height,
        )
        drawRoundRect(
            color = overlayColor,
            topLeft = Offset(previewRect.left, previewRect.top),
            size = previewRect.size,
            style = Stroke(width = 8f),
            cornerRadius = CornerRadius(28f, 28f),
        )

        val cornerLength = minOf(previewRect.width, previewRect.height) * 0.12f
        val strokeWidth = 10f
        val corners = listOf(
            Offset(previewRect.left, previewRect.top) to listOf(
                Offset(previewRect.left + cornerLength, previewRect.top),
                Offset(previewRect.left, previewRect.top + cornerLength),
            ),
            Offset(previewRect.right, previewRect.top) to listOf(
                Offset(previewRect.right - cornerLength, previewRect.top),
                Offset(previewRect.right, previewRect.top + cornerLength),
            ),
            Offset(previewRect.right, previewRect.bottom) to listOf(
                Offset(previewRect.right - cornerLength, previewRect.bottom),
                Offset(previewRect.right, previewRect.bottom - cornerLength),
            ),
            Offset(previewRect.left, previewRect.bottom) to listOf(
                Offset(previewRect.left + cornerLength, previewRect.bottom),
                Offset(previewRect.left, previewRect.bottom - cornerLength),
            ),
        )

        corners.forEach { (origin, targets) ->
            targets.forEach { target ->
                drawLine(
                    color = overlayColor,
                    start = origin,
                    end = target,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Floating translucent quality card (upper-left): live focus, glare and contrast
 * meters, color-coded green / yellow / red so the user can correct before capture.
 */
@Composable
private fun QualityPanel(state: ReceiptDetectionState) {
    val sharpness = (state.sharpness / 120f).coerceIn(0f, 1f)
    val contrast = (state.contrast / 60f).coerceIn(0f, 1f)
    val noGlare = (1f - state.glare / 0.06f).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QualityMeter(stringResource(R.string.scanner_quality_sharpness), sharpness)
            QualityMeter(stringResource(R.string.scanner_quality_glare), noGlare)
            QualityMeter(stringResource(R.string.scanner_quality_contrast), contrast)
        }
    }
}

@Composable
private fun QualityMeter(label: String, value: Float) {
    val animated by animateFloatAsState(targetValue = value.coerceIn(0f, 1f), label = "metric")
    val (barColor, statusRes) = when {
        value >= 0.66f -> QualityGood to R.string.scanner_quality_good
        value >= 0.40f -> QualityWarn to R.string.scanner_quality_medium
        else -> QualityPoor to R.string.scanner_quality_low
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(statusRes),
                color = barColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
    }
}
