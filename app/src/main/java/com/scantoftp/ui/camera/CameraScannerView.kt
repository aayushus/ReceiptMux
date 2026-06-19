package com.scantoftp.ui.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.scantoftp.BuildConfig
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.util.ReceiptFileStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(
    modifier: Modifier = Modifier,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    flashMode: FlashMode,
    captureRequestToken: Long,
    onFrameResult: (FrameAnalysisResult) -> Unit,
    onTorchChanged: (Boolean) -> Unit,
    onImageCaptured: (String) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val analyzerExecutor = rememberCameraExecutor()
    val latestOnFrameResult = rememberUpdatedState(onFrameResult)
    val latestOnTorchChanged = rememberUpdatedState(onTorchChanged)
    val latestOnImageCaptured = rememberUpdatedState(onImageCaptured)
    val latestOnCaptureError = rememberUpdatedState(onCaptureError)
    val latestFlashMode = rememberUpdatedState(flashMode)
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val focusConfirmed = remember { AtomicBoolean(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var desiredTorch = false
        var lastMeteringAtMillis = 0L
        var lastDetectedAtMillis = 0L
        var lastMeteringBox: Rect? = null
        var lastTorchChangeAtMillis = 0L

        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(
                analyzerExecutor,
                ReceiptFrameAnalyzer { result ->
                    val activeCamera = camera
                    if (
                        result.receiptDetected &&
                        !result.focusLocked &&
                        result.detectionConfidence >= 0.40f &&
                        result.boxArea >= 0.08f &&
                        result.boundingBox != null &&
                        result.frameWidth > 0 &&
                        result.frameHeight > 0 &&
                        previewView.width > 0 &&
                        previewView.height > 0 &&
                        activeCamera != null
                    ) {
                        val now = System.currentTimeMillis()
                        lastDetectedAtMillis = now
                        if (
                            shouldTriggerFocusMetering(
                                currentBox = result.boundingBox,
                                previousBox = lastMeteringBox,
                                focusConfirmed = focusConfirmed.get(),
                                currentTimeMillis = now,
                                lastMeteringTimeMillis = lastMeteringAtMillis,
                                stabilityScore = result.stabilityScore,
                            )
                        ) {
                            lastMeteringAtMillis = now
                            lastMeteringBox = result.boundingBox
                            focusConfirmed.set(false)
                            val mappedRect = mapFrameRectToView(
                                rect = result.boundingBox,
                                frameWidth = result.frameWidth,
                                frameHeight = result.frameHeight,
                                viewWidth = previewView.width.toFloat(),
                                viewHeight = previewView.height.toFloat(),
                            )
                            val centerX = (mappedRect.left + mappedRect.right) / 2f
                            val centerY = (mappedRect.top + mappedRect.bottom) / 2f
                            mainExecutor.execute {
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "ReceiptMuxScanner",
                                        "Focus metering requested center=(${centerX.toInt()}, ${centerY.toInt()}) frame=${result.frameWidth}x${result.frameHeight}",
                                    )
                                }
                                val point = previewView.meteringPointFactory.createPoint(centerX, centerY, 0.30f)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or
                                        FocusMeteringAction.FLAG_AE or
                                        FocusMeteringAction.FLAG_AWB,
                                )
                                    .setAutoCancelDuration(1500, TimeUnit.MILLISECONDS)
                                    .build()
                                val future = activeCamera.cameraControl.startFocusAndMetering(action)
                                future.addListener(
                                    {
                                        val success = runCatching { future.get().isFocusSuccessful() }.getOrDefault(false)
                                        focusConfirmed.set(success)
                                        if (BuildConfig.DEBUG) Log.d("ReceiptMuxScanner", "Focus metering result success=$success")
                                    },
                                    mainExecutor,
                                )
                            }
                        }
                    } else if (lastMeteringBox != null && System.currentTimeMillis() - lastDetectedAtMillis > 1600L) {
                        lastMeteringBox = null
                        focusConfirmed.set(false)
                        activeCamera?.cameraControl?.cancelFocusAndMetering()
                    }

                    val effectiveResult = result.copy(focusLocked = result.focusLocked || focusConfirmed.get())
                    latestOnFrameResult.value(effectiveResult)

                    // Torch hysteresis: ON below 80 luminance, OFF above 120, with 3s hold minimum
                    val now2 = System.currentTimeMillis()
                    val shouldUseTorch = when (latestFlashMode.value) {
                        FlashMode.AlwaysOn -> true
                        FlashMode.AlwaysOff -> false
                        FlashMode.Auto -> {
                            if (now2 - lastTorchChangeAtMillis < 3_000L) {
                                desiredTorch // Hold current state during cooldown
                            } else if (desiredTorch) {
                                effectiveResult.luminance < 120f // Stay on until well-lit
                            } else {
                                effectiveResult.luminance < 80f // Only turn on when truly dark
                            }
                        }
                    }
                    if (desiredTorch != shouldUseTorch) {
                        desiredTorch = shouldUseTorch
                        lastTorchChangeAtMillis = now2
                        activeCamera?.cameraControl?.enableTorch(shouldUseTorch)
                        latestOnTorchChanged.value(shouldUseTorch)
                    }
                },
            )

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                analysis,
            )
            val torchEnabled = when (latestFlashMode.value) {
                FlashMode.AlwaysOn -> true
                FlashMode.AlwaysOff -> false
                FlashMode.Auto -> false
            }
            desiredTorch = torchEnabled
            camera?.cameraControl?.enableTorch(torchEnabled)
            latestOnTorchChanged.value(torchEnabled)
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    LaunchedEffect(flashMode, camera) {
        val forcedTorch = when (flashMode) {
            FlashMode.AlwaysOn -> true
            FlashMode.AlwaysOff -> false
            FlashMode.Auto -> null
        }
        if (forcedTorch != null) {
            camera?.cameraControl?.enableTorch(forcedTorch)
            latestOnTorchChanged.value(forcedTorch)
        }
    }

    LaunchedEffect(captureRequestToken) {
        if (captureRequestToken <= 0) return@LaunchedEffect
        val outputFile = ReceiptFileStore.createRawCaptureFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    latestOnImageCaptured.value(outputFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    latestOnCaptureError.value(exception.message ?: "Unable to capture receipt image.")
                }
            },
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}

private fun shouldTriggerFocusMetering(
    currentBox: Rect,
    previousBox: Rect?,
    focusConfirmed: Boolean,
    currentTimeMillis: Long,
    lastMeteringTimeMillis: Long,
    stabilityScore: Float,
): Boolean {
    if (currentTimeMillis - lastMeteringTimeMillis < 1400L) return false
    if (previousBox == null) return true

    val currentCenterX = (currentBox.left + currentBox.right) / 2f
    val currentCenterY = (currentBox.top + currentBox.bottom) / 2f
    val previousCenterX = (previousBox.left + previousBox.right) / 2f
    val previousCenterY = (previousBox.top + previousBox.bottom) / 2f
    val centerShift = kotlin.math.abs(currentCenterX - previousCenterX) + kotlin.math.abs(currentCenterY - previousCenterY)
    val previousArea = previousBox.width * previousBox.height
    val currentArea = currentBox.width * currentBox.height
    val areaShift = kotlin.math.abs(currentArea - previousArea)
    return centerShift > 0.08f ||
        areaShift > 0.06f ||
        (!focusConfirmed && stabilityScore >= 0.55f && currentTimeMillis - lastMeteringTimeMillis > 1800L)
}

@Composable
private fun rememberCameraExecutor(): ExecutorService = remember { Executors.newSingleThreadExecutor() }
