package com.ssafy.lanterns.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ssafy.lanterns.data.model.ChatRoom
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatRoom(chatRoom: ChatRoom)

    @Query("SELECT * FROM chat_rooms WHERE chatRoomId = :chatRoomId")
    suspend fun getChatRoom(chatRoomId: String): ChatRoom?

    @Query("SELECT * FROM chat_rooms WHERE participantId = :participantId")
    suspend fun getChatRoomByParticipantId(participantId: Long): ChatRoom?

    @Query("SELECT * FROM chat_rooms WHERE participantId = :userId")
    suspend fun getChatRoomsByParticipantId(userId: Long): List<ChatRoom>

    @Query("SELECT * FROM chat_rooms ORDER BY updatedAt DESC")
    fun getAllChatRooms(): Flow<List<ChatRoom>>

    @Update
    suspend fun updateChatRoom(chatRoom: ChatRoom)

    @Query("DELETE FROM chat_rooms WHERE chatRoomId = :chatRoomId")
    suspend fun deleteChatRoom(chatRoomId: String)

    @Query("SELECT * FROM chat_rooms WHERE chatRoomId = :chatRoomId LIMIT 1")
    suspend fun findChatRoomById(chatRoomId: String): ChatRoom?

    @Query("DELETE FROM chat_rooms")
    suspend fun deleteAllChatRooms()

    @Query("""
        UPDATE chat_rooms 
        SET updatedAt = ( 
          SELECT MAX(timestamp) FROM messages 
          WHERE chatRoomId = :chatRoomId 
        )
        WHERE chatRoomId = :chatRoomId
    """)
    suspend fun updateChatRoomUpdatedAt(chatRoomId: String): Int
} 