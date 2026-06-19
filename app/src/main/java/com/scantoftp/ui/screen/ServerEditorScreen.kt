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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantoftp.R
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.model.isValid
import com.scantoftp.domain.model.toUploadConfig
import com.scantoftp.ui.components.GradientButton
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SecondaryButton
import com.scantoftp.ui.viewmodel.RemoteBrowserState
import com.scantoftp.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditorScreen(
    kind: String,
    profileId: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isSmb = kind == SERVER_KIND_SMB
    val isNew = profileId == com.scantoftp.ui.navigation.Destination.ServerEditor.NEW_PROFILE
    val profilesList = viewModel.profiles.collectAsStateWithLifecycle().value
    val remoteBrowser = viewModel.remoteBrowser.collectAsStateWithLifecycle().value
    val existing = remember(profilesList, profileId) { profilesList.firstOrNull { it.id == profileId } }

    var profile by remember {
        mutableStateOf(ServerProfile.blank(if (isSmb) UploadProtocol.Smb else UploadProtocol.Ftp))
    }
    var loaded by remember { mutableStateOf(isNew) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (existing != null && !loaded) {
            profile = existing
            loaded = true
        }
    }

    // Reversed SMB flow: once the connection fields are complete, automatically
    // connect and open the folder browser instead of making the user tap a button.
    // Keyed on the credentials so it re-attempts (debounced) as they finish typing,
    // and only while a destination folder hasn't been chosen yet.
    var lastAutoConnectSignature by remember { mutableStateOf<String?>(null) }
    val canConnect = profile.host.isNotBlank() &&
        profile.port in 1..65535 &&
        profile.username.isNotBlank() &&
        profile.password.isNotBlank()
    val credentialSignature = "${profile.host}|${profile.port}|${profile.username}|${profile.password}"
    if (isSmb) {
        LaunchedEffect(loaded, canConnect, credentialSignature, profile.remoteDirectory) {
            if (loaded &&
                canConnect &&
                profile.remoteDirectory.isBlank() &&
                credentialSignature != lastAutoConnectSignature
            ) {
                delay(700)
                lastAutoConnectSignature = credentialSignature
                viewModel.openRemoteBrowser(profile.toUploadConfig())
            }
        }
    }

    val titleRes = when {
        isNew && isSmb -> R.string.editor_new_smb
        isNew -> R.string.editor_new_ftp
        isSmb -> R.string.editor_edit_smb
        else -> R.string.editor_edit_ftp
    }
    val fieldShape = MaterialTheme.shapes.small

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(titleRes), onBack = onBack)

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EditorField(profile.name, { profile = profile.copy(name = it) }, stringResource(R.string.editor_name), fieldShape, profile.name.isBlank())
            }
            item {
                EditorField(profile.host, { profile = profile.copy(host = it) }, stringResource(if (isSmb) R.string.settings_nas_host else R.string.settings_ftp_host), fieldShape, profile.host.isBlank())
            }
            item {
                OutlinedTextField(
                    value = if (profile.port == 0) "" else profile.port.toString(),
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit).take(5)
                        profile = profile.copy(port = digits.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.settings_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape,
                    singleLine = true,
                    isError = profile.port !in 1..65535,
                )
            }
            item {
                EditorField(profile.username, { profile = profile.copy(username = it) }, stringResource(R.string.settings_username), fieldShape, profile.username.isBlank())
            }
            item {
                OutlinedTextField(
                    value = profile.password,
                    onValueChange = { profile = profile.copy(password = it) },
                    label = { Text(stringResource(R.string.settings_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    singleLine = true,
                    isError = profile.password.isBlank(),
                    supportingText = if (!isNew && profile.password.isBlank()) {
                        { Text(stringResource(R.string.editor_password_missing)) }
                    } else {
                        null
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.settings_hide_password) else stringResource(R.string.settings_show_password),
                            )
                        }
                    },
                )
            }
            item {
                if (isSmb) {
                    SmbDestinationField(
                        folder = profile.remoteDirectory,
                        canConnect = canConnect,
                        connecting = remoteBrowser.isLoading,
                        onBrowse = { viewModel.openRemoteBrowser(profile.toUploadConfig()) },
                        shape = fieldShape,
                    )
                } else {
                    EditorField(
                        profile.remoteDirectory,
                        { profile = profile.copy(remoteDirectory = it) },
                        stringResource(R.string.settings_remote_directory),
                        fieldShape,
                        profile.remoteDirectory.isBlank(),
                    )
                }
            }
            if (!isSmb) {
                item {
                    FtpsToggle(
                        checked = profile.protocol == UploadProtocol.Ftps,
                        onCheckedChange = { profile = profile.copy(protocol = if (it) UploadProtocol.Ftps else UploadProtocol.Ftp) },
                    )
                }
            }
            item {
                GradientButton(
                    text = stringResource(R.string.editor_save),
                    onClick = {
                        viewModel.saveProfile(profile)
                        onBack()
                    },
                    enabled = profile.isValid(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (!isNew) {
                item {
                    SecondaryButton(
                        text = stringResource(R.string.editor_delete),
                        onClick = {
                            viewModel.deleteProfile(profile.id)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (remoteBrowser.isVisible) {
        RemoteFolderBrowserSheet(
            state = remoteBrowser,
            onDismiss = viewModel::dismissRemoteBrowser,
            onNavigateUp = viewModel::browseUp,
            onDirectorySelected = viewModel::browseInto,
            onUseCurrentFolder = { viewModel.selectBrowsedFolder { path -> profile = profile.copy(remoteDirectory = path) } },
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        singleLine = true,
        isError = isError,
    )
}

@Composable
private fun SmbDestinationField(
    folder: String,
    canConnect: Boolean,
    connecting: Boolean,
    onBrowse: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val hasFolder = folder.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_destination_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val statusText = when {
                        hasFolder -> folder
                        connecting -> stringResource(R.string.editor_connecting)
                        canConnect -> stringResource(R.string.editor_smb_auto_hint)
                        else -> stringResource(R.string.editor_smb_fill_login)
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hasFolder) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (connecting && !hasFolder) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (hasFolder || canConnect) {
            SecondaryButton(
                text = stringResource(if (hasFolder) R.string.editor_smb_change else R.string.settings_browse_nas_folder),
                onClick = onBrowse,
                enabled = canConnect && !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FtpsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.editor_protocol_ftps), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.editor_protocol_ftps_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteFolderBrowserSheet(
    state: RemoteBrowserState,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onDirectorySelected: (String) -> Unit,
    onUseCurrentFolder: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_browse_nas_folder), style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.currentPath == "/") stringResource(R.string.settings_select_share) else state.currentPath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onNavigateUp, enabled = state.currentPath != "/") {
                    Text(stringResource(R.string.settings_up))
                }
                TextButton(onClick = onUseCurrentFolder, enabled = !state.isLoading && state.currentPath != "/") {
                    Text(stringResource(R.string.settings_use_this_folder))
                }
            }
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.errorMessage != null -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                else -> {
                    if (state.directories.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_no_folders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.directories, key = { it.path }) { directory ->
                                ListItem(
                                    headlineContent = { Text(directory.name) },
                                    supportingContent = { Text(directory.path) },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onDirectorySelected(directory.path) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
