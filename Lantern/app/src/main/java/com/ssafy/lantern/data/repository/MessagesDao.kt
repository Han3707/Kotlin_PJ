package com.ssafy.lantern.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ssafy.lantern.data.model.Messages
import java.time.LocalDateTime

@Dao
interface MessagesDao {

    // 메세지 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Messages): Long

    /**
     * 특정 채팅방의 모든 메시지를 삭제합니다.
     * @param chatRoomId 삭제할 메시지들의 chat_room_id
     * @return 삭제된 행(row) 수
     */
    @Query("""
        DELETE FROM messages
        WHERE chat_room_id = :chatRoomId
    """)
    suspend fun deleteMessagesByChatRoomId(chatRoomId: Long): Int


    // 메세지 불러오기
    @Query("""
        SELECT * FROM messages
        WHERE chat_room_id = :chatRoomId
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMessages(
        chatRoomId: Long,
        limit: Int,
        offset: Int
    ): List<Messages>


    // 스크롤 업 방식 페이징
    @Query("""
        SELECT * FROM messages
        WHERE chat_room_id = :chatRoomId
          AND date < :beforeDate
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getMessagesBefore(
        chatRoomId: Long,
        beforeDate: LocalDateTime,
        limit: Int
    ): List<Messages>



}