package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.chat.ChatMessage
import com.ssafy.lanterns.ui.screens.chat.state.PublicChatUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Random
import javax.inject.Inject

@HiltViewModel
class PublicChatScreenViewModel @Inject constructor() : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<PublicChatUiState>(PublicChatUiState())
    val uiState: StateFlow<PublicChatUiState> = _uiState.asStateFlow()
    
    private val random = Random()
    
    /**
     * 메시지 추가 함수
     */
    fun addMessage(message: ChatMessage) {
        _messages.value = listOf(message) + _messages.value
        
        // UI 상태 업데이트
        _uiState.update { state ->
            state.copy(
                messages = listOf(message) + state.messages
            )
        }
    }
    
    /**
     * 더 많은 메시지 로드
     */
    fun loadMoreMessages() {
        viewModelScope.launch {
            _uiState.update { state: PublicChatUiState ->
                state.copy(isLoadingMore = true)
            }
            
            // 더 많은 메시지가 없음을 표시
            _uiState.update { state: PublicChatUiState ->
                state.copy(
                    isLoadingMore = false,
                    hasMoreMessages = false
                )
            }
        }
    }

    /**
     * 초기 메시지 설정
     */
    fun initializeDefaultMessages() {
        if (_uiState.value.messages.isEmpty()) {
            val initialMessage = ChatMessage(
                id = 1,
                sender = "시스템",
                text = "모두의 광장에 오신 것을 환영합니다. 주변 사람들과 자유롭게 대화해보세요!",
                time = System.currentTimeMillis() - 3600000,
                isMe = false,
                senderProfileId = -1, // -1은 확성기 아이콘을 사용함을 나타냄
                isPublic = true
            )
            
            // UI 상태 업데이트
            _uiState.update { state: PublicChatUiState ->
                state.copy(
                    messages = listOf(initialMessage),
                    isLoading = false,
                    userCount = 1 // 사용자 자신
                )
            }
        }
    }
}