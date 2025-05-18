package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.MessageStatus
import com.ssafy.lanterns.data.source.local.dao.ChatRoomDao
import com.ssafy.lanterns.data.source.local.dao.MessageDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override suspend fun createChatRoom(chatRoom: ChatRoom) {
        chatRoomDao.insertChatRoom(chatRoom)
    }

    override suspend fun getChatRoom(chatRoomId: String): ChatRoom? {
        return chatRoomDao.getChatRoom(chatRoomId)
    }

    override suspend fun getChatRoomByParticipantId(participantId: Long): ChatRoom? {
        return chatRoomDao.getChatRoomByParticipantId(participantId)
    }

    override fun getAllChatRooms(): Flow<List<ChatRoom>> {
        return chatRoomDao.getAllChatRooms()
    }

    override suspend fun updateChatRoom(chatRoom: ChatRoom) {
        chatRoomDao.updateChatRoom(chatRoom)
    }

    override suspend fun deleteChatRoom(chatRoomId: String) {
        // CASCADE 옵션으로 인해 연결된 메시지들은 자동으로 삭제됨
        chatRoomDao.deleteChatRoom(chatRoomId)
    }

    override suspend fun findChatRoomById(chatRoomId: String): ChatRoom? {
        return chatRoomDao.findChatRoomById(chatRoomId)
    }

    override suspend fun saveMessage(message: Message): Long {
        val newId = messageDao.insertMessage(message)
        // 메시지 저장 후 해당 채팅방의 lastMessage 및 updatedAt 업데이트
        updateChatRoomWithLastMessage(message.chatRoomId)
        return newId
    }

    override fun getMessagesForChatRoom(chatRoomId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChatRoom(chatRoomId)
    }

    override suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status)
    }

    override suspend fun getLatestMessageForChatRoom(chatRoomId: String): Message? {
        return messageDao.getLatestMessageForChatRoom(chatRoomId)
    }

    override suspend fun updateChatRoomWithLastMessage(chatRoomId: String) {
        val latestMessage = messageDao.getLatestMessageForChatRoom(chatRoomId)
        val chatRoom = chatRoomDao.getChatRoom(chatRoomId)
        if (chatRoom != null && latestMessage != null) {
            val updatedChatRoom = chatRoom.copy(
                lastMessage = latestMessage.content,
                updatedAt = LocalDateTime.now() // 현재 시간으로 업데이트
            )
            chatRoomDao.updateChatRoom(updatedChatRoom)
        }
    }

    override suspend fun getOrCreateChatRoom(chatRoomId: String, currentUserId: Long, targetUserId: Long): ChatRoom {
        var chatRoom = chatRoomDao.getChatRoom(chatRoomId) // ChatRoomDao에 getChatRoom(String)이 있다고 가정
        if (chatRoom == null) {
            // 채팅방이 없으면 새로 생성
            // ChatRoom 엔티티의 participantId는 상대방의 ID를 저장한다고 가정
            // ChatRoom 생성 시 currentUserId는 일반적으로 ChatRoom 엔티티 자체보다는
            // ChatRoomUserJoinTable 같은 연결 테이블이나, participantId를 통해 구분합니다.
            // 여기서는 ChatRoom 엔티티가 participantId (상대방 ID) 만 가진다고 가정합니다.
            val newChatRoom = ChatRoom(
                chatRoomId = chatRoomId,
                participantId = targetUserId, // 상대방 ID 저장
                participantNickname = "새 참가자", // 필수 파라미터 추가
                participantProfileImageNumber = 1, // 필수 파라미터 추가
                lastMessage = null, // 새 채팅방이므로 마지막 메시지 없음
                updatedAt = LocalDateTime.now()
            )
            chatRoomDao.insertChatRoom(newChatRoom) // ChatRoomDao에 insertChatRoom(ChatRoom)이 있다고 가정
            chatRoom = newChatRoom // 새로 생성된 채팅방 반환
        } else {
            // 기존 채팅방이 있지만, participantId가 현재 targetUserId와 다를 경우의 처리 (예: 그룹채팅 확장 등)
            // 1:1 채팅에서는 chatRoomId가 고유하므로 이 경우는 드물지만, 방어적으로 로직 추가 가능
            // if (chatRoom.participantId != targetUserId) { /* 로직 추가 */ }
        }
        return chatRoom
    }
} 