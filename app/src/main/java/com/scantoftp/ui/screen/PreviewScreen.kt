package com.scantoftp.ui.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.ui.components.GradientButton
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SecondaryButton
import com.scantoftp.ui.viewmodel.PreviewViewModel
import com.scantoftp.util.ReceiptBitmapLoader

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onAdjustCrop: () -> Unit,
    onSettingsRequested: () -> Unit,
    onUploaded: (Long) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val showProcessed = remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    var localFileName by remember(state.fileName) { mutableStateOf(state.fileName) }

    if (!state.hasCapture) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.preview_no_capture), style = MaterialTheme.typography.titleLarge)
        }
        return
    }

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
                title = stringResource(R.string.preview_title),
                modifier = Modifier.weight(1f).padding(horizontal = 0.dp),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                val previewPath = if (showProcessed.value) state.afterPath else state.beforePath
                val bitmap = remember(previewPath) {
                    ReceiptBitmapLoader.decodeSampledBitmap(
                        path = previewPath,
                        requestedWidth = 1440,
                        requestedHeight = 2200,
                    )
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_receipt),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = if (showProcessed.value) {
                            stringResource(R.string.preview_processed_receipt)
                        } else {
                            stringResource(R.string.preview_original_receipt)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleChip(text = stringResource(R.string.preview_processed), selected = showProcessed.value) { showProcessed.value = true }
            ToggleChip(text = stringResource(R.string.preview_original), selected = !showProcessed.value) { showProcessed.value = false }
        }

        OutlinedTextField(
            value = localFileName,
            onValueChange = {
                localFileName = it
                viewModel.updateFileName(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.preview_filename)) },
            shape = MaterialTheme.shapes.small,
            singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(label = stringResource(R.string.preview_vendor), value = state.vendor, modifier = Modifier.weight(1f))
            InfoCard(label = stringResource(R.string.preview_amount), value = state.amount, modifier = Modifier.weight(1f))
        }
        InfoCard(
            label = stringResource(R.string.preview_receipt_date),
            value = state.receiptDate,
            modifier = Modifier.fillMaxWidth(),
            footer = "Crop confidence ${(state.cropConfidence * 100).toInt()}%",
        )

        if (state.cropConfidence < 0.72f) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.preview_low_confidence_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.preview_low_confidence_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.preview_adjust_crop),
                        onClick = onAdjustCrop,
                    )
                }
            }
        }

        if (state.setupIssues.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.preview_finish_setup),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        state.setupIssues.first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.scanner_open_settings),
                        onClick = onSettingsRequested,
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.preview_retake),
                onClick = { viewModel.discardCapture(onBack) },
                modifier = Modifier.weight(1f),
                enabled = !state.isUploading,
            )
            GradientButton(
                text = if (state.isUploading) stringResource(R.string.preview_uploading) else stringResource(R.string.preview_upload),
                onClick = { viewModel.confirmUpload(onUploaded) },
                modifier = Modifier.weight(1f),
                enabled = !state.isUploading && state.setupIssues.isEmpty(),
            )
        }
    }
}

@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (footer != null) {
                Text(
                    footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
