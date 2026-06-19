package com.scantoftp.data.local

import androidx.room.TypeConverter
import com.scantoftp.domain.model.UploadStatus

class Converters {
    @TypeConverter
    fun toStatus(raw: String): UploadStatus = UploadStatus.valueOf(raw)

    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name
}
