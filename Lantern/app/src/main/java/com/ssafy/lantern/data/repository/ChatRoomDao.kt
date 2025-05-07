package com.ssafy.lantern.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ssafy.lantern.data.model.ChatRoom
import java.time.LocalDateTime

@Dao
interface ChatRoomDao {

    /**
     * 채팅방 추가.
     * @return 추가된 행 위치
     * **/
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun InsertChatRoom(chatRoom: ChatRoom): Long

    /**
     * chatRoomId 에 해당하는 채팅방 삭제
     * @param chatRoomId 삭제할 채팅방의 ID
     * @return 삭제된 행(row) 수 (1이면 삭제 성공)
     */
    @Query("DELETE FROM chat_room WHERE chat_room_id = :chatRoomId")
    suspend fun deleteChatRoomById(chatRoomId: Long): Int

    /**
     * chatRoomId 방의 모든 메시지 중 가장 최신 date로 updatedAt을 갱신
     * @param chatRoomId 채팅방 ID
     * @return 업데이트된 행(row) 수 -> 1이면 업데이트 성공, 0이면 실패
     */
    @Query("""
        UPDATE chat_room
        SET updated_at = (
          SELECT MAX(date) FROM messages
          WHERE chat_room_id = :chatRoomId
        )
        WHERE chat_room_id = :chatRoomId
    """)
    suspend fun updateChatRoomUpdateAt(chatRoomId: Long): Int


    /**
     * 특정 참가자의 채팅방 가져오기
     * @param participantId 조회할 participant_id
     * @return 해당 participant가 속한 채팅방 리스트
     */
    @Query("""
        SELECT * 
        FROM chat_room 
        WHERE participant_id = :participantId
        ORDER BY updated_at DESC
    """)
    suspend fun getChatRoomsByParticipantId(participantId: Long): List<ChatRoom>


}