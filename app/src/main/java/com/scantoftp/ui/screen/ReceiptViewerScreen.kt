package com.scantoftp.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.ui.viewmodel.ReceiptViewerViewModel
import com.scantoftp.util.ReceiptBitmapLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptViewerScreen(
    onBack: () -> Unit,
    viewModel: ReceiptViewerViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val receipt = state.receipt
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            !state.loaded -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            receipt == null -> {
                Text(
                    stringResource(R.string.viewer_unavailable),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            else -> {
                ZoomableImage(
                    path = receipt.processedPath.ifBlank { receipt.localPath },
                    modifier = Modifier.fillMaxSize(),
                )

                BottomInfo(
                    fileName = receipt.fileName,
                    metadata = listOf(receipt.vendor, receipt.amount)
                        .filter { it.isNotBlank() && it != "UnknownVendor" }
                        .joinToString(" · "),
                    timestamp = formatViewerTimestamp(receipt.createdAt),
                    status = receipt.status,
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        TopBar(
            title = receipt?.fileName.orEmpty(),
            canDelete = receipt != null,
            onBack = onBack,
            onDelete = { confirmDelete = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.queue_delete_title)) },
            text = { Text(stringResource(R.string.queue_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted = onBack)
                    },
                ) { Text(stringResource(R.string.queue_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.queue_cancel))
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        ReceiptBitmapLoader.decodeSampledBitmap(
            path = path,
            requestedWidth = 1440,
            requestedHeight = 1920,
        )
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    },
            )
        } else {
            Text(
                stringResource(R.string.viewer_image_missing),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim(topDown = true))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
        }
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.queue_delete), tint = Color.White)
            }
        }
    }
}

@Composable
private fun BottomInfo(
    fileName: String,
    metadata: String,
    timestamp: String,
    status: UploadStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim(topDown = false))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    timestamp,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ViewerStatusPill(status = status)
        }
        if (status == UploadStatus.Failed) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Text(
                    stringResource(R.string.queue_retry),
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewerStatusPill(status: UploadStatus) {
    val (dot, label) = when (status) {
        UploadStatus.Completed -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.queue_status_completed)
        UploadStatus.Failed -> MaterialTheme.colorScheme.error to stringResource(R.string.queue_status_failed)
        UploadStatus.Uploading -> MaterialTheme.colorScheme.primary to stringResource(R.string.queue_status_uploading)
        UploadStatus.Pending -> Color.White.copy(alpha = 0.7f) to stringResource(R.string.queue_status_pending)
    }
    Surface(shape = RoundedCornerShape(percent = 50), color = Color.White.copy(alpha = 0.14f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(modifier = Modifier.size(7.dp).background(dot, CircleShape))
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

private fun scrim(topDown: Boolean): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = if (topDown) {
            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
        } else {
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
        },
    )

private fun formatViewerTimestamp(timestamp: Long): String =
    SimpleDateFormat("MMM dd, yyyy · h:mm a", Locale.US).format(Date(timestamp))
