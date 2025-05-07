package com.ssafy.lantern.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "messages")
data class Messages(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "chat_room_id")
    val chatRoomId: Long,

    val text: String,

    val date: LocalDateTime,


)
