package com.scantoftp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.ui.components.GradientButton
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.theme.BrandGradients
import com.scantoftp.ui.viewmodel.QueueViewModel
import com.scantoftp.util.ReceiptBitmapLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class QueueFilter { All, Active, Failed, Done }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onOpenReceipt: (Long) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val receipts = viewModel.receipts.collectAsStateWithLifecycle().value
    val pendingCount = viewModel.pendingCount.collectAsStateWithLifecycle().value
    val failedCount = viewModel.failedCount.collectAsStateWithLifecycle().value
    val completedCount = receipts.count { it.status == UploadStatus.Completed }
    val activeCount = receipts.count { it.status == UploadStatus.Pending || it.status == UploadStatus.Uploading }

    var filter by remember { mutableStateOf(QueueFilter.All) }
    val showGallery = remember { mutableStateOf(false) }
    val receiptPendingDelete = remember { mutableStateOf<ScannedReceipt?>(null) }

    val filtered = remember(receipts, filter) {
        when (filter) {
            QueueFilter.All -> receipts
            QueueFilter.Active -> receipts.filter { it.status == UploadStatus.Pending || it.status == UploadStatus.Uploading }
            QueueFilter.Failed -> receipts.filter { it.status == UploadStatus.Failed }
            QueueFilter.Done -> receipts.filter { it.status == UploadStatus.Completed }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.queue_title),
            subtitle = if (pendingCount > 0) {
                stringResource(R.string.queue_pending_uploads, pendingCount)
            } else {
                stringResource(R.string.queue_all_caught_up)
            },
            trailing = {
                QueueOverflow(
                    clearCompletedEnabled = completedCount > 0,
                    onClearCompleted = viewModel::clearCompleted,
                    onViewGallery = { showGallery.value = true },
                )
            },
        )

        if (receipts.isNotEmpty()) {
            FilterRow(
                filter = filter,
                allCount = receipts.size,
                activeCount = activeCount,
                failedCount = failedCount,
                doneCount = completedCount,
                onSelect = { filter = it },
            )

            AnimatedVisibility(visible = failedCount > 0) {
                GradientButton(
                    text = stringResource(R.string.queue_retry_all_failed, failedCount),
                    onClick = viewModel::retryAllFailed,
                    brush = BrandGradients.Sunset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(isFiltered = receipts.isNotEmpty())
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { receipt ->
                    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                receiptPendingDelete.value = receipt
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = stringResource(R.string.queue_delete),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        ReceiptQueueCard(
                            receipt = receipt,
                            onRetry = { viewModel.retry(receipt.id) },
                            onClick = { onOpenReceipt(receipt.id) },
                        )
                    }
                }
            }
        }
    }

    if (showGallery.value) {
        ModalBottomSheet(
            onDismissRequest = { showGallery.value = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            GallerySheet(
                receipts = receipts,
                onOpenReceipt = { id ->
                    showGallery.value = false
                    onOpenReceipt(id)
                },
            )
        }
    }

    receiptPendingDelete.value?.let { receipt ->
        AlertDialog(
            onDismissRequest = { receiptPendingDelete.value = null },
            title = { Text(stringResource(R.string.queue_delete_title)) },
            text = { Text(stringResource(R.string.queue_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(receipt.id)
                        receiptPendingDelete.value = null
                    },
                ) {
                    Text(stringResource(R.string.queue_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { receiptPendingDelete.value = null }) {
                    Text(stringResource(R.string.queue_cancel))
                }
            },
        )
    }
}

@Composable
private fun QueueOverflow(
    clearCompletedEnabled: Boolean,
    onClearCompleted: () -> Unit,
    onViewGallery: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.queue_more_actions))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_view_gallery)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onViewGallery()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_clear_completed)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                enabled = clearCompletedEnabled,
                onClick = {
                    menuOpen = false
                    onClearCompleted()
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    filter: QueueFilter,
    allCount: Int,
    activeCount: Int,
    failedCount: Int,
    doneCount: Int,
    onSelect: (QueueFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QueueFilterChip(stringResource(R.string.queue_filter_all), allCount, filter == QueueFilter.All) { onSelect(QueueFilter.All) }
        QueueFilterChip(stringResource(R.string.queue_filter_active), activeCount, filter == QueueFilter.Active) { onSelect(QueueFilter.Active) }
        QueueFilterChip(stringResource(R.string.queue_filter_failed), failedCount, filter == QueueFilter.Failed) { onSelect(QueueFilter.Failed) }
        QueueFilterChip(stringResource(R.string.queue_filter_done), doneCount, filter == QueueFilter.Done) { onSelect(QueueFilter.Done) }
    }
}

@Composable
private fun QueueFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (count > 0) "$label · $count" else label) },
        shape = RoundedCornerShape(percent = 50),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun ReceiptQueueCard(
    receipt: ScannedReceipt,
    onRetry: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = remember(receipt.processedPath) {
                    ReceiptBitmapLoader.decodeSampledBitmap(
                        path = receipt.processedPath,
                        requestedWidth = 256,
                        requestedHeight = 256,
                    )
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = receipt.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    receipt.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = listOf(receipt.vendor, receipt.amount)
                    .filter { it.isNotBlank() && it != "UnknownVendor" }
                    .joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatTimestamp(receipt.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (receipt.status == UploadStatus.Uploading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(percent = 50)),
                    )
                }
                receipt.errorMessage?.takeIf { receipt.status == UploadStatus.Failed }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(status = receipt.status)
                if (receipt.status == UploadStatus.Failed) {
                    FilledTonalButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.queue_retry),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: UploadStatus) {
    val (container, content, label) = when (status) {
        UploadStatus.Completed -> Triple(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f), MaterialTheme.colorScheme.tertiary, stringResource(R.string.queue_status_completed))
        UploadStatus.Failed -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, stringResource(R.string.queue_status_failed))
        UploadStatus.Uploading -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, stringResource(R.string.queue_status_uploading))
        UploadStatus.Pending -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.queue_status_pending))
    }
    Surface(shape = RoundedCornerShape(percent = 50), color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(content),
            )
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyState(isFiltered: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(BrandGradients.Ocean),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = Color.White,
                )
            }
            Text(
                stringResource(if (isFiltered) R.string.queue_empty_filter else R.string.queue_no_uploads),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(if (isFiltered) R.string.queue_empty_filter_body else R.string.queue_no_uploads_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GallerySheet(receipts: List<ScannedReceipt>, onOpenReceipt: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.queue_all_receipts), style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .heightIn(min = 240.dp),
        ) {
            items(receipts.size) { index ->
                val receipt = receipts[index]
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { onOpenReceipt(receipt.id) },
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bitmap = remember(receipt.processedPath) {
                            ReceiptBitmapLoader.decodeSampledBitmap(
                                path = receipt.processedPath,
                                requestedWidth = 512,
                                requestedHeight = 512,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(140.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = receipt.fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Text(receipt.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(status = receipt.status)
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, h:mm a", Locale.US).format(Date(timestamp))
}
