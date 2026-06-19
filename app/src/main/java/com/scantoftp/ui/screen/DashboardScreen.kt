package com.scantoftp.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.ScannedReceipt
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SectionLabel
import com.scantoftp.ui.components.bounceClick
import com.scantoftp.ui.theme.Brand
import com.scantoftp.ui.theme.BrandGradients
import com.scantoftp.ui.viewmodel.DashboardViewModel
import com.scantoftp.util.ReceiptBitmapLoader

@Composable
fun DashboardScreen(
    onScan: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Box(modifier = Modifier.fillMaxSize()) {
        HeaderShimmer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(260.dp),
        )

        Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.dashboard_title),
            subtitle = if (state.destinationLabel.isNotBlank()) {
                stringResource(R.string.dashboard_destination, state.destinationLabel)
            } else {
                stringResource(R.string.dashboard_subtitle)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.setupComplete) {
                SetupBanner(onOpenSettings = onOpenSettings)
            }

            ScanCta(onScan = onScan)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.dashboard_overview))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        value = state.pending,
                        label = stringResource(R.string.dashboard_stat_pending),
                    accent = Brand.Indigo,
                    modifier = Modifier.weight(1f).bounceClick(onClick = onOpenQueue),
                )
                    StatTile(
                        value = state.completed,
                        label = stringResource(R.string.dashboard_stat_uploaded),
                        accent = Brand.Emerald,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.failed,
                        label = stringResource(R.string.dashboard_stat_failed),
                    accent = Brand.Rose,
                    modifier = Modifier.weight(1f).bounceClick(onClick = onOpenQueue),
                )
                }
            }

            RecentSection(recent = state.recent, onViewAll = onOpenQueue, onOpenReceipt = onOpenReceipt)
        }
        }
    }
}

@Composable
private fun HeaderShimmer(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "headerShimmer")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobA",
    )
    val b by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobB",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Brand.Violet.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(w * (0.18f + 0.5f * a), h * 0.08f),
                radius = w * 0.62f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Brand.Sky.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * (0.82f - 0.5f * b), h * 0.34f),
                radius = w * 0.58f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Brand.Pink.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * (0.5f + 0.3f * b), -h * 0.05f),
                radius = w * 0.5f,
            ),
        )
    }
}

@Composable
private fun SetupBanner(onOpenSettings: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.dashboard_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.dashboard_setup_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ScanCta(onScan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onScan)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(BrandGradients.Hero)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dashboard_scan),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.dashboard_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun StatTile(
    value: Int,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "statCount",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(animated.toString(), style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = accent.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RecentSection(recent: List<ScannedReceipt>, onViewAll: () -> Unit, onOpenReceipt: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.dashboard_recent))
            if (recent.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onViewAll),
                ) {
                    Text(
                        stringResource(R.string.dashboard_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (recent.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.dashboard_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recent, key = { it.id }) { receipt ->
                    RecentThumb(receipt = receipt, onClick = { onOpenReceipt(receipt.id) })
                }
            }
        }
    }
}

@Composable
private fun RecentThumb(receipt: ScannedReceipt, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.bounceClick(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp, 124.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = androidx.compose.runtime.remember(receipt.processedPath) {
                ReceiptBitmapLoader.decodeSampledBitmap(
                    path = receipt.processedPath,
                    requestedWidth = 256,
                    requestedHeight = 320,
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
            StatusDot(
                status = receipt.status,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Text(
            receipt.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun StatusDot(status: UploadStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        UploadStatus.Completed -> MaterialTheme.colorScheme.tertiary
        UploadStatus.Failed -> MaterialTheme.colorScheme.error
        UploadStatus.Uploading -> MaterialTheme.colorScheme.primary
        UploadStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = color, modifier = modifier.size(12.dp)) {}
}
