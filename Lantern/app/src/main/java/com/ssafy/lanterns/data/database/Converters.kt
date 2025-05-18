package com.ssafy.lanterns.data.database

import androidx.room.TypeConverter
import com.ssafy.lanterns.data.model.MessageStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? =
        value?.format(fmt)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, fmt) }

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toMessageStatus(statusString: String?): MessageStatus? {
        return statusString?.let { enumValueOf<MessageStatus>(it) }
    }
}