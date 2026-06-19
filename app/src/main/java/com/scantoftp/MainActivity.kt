package com.scantoftp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.scantoftp.ui.ScanToFtpApp
import com.scantoftp.ui.navigation.Destination
import com.scantoftp.ui.theme.ScanToFtpTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchRoute by mutableStateOf<String?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRoute = sanitizeRoute(intent?.getStringExtra(EXTRA_START_ROUTE))
        requestNotificationPermissionIfNeeded()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ScanToFtpTheme {
                ScanToFtpApp(launchRoute = launchRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRoute = sanitizeRoute(intent.getStringExtra(EXTRA_START_ROUTE))
    }

    // This activity is exported (it is the LAUNCHER entry point), so the start-route
    // extra is attacker-controllable input from arbitrary apps. Only honor it when it
    // matches a known, safe deep-link target; otherwise fall back to the default screen.
    private fun sanitizeRoute(raw: String?): String? =
        raw?.takeIf { it in ALLOWED_START_ROUTES }

    companion object {
        private const val EXTRA_START_ROUTE = "start_route"

        private val ALLOWED_START_ROUTES = setOf(Destination.Queue.route)

        fun createQueueIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_START_ROUTE, Destination.Queue.route)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
