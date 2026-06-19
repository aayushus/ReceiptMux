package com.scantoftp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.ui.components.PrimaryButton
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SecondaryButton
import com.scantoftp.ui.viewmodel.CropAdjustViewModel
import com.scantoftp.util.ReceiptBitmapLoader
import kotlin.math.abs
import kotlin.math.min

@Composable
fun CropAdjustScreen(
    onBack: () -> Unit,
    viewModel: CropAdjustViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.preview_back))
            }
            ScreenHeader(
                title = stringResource(R.string.crop_adjust_title),
                subtitle = stringResource(R.string.crop_adjust_subtitle),
                modifier = Modifier.weight(1f).padding(horizontal = 0.dp),
            )
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.crop_adjust_loading))
                    }
                }
            }

            state.errorMessage != null -> {
                ErrorSurface(message = state.errorMessage.orEmpty())
            }

            else -> {
                CropEditor(
                    imagePath = state.imagePath,
                    corners = state.corners,
                    onCornerChanged = viewModel::updateCorner,
                )
            }
        }

        state.errorMessage?.let { ErrorSurface(message = it) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.crop_adjust_reset),
                onClick = viewModel::reset,
                modifier = Modifier.weight(1f),
                enabled = !state.isApplying && !state.isLoading,
            )
            PrimaryButton(
                text = if (state.isApplying) {
                    stringResource(R.string.crop_adjust_applying)
                } else {
                    stringResource(R.string.crop_adjust_apply)
                },
                onClick = { viewModel.apply(onBack) },
                modifier = Modifier.weight(1f),
                enabled = !state.isApplying && !state.isLoading && state.corners.size == 4,
            )
        }
    }
}

@Composable
private fun CropEditor(
    imagePath: String,
    corners: List<Offset>,
    onCornerChanged: (Int, Offset) -> Unit,
) {
    val bitmap = remember(imagePath) {
        ReceiptBitmapLoader.decodeSampledBitmap(
            path = imagePath,
            requestedWidth = 1440,
            requestedHeight = 2200,
        )
    }
    val handleRadius = with(LocalDensity.current) { 18.dp.toPx() }
    var activeCorner by remember { mutableStateOf<Int?>(null) }
    val cornerDescription = stringResource(R.string.crop_adjust_corner, (activeCorner ?: 0) + 1)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 460.dp, max = 620.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.preview_receipt),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = cornerDescription }
                    .pointerInput(corners) {
                        fun imageRect(): Rect = fittedImageRect(
                            canvasWidth = size.width.toFloat(),
                            canvasHeight = size.height.toFloat(),
                            imageWidth = bitmap?.width ?: 1,
                            imageHeight = bitmap?.height ?: 1,
                        )

                        fun pointFor(corner: Offset): Offset {
                            val rect = imageRect()
                            return Offset(
                                x = rect.left + corner.x * rect.width,
                                y = rect.top + corner.y * rect.height,
                            )
                        }

                        detectDragGestures(
                            onDragStart = { start ->
                                activeCorner = corners
                                    .mapIndexed { index, corner -> index to distance(start, pointFor(corner)) }
                                    .minByOrNull { it.second }
                                    ?.takeIf { it.second <= handleRadius * 2.4f }
                                    ?.first
                            },
                            onDragEnd = { activeCorner = null },
                            onDragCancel = { activeCorner = null },
                            onDrag = { change, _ ->
                                val index = activeCorner ?: return@detectDragGestures
                                val rect = imageRect()
                                val normalized = Offset(
                                    x = ((change.position.x - rect.left) / rect.width).coerceIn(0f, 1f),
                                    y = ((change.position.y - rect.top) / rect.height).coerceIn(0f, 1f),
                                )
                                onCornerChanged(index, normalized)
                            },
                        )
                    },
            ) {
                if (corners.size == 4) {
                    val imageRect = fittedImageRect(
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        imageWidth = bitmap?.width ?: 1,
                        imageHeight = bitmap?.height ?: 1,
                    )
                    val points = corners.map { corner ->
                        Offset(
                            x = imageRect.left + corner.x * imageRect.width,
                            y = imageRect.top + corner.y * imageRect.height,
                        )
                    }
                    drawPath(
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points[0].x, points[0].y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        },
                        color = Color.White.copy(alpha = 0.18f),
                    )
                    points.indices.forEach { index ->
                        val next = points[(index + 1) % points.size]
                        drawLine(
                            color = Color.White,
                            start = points[index],
                            end = next,
                            strokeWidth = 5f,
                            cap = StrokeCap.Round,
                        )
                    }
                    points.forEach { point ->
                        drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = handleRadius + 5f, center = point)
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = point,
                            style = Stroke(width = 5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorSurface(message: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun fittedImageRect(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): Rect {
    val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val fittedWidth = imageWidth * scale
    val fittedHeight = imageHeight * scale
    val left = (canvasWidth - fittedWidth) / 2f
    val top = (canvasHeight - fittedHeight) / 2f
    return Rect(left, top, left + fittedWidth, top + fittedHeight)
}

private fun distance(first: Offset, second: Offset): Float =
    abs(first.x - second.x) + abs(first.y - second.y)
