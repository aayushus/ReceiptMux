package com.scantoftp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.scantoftp.notification.UploadNotificationManager
import com.scantoftp.worker.UploadWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class ScanToFtpApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var uploadNotificationManager: UploadNotificationManager
    @Inject lateinit var uploadWorkScheduler: UploadWorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        uploadNotificationManager.ensureChannels()
        uploadWorkScheduler.enqueueUploadQueue()
        runCatching { OpenCVLoader.initLocal() }
    }
}
