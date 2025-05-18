package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.source.local.dao.ChatRoomDao
import com.ssafy.lanterns.data.source.local.dao.MessageDao
import com.ssafy.lanterns.data.source.local.dao.UserDao
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

/**
 * 채팅 리스트 UI 상태를 위한 데이터 클래스
 */
data class ChatListUiState(
    val isLoading: Boolean = false,
    val chatList: List<ChatItem> = emptyList(),
    // 주변 사용자 목록 필드 주석 처리
    val nearbyUsers: List<NearbyUser> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 채팅 리스트 화면을 위한 ViewModel
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState(isLoading = true))
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null

    init {
        loadCurrentUser()
    }

    /**
     * 현재 사용자 정보 로드 및 채팅방 목록 조회
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                // 현재 사용자 정보 확인
                currentUser = userRepository.getCurrentUser()
                
                // 사용자 정보가 없으면 오류 메시지 표시
                if (currentUser == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "로그인된 사용자 정보가 없습니다. 로그인을 진행해주세요."
                        )
                    }
                    return@launch
                }
                
                // 채팅방 및 주변 사용자 로드
                loadChatRooms()
                // 주변 사용자 생성 함수 호출 주석 처리
                generateNearbyUsers() // 주변 사용자 데이터 생성 (실제로는 BLE 스캐닝으로 대체)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "사용자 정보 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 채팅방 목록 불러오기
     */
    private fun loadChatRooms() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // 현재 사용자의 채팅방 목록 조회
                val userId = currentUser?.userId ?: return@launch
                val chatRooms = chatRoomDao.getChatRoomsByParticipantId(userId)
                
                // 채팅방별 마지막 메시지 및 상대방 정보 조회
                val chatItems = mutableListOf<ChatItem>()
                
                for (chatRoom in chatRooms) {
                    val participantId = chatRoom.participantId
                    val participant = userDao.getUserById(participantId)
                    
                    // 해당 채팅방의 최신 메시지 가져오기
                    // Flow<List<Message>>를 바로 사용할 수 없어 최신 메시지는 다른 방식으로 구현
                    val lastMessage = messageDao.getLatestMessageForChatRoom(chatRoom.chatRoomId.toString())
                    
                    // 임의의 거리 설정 (실제로는 BLE를 통해 거리 계산)
                    val distance = Random.nextFloat() * 300
                    
                    // 채팅 아이템 생성
                    if (participant != null) {
                        chatItems.add(
                            ChatItem(
                                id = participantId.toInt(),  // 상대방 ID로 설정
                                name = participant.nickname,
                                lastMessage = lastMessage?.content ?: "대화를 시작해보세요",
                                time = formatDateTime(lastMessage?.timestamp?.let { timestamp -> 
                                    LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC) 
                                } ?: LocalDateTime.now()),
                                unread = false, // 읽음 상태 관리 기능 추가 필요
                                distance = distance
                            )
                        )
                    }
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chatList = chatItems,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "채팅방 목록 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 주변 사용자 스캔 (실제 BLE 스캔 로직이 들어갈 위치)
     */
    private fun generateNearbyUsers() {
        viewModelScope.launch {
            try {
                // 실제 구현에서는 BLE 스캔 로직과 검색된 사용자 데이터 처리 로직이 들어갈 위치
                // 더미 데이터 대신 실제 BLE 스캔 결과 사용
                
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = "주변 사용자 정보 로드 오류: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 날짜/시간 형식 변환 함수
     */
    private fun formatDateTime(dateTime: LocalDateTime): String {
        val now = LocalDateTime.now()
        return when {
            dateTime.toLocalDate() == now.toLocalDate() -> {
                // 오늘이면 시간만 표시
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            dateTime.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                // 어제면 "어제" 표시
                "어제"
            }
            dateTime.year == now.year -> {
                // 올해면 월/일 표시
                dateTime.format(DateTimeFormatter.ofPattern("MM/dd"))
            }
            else -> {
                // 작년 이전이면 년/월/일 표시
                dateTime.format(DateTimeFormatter.ofPattern("yy/MM/dd"))
            }
        }
    }
    
    /**
     * 채팅방 새로고침
     */
    fun refreshChatRooms() {
        loadChatRooms()
    }
    
    /**
     * 새 채팅방 생성
     */
    fun createChatRoom(participantId: Long) {
        viewModelScope.launch {
            try {
                val currentUserId = currentUser?.userId ?: return@launch
                
                // 이미 존재하는 채팅방인지 확인
                val existingRooms = chatRoomDao.getChatRoomsByParticipantId(currentUserId)
                val alreadyExists = existingRooms.any { it.participantId == participantId }
                
                if (!alreadyExists) {
                    // 상대방 정보 가져오기
                    val participant = userDao.getUserById(participantId)
                    
                    // 새 채팅방 생성
                    val chatRoom = ChatRoom(
                        chatRoomId = System.currentTimeMillis().toString(),
                        participantId = participantId,
                        participantNickname = participant?.nickname ?: "사용자",
                        participantProfileImageNumber = participant?.selectedProfileImageNumber ?: 1,
                        updatedAt = LocalDateTime.now()
                    )
                    
                    chatRoomDao.insertChatRoom(chatRoom)
                    
                    // 채팅방 목록 다시 로드
                    loadChatRooms()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "채팅방 생성 중 오류: ${e.message}")
                }
            }
        }
    }
}