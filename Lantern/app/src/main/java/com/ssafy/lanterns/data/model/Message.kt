package com.ssafy.lanterns.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

// 메시지 전송 상태
enum class MessageStatus {
    PENDING, // 전송 중
    SENT,    // 전송 완료 (BLE 광고 패킷 발송 완료)
    FAILED,  // 전송 실패
    RECEIVED // 수신된 메시지 (상대방이 보낸 메시지)
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["chatRoomId"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE // 채팅방 삭제 시 관련 메시지도 삭제
        )
    ],
    indices = [Index(value = ["chatRoomId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "messageId", defaultValue = "0")
    val messageId: Long = 0,
    
    @ColumnInfo(name = "chatRoomId", defaultValue = "")
    val chatRoomId: String, // 이 메시지가 속한 채팅방 ID
    
    @ColumnInfo(name = "senderId", defaultValue = "0")
    val senderId: Long, // 메시지 송신자 ID (자신의 ID 또는 상대방 ID)
    
    @ColumnInfo(name = "receiverId", defaultValue = "0")
    val receiverId: Long, // 메시지 수신자 ID
    
    @ColumnInfo(name = "content", defaultValue = "")
    val content: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // 타임스탬프를 LocalDateTime 대신 Long으로 저장
    
    @ColumnInfo(name = "isSentByMe", defaultValue = "0") // SQLite에서는 boolean이 0/1로 저장됨
    val isSentByMe: Boolean, // 내가 보낸 메시지인지 여부
    
    @ColumnInfo(name = "status", defaultValue = "0") // PENDING enum의 ordinal은 0
    val status: MessageStatus = MessageStatus.PENDING, // 메시지 상태
    
    @ColumnInfo(name = "messageType", defaultValue = "TEXT")
    val messageType: String = "TEXT" // 메시지 타입 (TEXT, IMAGE 등)
) 