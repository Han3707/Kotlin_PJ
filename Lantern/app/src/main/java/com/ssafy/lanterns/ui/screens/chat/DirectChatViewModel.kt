package com.ssafy.lanterns.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.config.ChatConstants
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.MessageStatus
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.ChatRepository
import com.ssafy.lanterns.data.repository.UserRepository
import com.ssafy.lanterns.service.ble.advertiser.ChatAdvertiserManager
import com.ssafy.lanterns.service.ble.scanner.ChatScannerManager
import com.ssafy.lanterns.service.ble.scanner.ParsedChatMessage
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.util.toShort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class DirectChatViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val savedStateHandle: SavedStateHandle,
    application: Application // Application Context 주입
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectChatUiState(isLoading = true))
    val uiState: StateFlow<DirectChatUiState> = _uiState.asStateFlow()

    private val chatAdvertiserManager: ChatAdvertiserManager = ChatAdvertiserManager(application)
    private val chatScannerManager: ChatScannerManager = ChatScannerManager(application)

    private val pendingMessageChunks = ConcurrentHashMap<Long, MutableList<ParsedChatMessage>>() // Key: messageId
    
    // 재전송 중인 메시지 ID 집합
    private val retryingMessageIds = mutableSetOf<Long>()

    init {
        Log.d("DirectChatVM", "ViewModel initialized")
        loadCurrentUserAndProceed() // currentUser 로드 후 다음 단계 진행

        // ChatScannerManager로부터 메시지 수신 시작
        chatScannerManager.chatMessageReceived
            .onEach { parsedMessage -> handleReceivedMessage(parsedMessage) }
            .catch { e ->
                Log.e("DirectChatVM", "Error collecting from chatMessageReceived flow: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "메시지 수신 중 오류가 발생했습니다.") }
            }
            .launchIn(viewModelScope)
    }

    private fun loadCurrentUserAndProceed() {
        viewModelScope.launch {
            userRepository.getUserFlow().collectLatest { user ->
                if (user != null) {
                    _uiState.update { it.copy(currentUser = user) }
                    chatScannerManager.setCurrentUserId(user.userId) 
                    Log.d("DirectChatVM", "Current user loaded: ID=${user.userId}, Nickname=${user.nickname}. Scanner UserID set.")

                    chatScannerManager.startScanning()
                    Log.d("DirectChatVM", "ChatScanner started.")

                    val passedUserIdString = savedStateHandle.get<String>(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
                    if (passedUserIdString == null) {
                        Log.e("DirectChatVM", "UserId not found in SavedStateHandle.")
                        _uiState.update { it.copy(isLoading = false, errorMessage = "채팅방 정보를 전달받지 못했습니다.") }
                        return@collectLatest
                    }
                    
                    Log.d("DirectChatVM", "UserId string from NavArgs: $passedUserIdString")
                    val targetUserIdLong = passedUserIdString.toLongOrNull()
                    
                    if (targetUserIdLong == null) {
                        Log.e("DirectChatVM", "Invalid UserId format from NavArgs: $passedUserIdString")
                        _uiState.update { it.copy(isLoading = false, errorMessage = "잘못된 사용자 ID 형식입니다.") }
                        return@collectLatest
                    }
                    
                    // 사용자 자신과의 채팅방은 만들지 않도록 처리 (선택적)
                    if (user.userId == targetUserIdLong) {
                        Log.w("DirectChatVM", "Attempting to chat with oneself. UserID: ${user.userId}")
                        _uiState.update { it.copy(isLoading = false, errorMessage = "자기 자신과는 채팅할 수 없습니다.") }
                        // 이전 화면으로 돌아가거나 다른 UI 처리
                        return@collectLatest
                    }

                    val chatRoomId = "${Math.min(user.userId, targetUserIdLong)}_${Math.max(user.userId, targetUserIdLong)}"
                    _uiState.update { it.copy(chatRoomId = chatRoomId) }
                    loadChatRoomDetailsAndMessages(chatRoomId, user, targetUserIdLong) // targetUserIdLong 전달
                    
                } else {
                    Log.e("DirectChatVM", "Failed to load current user.")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "현재 사용자 정보를 가져올 수 없습니다.") }
                }
            }
        }
    }

    private fun loadChatRoomDetailsAndMessages(chatRoomId: String, currentUser: User, targetUserId: Long) {
        viewModelScope.launch {
            try {
                // ChatRoom을 가져오거나 생성하는 로직 (getOrCreateChatRoom)
                val chatRoom = chatRepository.getOrCreateChatRoom(chatRoomId, currentUser.userId, targetUserId)
                Log.d("DirectChatVM", "ChatRoom loaded/created: ID=${chatRoom.id}, Participant=${chatRoom.participantId}")

                // 상대방 정보 로드 (chatRoom.participantId 사용)
                val participant = userRepository.getUserById(chatRoom.participantId)
                if (participant == null) {
                    Log.e("DirectChatVM", "Participant user not found for ID: ${chatRoom.participantId}")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "상대방 사용자 정보를 찾을 수 없습니다.") }
                    return@launch
                }
                Log.d("DirectChatVM", "Participant loaded: ID=${participant.userId}, Nickname=${participant.nickname}")
                _uiState.update { it.copy(participantUser = participant, isLoading = false) } // 로딩 완료

                observeMessages(chatRoomId)

            } catch (e: Exception) {
                Log.e("DirectChatVM", "Error loading chat room details for $chatRoomId: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "채팅방 상세 정보 로드 중 오류 발생.") }
            }
        }
    }

    private fun observeMessages(chatRoomId: String) {
        chatRepository.getMessagesForChatRoom(chatRoomId)
            .onEach { messages ->
                Log.d("DirectChatVM", "Messages updated for $chatRoomId. Count: ${messages.size}")
                _uiState.update {
                    it.copy(
                        messages = messages.sortedBy { msg -> msg.timestamp }, // 시간순 정렬
                        isLoading = false // 메시지 로드/업데이트 시 로딩 완료
                    )
                }
            }
            .catch { e ->
                Log.e("DirectChatVM", "Error observing messages for $chatRoomId: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "메시지 업데이트 중 오류 발생.") }
            }
            .launchIn(viewModelScope)
    }


    private fun handleReceivedMessage(parsedMessage: ParsedChatMessage) {
        val currentUser = _uiState.value.currentUser ?: return Unit.also { Log.w("DirectChatVM", "handleReceivedMessage: currentUser is null") }
        val participant = _uiState.value.participantUser ?: return Unit.also { Log.w("DirectChatVM", "handleReceivedMessage: participantUser is null") }
        val currentChatRoomId = _uiState.value.chatRoomId ?: return Unit.also { Log.w("DirectChatVM", "handleReceivedMessage: chatRoomId is null") }

        if (parsedMessage.recipientId != currentUser.userId) {
            return
        }

        if (parsedMessage.senderId != participant.userId) {
            Log.d("DirectChatVM", "Message from unexpected sender ${parsedMessage.senderId}, current participant is ${participant.userId}")
            return
        }
        
        if (parsedMessage.messageType != ChatConstants.MESSAGE_TYPE_DM_CHUNK) {
            Log.w("DirectChatVM", "Received non-DM_CHUNK message type: ${parsedMessage.messageType}")
            return
        }

        Log.d("DirectChatVM", "Handling chunk: MsgID=${parsedMessage.messageId}, Chunk ${parsedMessage.chunkNumber + 1}/${parsedMessage.totalChunks} from ${parsedMessage.senderId}")

        val chunks = pendingMessageChunks.getOrPut(parsedMessage.messageId) {
            mutableListOf()
        }
        if (chunks.any { it.chunkNumber == parsedMessage.chunkNumber }) {
             Log.w("DirectChatVM", "Duplicate chunk received and ignored: MsgID=${parsedMessage.messageId}, ChunkNo=${parsedMessage.chunkNumber}")
            return
        }
        chunks.add(parsedMessage)

        if (chunks.size == parsedMessage.totalChunks.toInt()) {
            chunks.sortBy { it.chunkNumber } 
            val completeMessageContent = chunks.joinToString("") {
                it.chunkData.toString(StandardCharsets.UTF_8)
            }
            pendingMessageChunks.remove(parsedMessage.messageId)

            Log.i("DirectChatVM", "Complete message assembled: ID=${parsedMessage.messageId}, From=${parsedMessage.senderId}, Content='${completeMessageContent}'")

            val newMessage = Message(
                chatRoomId = currentChatRoomId,
                senderId = parsedMessage.senderId.toLong(),
                receiverId = currentUser.userId.toLong(),
                content = completeMessageContent,
                timestamp = parsedMessage.messageId, 
                isSentByMe = false,
                status = MessageStatus.RECEIVED,
                messageType = "TEXT"
            )
            viewModelScope.launch {
                chatRepository.saveMessage(newMessage)
                Log.d("DirectChatVM", "Assembled message saved to DB. MsgID: ${parsedMessage.messageId}")
            }
        } else {
            Log.d("DirectChatVM", "Chunk stored for MsgID=${parsedMessage.messageId}. (${chunks.size}/${parsedMessage.totalChunks})")
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun sendMessage() {
        val messageText = _uiState.value.inputText.trim()
        if (messageText.isBlank()) return

        val currentUser = _uiState.value.currentUser ?: return Unit.also { handleError("메시지 전송 실패: 사용자 정보 없음") }
        val participant = _uiState.value.participantUser ?: return Unit.also { handleError("메시지 전송 실패: 상대방 정보 없음") }
        val currentChatRoomId = _uiState.value.chatRoomId ?: return Unit.also { handleError("메시지 전송 실패: 채팅방 ID 없음") }

        viewModelScope.launch {
            try {
                val messageBytes = messageText.toByteArray(StandardCharsets.UTF_8)
                val chunkSize = if (ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH > 0) ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH else 1
                val numChunks = (messageBytes.size + chunkSize - 1) / chunkSize

                val messageTimestampId = System.currentTimeMillis()

                val pendingMessage = Message(
                    chatRoomId = currentChatRoomId,
                    senderId = currentUser.userId.toLong(),
                    receiverId = participant.userId.toLong(),
                    content = messageText,
                    timestamp = messageTimestampId, // Advertising할 때 사용할 임시 ID 겸 타임스탬프
                    isSentByMe = true,
                    status = MessageStatus.PENDING,
                    messageType = "TEXT"
                    // messageId는 Room이 자동 생성하므로 여기서 지정하지 않음
                )
                val savedMessageId = chatRepository.saveMessage(pendingMessage) // Long 타입의 ID를 받음
                Log.d("DirectChatVM", "Message saved as PENDING. DB ID: $savedMessageId, Ad MsgID: $messageTimestampId")

                _uiState.update { it.copy(inputText = "") } 

                Log.d("DirectChatVM", "Sending message: '$messageText'. Chunks: $numChunks, ChunkSize: $chunkSize, MsgID: $messageTimestampId")

                for (i in 0 until numChunks) {
                    val start = i * chunkSize
                    val end = kotlin.math.min(start + chunkSize, messageBytes.size)
                    val chunkData = messageBytes.sliceArray(start until end)

                    chatAdvertiserManager.startAdvertising(
                        messageType = ChatConstants.MESSAGE_TYPE_DM_CHUNK,
                        recipientId = participant.userId.toShort(),
                        senderId = currentUser.userId.toShort(),
                        chunkNumber = i.toByte(),
                        totalChunks = numChunks.toByte(),
                        messageId = messageTimestampId, // BLE 광고 시에는 임시 ID (타임스탬프) 사용
                        payloadChunk = chunkData
                    )
                }
                
                // DB에 저장된 실제 messageId를 사용하여 상태 업데이트
                chatRepository.updateMessageStatus(savedMessageId, MessageStatus.SENT)
                Log.d("DirectChatVM", "Message status updated to SENT for DB ID: $savedMessageId (Ad MsgID: $messageTimestampId)")

            } catch (e: Exception) {
                Log.e("DirectChatVM", "Error sending message: ${e.message}", e)
                handleError("메시지 전송 중 오류 발생: ${e.message}")
            }
        }
    }
    
    private fun handleError(message: String) {
        Log.e("DirectChatVM", "Error: $message")
        _uiState.update { it.copy(errorMessage = message) }
        viewModelScope.launch {
            delay(3000) // 에러 메시지를 3초간 보여준 후
            if (_uiState.value.errorMessage == message) { // 다른 에러로 덮어쓰여지지 않았는지 확인
                 _uiState.update { it.copy(errorMessage = null) } // 초기화
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatScannerManager.stopScanning()
        chatAdvertiserManager.stopAdvertising()
        pendingMessageChunks.clear()
        Log.d("DirectChatVM", "ViewModel cleared. BLE operations stopped.")
    }

    /**
     * 특정 메시지를 재전송하는 함수
     */
    fun retryMessage(message: Message) {
        // 이미 재전송 시도 중인 메시지는 무시 (중복 요청 방지)
        if (retryingMessageIds.contains(message.messageId)) return
        
        val messageId = message.messageId
        retryingMessageIds.add(messageId)

        // 메시지 상태를 PENDING으로 변경
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.messageId == message.messageId) {
                            msg.copy(status = MessageStatus.PENDING)
                        } else {
                            msg
                        }
                    }
                    state.copy(messages = updatedMessages)
                }
                
                // BLE를 통해 메시지 전송 시도
                val receiver = _uiState.value.participantUser
                if (receiver != null) {
                    val isSuccess = sendViaBle(message.content, receiver.userId.toString())
                    
                    if (isSuccess) {
                        // 성공 시 상태 업데이트
                        updateMessageStatus(message.messageId, MessageStatus.SENT)
                    } else {
                        // 실패 시 상태 업데이트
                        updateMessageStatus(message.messageId, MessageStatus.FAILED)
                    }
                } else {
                    updateMessageStatus(message.messageId, MessageStatus.FAILED)
                    _uiState.update { it.copy(errorMessage = "메시지를 전송할 수 없습니다: 수신자 정보가 없습니다.") }
                }
            } catch (e: Exception) {
                updateMessageStatus(message.messageId, MessageStatus.FAILED)
                _uiState.update { it.copy(errorMessage = "메시지 재전송 중 오류: ${e.message}") }
            } finally {
                retryingMessageIds.remove(messageId)
            }
        }
    }
    
    // BLE를 통해 메시지 전송 시도하는 함수
    private suspend fun sendViaBle(content: String, receiverId: String): Boolean {
        try {
            // 여기에 실제 BLE 전송 로직을 구현
            // 성공하면 true, 실패하면 false 반환
            return true
        } catch (e: Exception) {
            Log.e("DirectChatVM", "BLE 전송 중 오류: ${e.message}")
            return false
        }
    }
    
    // 메시지 상태 업데이트 함수
    private suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.messageId == messageId) {
                    msg.copy(status = status)
                } else {
                    msg
                }
            }
            state.copy(messages = updatedMessages)
        }
    }
} 