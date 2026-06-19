package com.scantoftp.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.UploadStatus
import com.scantoftp.ui.components.GradientButton
import com.scantoftp.ui.components.SecondaryButton
import com.scantoftp.ui.theme.BrandGradients
import com.scantoftp.ui.viewmodel.UploadResultViewModel
import com.scantoftp.util.ReceiptBitmapLoader

@Composable
fun UploadResultScreen(
    onScanAnother: () -> Unit,
    onDone: () -> Unit,
    onViewQueue: () -> Unit,
    viewModel: UploadResultViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val status = state.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        StatusVisual(status = status)
        Spacer(Modifier.height(24.dp))

        val (title, text) = when (status) {
            UploadStatus.Completed -> stringResource(R.string.upload_result_success_title) to stringResource(R.string.upload_result_success_text)
            UploadStatus.Failed -> stringResource(R.string.upload_result_failed_title) to stringResource(R.string.upload_result_failed_text)
            UploadStatus.Uploading -> stringResource(R.string.upload_result_uploading_title) to stringResource(R.string.upload_result_uploading_text)
            UploadStatus.Pending -> stringResource(R.string.upload_result_pending_title) to stringResource(R.string.upload_result_pending_text)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.receipt?.errorMessage?.takeIf { status == UploadStatus.Failed } ?: text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        state.receipt?.let { receipt ->
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp, 150.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = remember(receipt.processedPath) {
                            ReceiptBitmapLoader.decodeSampledBitmap(
                                path = receipt.processedPath,
                                requestedWidth = 320,
                                requestedHeight = 400,
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
                    Text(receipt.fileName, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == UploadStatus.Failed) {
                GradientButton(
                    text = stringResource(R.string.upload_result_retry),
                    onClick = viewModel::retry,
                    brush = BrandGradients.Sunset,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.upload_result_view_queue),
                    onClick = onViewQueue,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GradientButton(
                    text = stringResource(R.string.upload_result_scan_another),
                    onClick = onScanAnother,
                    brush = BrandGradients.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SecondaryButton(
                text = stringResource(R.string.upload_result_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusVisual(status: UploadStatus) {
    val brush = when (status) {
        UploadStatus.Completed -> BrandGradients.Success
        UploadStatus.Failed -> BrandGradients.Sunset
        else -> BrandGradients.Hero
    }
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            UploadStatus.Completed -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
            UploadStatus.Failed -> Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(46.dp),
                strokeWidth = 4.dp,
                color = Color.White,
            )
        }
    }
}
