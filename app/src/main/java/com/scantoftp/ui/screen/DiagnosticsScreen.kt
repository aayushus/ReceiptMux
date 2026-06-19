package com.scantoftp.ui.screen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scantoftp.BuildConfig
import com.scantoftp.R
import com.scantoftp.ui.components.ScreenHeader
import com.scantoftp.ui.components.SectionCard

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val openCvVersion = runCatching { org.opencv.core.Core.VERSION }.getOrDefault("Unknown")

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.diagnostics_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard {
                    DiagRow(stringResource(R.string.diagnostics_opencv), openCvVersion)
                    DiagRow(stringResource(R.string.diagnostics_mlkit), "On-device (bundled)")
                }
            }
            item {
                SectionCard {
                    DiagRow(stringResource(R.string.diagnostics_app_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    DiagRow(stringResource(R.string.diagnostics_package), BuildConfig.APPLICATION_ID)
                    DiagRow(stringResource(R.string.diagnostics_device), "${Build.MANUFACTURER} ${Build.MODEL}")
                    DiagRow(stringResource(R.string.diagnostics_android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
