package com.scantoftp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : Destination("home", "Home", Icons.Outlined.GridView)
    data object Scanner : Destination("scanner", "Scanner", Icons.Outlined.PhotoCamera)
    data object Queue : Destination("queue", "Queue", Icons.Outlined.CloudUpload)
    data object Settings : Destination("settings", "Settings", Icons.Outlined.Settings)
    data object Preview : Destination("preview", "Preview", Icons.Outlined.PhotoCamera)
    data object CropAdjust : Destination("crop-adjust", "Adjust", Icons.Outlined.PhotoCamera)

    data object UploadResult : Destination("upload-result", "Done", Icons.Outlined.CloudUpload) {
        const val ARG_RECEIPT_ID = "receiptId"
        val routeWithArg = "$route/{$ARG_RECEIPT_ID}"
        fun routeFor(receiptId: Long) = "$route/$receiptId"
    }

    data object ReceiptViewer : Destination("receipt-viewer", "Receipt", Icons.Outlined.Image) {
        const val ARG_RECEIPT_ID = "receiptId"
        val routeWithArg = "$route/{$ARG_RECEIPT_ID}"
        fun routeFor(receiptId: Long) = "$route/$receiptId"
    }

    data object ServerList : Destination("server-list", "Servers", Icons.Outlined.Storage) {
        const val ARG_KIND = "kind"
        val routeWithArg = "$route/{$ARG_KIND}"
        fun routeFor(kind: String) = "$route/$kind"
    }

    data object ServerEditor : Destination("server-editor", "Server", Icons.Outlined.Storage) {
        const val ARG_KIND = "kind"
        const val ARG_PROFILE_ID = "profileId"
        const val NEW_PROFILE = "new"
        val routeWithArg = "$route/{$ARG_KIND}/{$ARG_PROFILE_ID}"
        fun routeFor(kind: String, profileId: String = NEW_PROFILE) = "$route/$kind/$profileId"
    }

    data object Diagnostics : Destination("diagnostics", "Diagnostics", Icons.Outlined.Settings)
}
