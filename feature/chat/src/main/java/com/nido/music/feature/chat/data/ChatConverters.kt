package com.nido.music.feature.chat.data

import androidx.room.TypeConverter

class ChatConverters {
    @TypeConverter
    fun fromMessageType(type: MessageType?): String? = type?.name

    @TypeConverter
    fun toMessageType(value: String?): MessageType? = value?.let {
        runCatching { MessageType.valueOf(it) }.getOrDefault(MessageType.TEXT)
    }

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus?): String? = status?.name

    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus? = value?.let {
        runCatching { MessageStatus.valueOf(it) }.getOrDefault(MessageStatus.OUTBOX)
    }
}
