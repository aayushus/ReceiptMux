package com.scantoftp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.ui.components.GradientButton
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.bounceClick
import com.scantoftp.ui.theme.BrandGradients
import com.scantoftp.ui.viewmodel.SettingsViewModel

@Composable
fun ServerListScreen(
    kind: String,
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profiles = viewModel.profiles.collectAsStateWithLifecycle().value
    val activeId = viewModel.activeProfileId.collectAsStateWithLifecycle().value
    val isSmb = kind == SERVER_KIND_SMB
    val filtered = profiles.filter { if (isSmb) it.isSmb else it.isFtpFamily }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(if (isSmb) R.string.servers_smb_title else R.string.servers_ftp_title),
            onBack = onBack,
            trailing = {
                IconButton(onClick = onAddServer) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.servers_add))
                }
            },
        )

        if (filtered.isEmpty()) {
            EmptyServers(onAddServer = onAddServer)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { profile ->
                    ServerCard(
                        profile = profile,
                        isActive = profile.id == activeId,
                        onEdit = { onEditServer(profile.id) },
                        onSetActive = { viewModel.setActiveProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyServers(onAddServer: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BrandGradients.Ocean),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Storage, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Text(stringResource(R.string.servers_empty), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.servers_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            GradientButton(
                text = stringResource(R.string.servers_add),
                onClick = onAddServer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServerCard(
    profile: ServerProfile,
    isActive: Boolean,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().bounceClick(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        profile.name.ifBlank { profile.host },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProtocolPill(profile.protocol)
                }
                Text(
                    "${profile.host}:${profile.port}${profile.remoteDirectory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ActiveControl(isActive = isActive, onSetActive = onSetActive)
        }
    }
}

@Composable
private fun ActiveControl(isActive: Boolean, onSetActive: () -> Unit) {
    if (isActive) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.servers_active),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            onClick = onSetActive,
        ) {
            Text(
                stringResource(R.string.servers_set_active),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ProtocolPill(protocol: UploadProtocol) {
    val label = when (protocol) {
        UploadProtocol.Smb -> "SMB"
        UploadProtocol.Ftps -> "FTPS"
        UploadProtocol.Ftp -> "FTP"
    }
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
