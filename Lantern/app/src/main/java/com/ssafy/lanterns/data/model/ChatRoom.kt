package com.ssafy.lanterns.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "chat_rooms")
data class ChatRoom(
    @PrimaryKey 
    @ColumnInfo(name = "chatRoomId", defaultValue = "")
    val chatRoomId: String,
    
    @ColumnInfo(name = "participantId", defaultValue = "0")
    val participantId: Long,
    
    @ColumnInfo(name = "participantNickname", defaultValue = "")
    val participantNickname: String,
    
    @ColumnInfo(name = "participantProfileImageNumber", defaultValue = "1")
    val participantProfileImageNumber: Int,
    
    @ColumnInfo(name = "lastMessage")
    val lastMessage: String? = null,
    
    @ColumnInfo(name = "updatedAt")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
