package com.scantoftp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.scantoftp.MainActivity
import com.scantoftp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val uploadChannel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            "Active uploads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows upload progress while ReceiptMux syncs receipts."
            setShowBadge(false)
        }
        val queueChannel = NotificationChannel(
            QUEUE_CHANNEL_ID,
            "Upload queue",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps a badge-friendly summary of pending receipt uploads."
            setShowBadge(true)
        }

        manager.createNotificationChannel(uploadChannel)
        manager.createNotificationChannel(queueChannel)
    }

    fun createForegroundInfo(workerId: UUID, pendingCount: Int, currentFileName: String?): ForegroundInfo {
        ensureChannels()
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workerId)
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.notification_upload_title))
            .setContentText(
                currentFileName ?: context.getString(R.string.notification_upload_text, pendingCount),
            )
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(pendingCount.coerceAtLeast(1), 1, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        return ForegroundInfo(
            UPLOAD_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    fun refreshQueueBadge(pendingCount: Int) {
        ensureChannels()
        val notifications = NotificationManagerCompat.from(context)
        if (pendingCount <= 0) {
            notifications.cancel(QUEUE_NOTIFICATION_ID)
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            QUEUE_NOTIFICATION_ID,
            MainActivity.createQueueIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, QUEUE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(context.getString(R.string.notification_queue_title, pendingCount))
            .setContentText(context.getString(R.string.notification_queue_text))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setNumber(pendingCount)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()

        notifications.notify(QUEUE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val UPLOAD_CHANNEL_ID = "receiptmux_uploads"
        private const val QUEUE_CHANNEL_ID = "receiptmux_queue"
        private const val UPLOAD_NOTIFICATION_ID = 4101
        private const val QUEUE_NOTIFICATION_ID = 4102
    }
}
