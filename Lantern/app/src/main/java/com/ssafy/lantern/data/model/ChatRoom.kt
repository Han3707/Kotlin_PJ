package com.ssafy.lantern.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "chat_room")
data class ChatRoom(
    @PrimaryKey
    @ColumnInfo(name = "chat_room_id")
    val chatRoomId: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "participant_id")
    val participantId: Long
)
