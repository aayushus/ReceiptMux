package com.scantoftp.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.scantoftp.data.local.AppDatabase
import com.scantoftp.data.local.ReceiptDao
import com.scantoftp.data.repository.DataStoreSettingsRepository
import com.scantoftp.data.repository.InMemoryCaptureSessionRepository
import com.scantoftp.data.repository.RoomReceiptRepository
import com.scantoftp.data.service.BitmapReceiptProcessor
import com.scantoftp.data.service.MlKitReceiptTextRecognizer
import com.scantoftp.data.service.ProtocolUploadClient
import com.scantoftp.domain.repository.CaptureSessionRepository
import com.scantoftp.domain.repository.ReceiptRepository
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.ReceiptProcessor
import com.scantoftp.domain.service.ReceiptTextRecognizer
import com.scantoftp.domain.service.UploadClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds abstract fun bindCaptureSessionRepository(impl: InMemoryCaptureSessionRepository): CaptureSessionRepository
    @Binds abstract fun bindReceiptRepository(impl: RoomReceiptRepository): ReceiptRepository
    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindReceiptProcessor(impl: BitmapReceiptProcessor): ReceiptProcessor
    @Binds abstract fun bindTextRecognizer(impl: MlKitReceiptTextRecognizer): ReceiptTextRecognizer
    @Binds abstract fun bindUploadClient(impl: ProtocolUploadClient): UploadClient
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "scan_to_ftp.db").build()
    }

    @Provides
    fun provideReceiptDao(database: AppDatabase): ReceiptDao = database.receiptDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
