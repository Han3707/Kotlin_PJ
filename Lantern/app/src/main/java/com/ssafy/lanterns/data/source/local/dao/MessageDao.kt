package com.ssafy.lanterns.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * 메시지 데이터 액세스 객체 인터페이스
 */
@Dao
interface MessageDao {
    /**
     * 메시지 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    /**
     * 메시지 업데이트
     */
    @Update
    suspend fun updateMessage(message: Message)

    /**
     * 채팅방의 모든 메시지 조회
     */
    @Query("SELECT * FROM messages WHERE chatRoomId = :chatRoomId ORDER BY timestamp ASC")
    fun getMessagesForChatRoom(chatRoomId: String): Flow<List<Message>>

    /**
     * 채팅방의 메시지 삭제
     */
    @Query("DELETE FROM messages WHERE chatRoomId = :chatRoomId")
    suspend fun deleteMessagesForChatRoom(chatRoomId: String)

    /**
     * 메시지 상태 업데이트
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    /**
     * 최근 메시지 조회
     */
    @Query("SELECT * FROM messages WHERE chatRoomId = :chatRoomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageForChatRoom(chatRoomId: String): Message?

    /**
     * 모든 메시지 삭제
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
} 