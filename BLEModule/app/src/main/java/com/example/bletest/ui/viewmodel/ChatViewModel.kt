package com.example.bletest.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 채팅 화면을 위한 ViewModel
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val bleRepository: BleRepository
) : ViewModel() {

    // 현재 메시지 텍스트
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    // 현재 타겟 ID
    private val _targetId = MutableStateFlow("")
    val targetId: StateFlow<String> = _targetId.asStateFlow()

    // 메시지 목록
    private val _messages = MutableStateFlow<List<MessageData>>(emptyList())
    val messages: StateFlow<List<MessageData>> = _messages.asStateFlow()

    // 이벤트 (토스트 메시지 등)
    private val _uiEvent = MutableSharedFlow<ChatUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    // 내 장치 ID
    private val myDeviceId = bleRepository.getDeviceId()

    init {
        // 메시지 수집
        viewModelScope.launch {
            bleRepository.messages.collect { newMessages ->
                _messages.value = newMessages
            }
        }
    }

    /**
     * 메시지 텍스트 업데이트
     */
    fun updateMessageText(text: String) {
        _messageText.value = text
    }

    /**
     * 타겟 ID 업데이트
     */
    fun updateTargetId(id: String) {
        _targetId.value = id
    }

    /**
     * 메시지 전송
     */
    fun sendMessage() {
        val text = _messageText.value.trim()
        val target = _targetId.value.trim()

        if (text.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.emit(ChatUiEvent.ShowToast("메시지를 입력하세요"))
            }
            return
        }

        if (target.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.emit(ChatUiEvent.ShowToast("대상 ID를 입력하세요"))
            }
            return
        }
        
        // 자기 자신에게 메시지를 보내려는 경우 방지
        if (target == myDeviceId) {
            viewModelScope.launch {
                _uiEvent.emit(ChatUiEvent.ShowToast("자신에게는 메시지를 보낼 수 없습니다"))
            }
            return
        }

        // 전송 전 로그
        Log.d("ChatViewModel", "메시지 전송 시도: target=$target, content=$text, myId=${myDeviceId}")

        viewModelScope.launch {
            try {
                // 전송 시도
                Log.d("ChatViewModel", "BleRepository.sendMessage() 호출")
                val success = bleRepository.sendMessage(target, MessageType.TEXT, text)
                
                Log.d("ChatViewModel", "메시지 전송 결과: $success")
                
                if (success) {
                    _messageText.value = "" // 메시지 전송 성공 시 입력창 초기화
                    
                    // 성공적으로 보낸 메시지를 로컬 메시지 목록에 추가
                    val newMessage = MessageData(
                        sourceId = myDeviceId,
                        targetId = target,
                        content = text,
                        messageType = MessageType.TEXT,
                        isOutgoing = true,
                        isSent = true,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    // 현재 메시지 목록에 직접 추가
                    _messages.update { currentList -> currentList + newMessage }
                    
                    _uiEvent.emit(ChatUiEvent.ShowToast("메시지 전송 성공"))
                } else {
                    Log.e("ChatViewModel", "메시지 전송 실패: target=$target")
                    _uiEvent.emit(ChatUiEvent.ShowToast("메시지 전송 실패 - 상대방과 연결이 원활하지 않습니다"))
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "메시지 전송 오류", e)
                _uiEvent.emit(ChatUiEvent.ShowToast("오류: ${e.message ?: "알 수 없는 오류"}"))
            }
        }
    }

    /**
     * 메시지가 내가 보낸 것인지 확인
     */
    fun isOwnMessage(message: MessageData): Boolean {
        return message.sourceId == myDeviceId || message.isOutgoing
    }
}

/**
 * 채팅 UI 이벤트
 */
sealed class ChatUiEvent {
    data class ShowToast(val message: String) : ChatUiEvent()
} 