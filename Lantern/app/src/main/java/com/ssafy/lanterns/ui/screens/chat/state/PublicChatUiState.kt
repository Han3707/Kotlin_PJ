package com.ssafy.lanterns.ui.screens.chat.state

import com.ssafy.lanterns.data.model.chat.ChatMessage

/**
 * 공개 채팅 UI 상태를 위한 데이터 클래스
 */
data class PublicChatUiState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val userCount: Int = 0,
    val errorMessage: String? = null,
    val connectionStatus: String = "연결 안됨",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isConnecting: Boolean = false
) 