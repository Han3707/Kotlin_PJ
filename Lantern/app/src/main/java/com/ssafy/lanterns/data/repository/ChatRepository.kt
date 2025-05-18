package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // ChatRoom 관련
    suspend fun createChatRoom(chatRoom: ChatRoom)
    suspend fun getChatRoom(chatRoomId: String): ChatRoom?
    suspend fun getChatRoomByParticipantId(participantId: Long): ChatRoom?
    fun getAllChatRooms(): Flow<List<ChatRoom>>
    suspend fun updateChatRoom(chatRoom: ChatRoom)
    suspend fun deleteChatRoom(chatRoomId: String) // 채팅방 나가기/삭제 시 메시지도 함께 삭제 (CASCADE)
    suspend fun findChatRoomById(chatRoomId: String): ChatRoom?

    // Message 관련
    suspend fun saveMessage(message: Message): Long
    fun getMessagesForChatRoom(chatRoomId: String): Flow<List<Message>>
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)
    suspend fun getLatestMessageForChatRoom(chatRoomId: String): Message?
    suspend fun updateChatRoomWithLastMessage(chatRoomId: String)

    /**
     * 주어진 chatRoomId로 채팅방을 조회하고, 없으면 새로운 채팅방을 생성하여 반환합니다.
     * 새로운 채팅방은 currentUser.userId와 targetUserId를 사용하여 생성됩니다.
     * @param chatRoomId 조회 또는 생성할 채팅방의 ID
     * @param currentUserId 현재 사용자의 ID
     * @param targetUserId 상대방 사용자의 ID
     * @return 조회되거나 생성된 ChatRoom 객체
     */
    suspend fun getOrCreateChatRoom(chatRoomId: String, currentUserId: Long, targetUserId: Long): ChatRoom
} 