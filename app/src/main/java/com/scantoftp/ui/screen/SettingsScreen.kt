package com.scantoftp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SectionCard
import com.scantoftp.ui.components.SectionLabel
import com.scantoftp.ui.viewmodel.SettingsViewModel

const val SERVER_KIND_SMB = "smb"
const val SERVER_KIND_FTP = "ftp"

@Composable
fun SettingsScreen(
    onOpenServers: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value
    val profiles = viewModel.profiles.collectAsStateWithLifecycle().value
    val smbCount = profiles.count { it.isSmb }
    val ftpCount = profiles.count { it.isFtpFamily }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.settings_group_destinations)) {
                    NavRow(
                        title = stringResource(R.string.settings_smb_servers),
                        subtitle = stringResource(R.string.settings_smb_servers_desc),
                        status = profileCountLabel(smbCount),
                        onClick = { onOpenServers(SERVER_KIND_SMB) },
                    )
                    NavRow(
                        title = stringResource(R.string.settings_ftp_servers),
                        subtitle = stringResource(R.string.settings_ftp_servers_desc),
                        status = profileCountLabel(ftpCount),
                        onClick = { onOpenServers(SERVER_KIND_FTP) },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_capture)) {
                    ToggleRow(
                        title = stringResource(R.string.settings_auto_capture),
                        subtitle = stringResource(R.string.settings_auto_capture_subtitle),
                        checked = settings.autoCaptureEnabled,
                        onCheckedChange = viewModel::setAutoCapture,
                    )
                    Text(stringResource(R.string.settings_flash_mode), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlashMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.flashMode == mode,
                                onClick = { viewModel.setFlashMode(mode) },
                                label = { Text(mode.label()) },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                border = null,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_image)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_image_quality), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${settings.imageQuality}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.imageQuality.toFloat(),
                        onValueChange = viewModel::setImageQuality,
                        valueRange = 50f..100f,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_group_network)) {
                    ToggleRow(
                        title = stringResource(R.string.settings_wifi_only),
                        subtitle = stringResource(R.string.settings_wifi_only_desc),
                        checked = settings.uploadOnWifiOnly,
                        onCheckedChange = viewModel::setUploadOnWifiOnly,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_group_experimental)) {
                    ToggleRow(
                        title = stringResource(R.string.settings_ocr_title),
                        subtitle = stringResource(R.string.settings_ocr_desc),
                        checked = settings.ocrRenamingEnabled,
                        onCheckedChange = viewModel::setOcrRenaming,
                        badge = stringResource(R.string.settings_badge_experimental),
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_group_advanced)) {
                    NavRow(
                        title = stringResource(R.string.settings_diagnostics),
                        subtitle = stringResource(R.string.settings_diagnostics_desc),
                        status = null,
                        onClick = onOpenDiagnostics,
                    )
                }
            }
        }
    }
}

@Composable
private fun profileCountLabel(count: Int): String =
    if (count > 0) stringResource(R.string.settings_profile_count, count) else stringResource(R.string.settings_profile_none)

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    status: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (badge != null) {
                    ExperimentalBadge(badge)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ExperimentalBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun FlashMode.label(): String = when (this) {
    FlashMode.Auto -> stringResource(R.string.settings_flash_auto)
    FlashMode.AlwaysOff -> stringResource(R.string.settings_flash_off)
    FlashMode.AlwaysOn -> stringResource(R.string.settings_flash_on)
}
