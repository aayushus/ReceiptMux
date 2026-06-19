package com.scantoftp.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.scantoftp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    settingsRepository: SettingsRepository,
) {
    @Volatile
    private var wifiOnly: Boolean = false

    init {
        settingsRepository.settingsFlow()
            .onEach { wifiOnly = it.uploadOnWifiOnly }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    fun enqueueUploadQueue() {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "receipt_upload"
    }
}
