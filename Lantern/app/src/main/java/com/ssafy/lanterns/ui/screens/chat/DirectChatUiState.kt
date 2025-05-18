package com.ssafy.lanterns.ui.screens.chat

import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.User

data class DirectChatUiState(
    val chatRoomId: String? = null,
    val currentUser: User? = null,
    val participantUser: User? = null, // The other user in the chat
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isParticipantNearby: Boolean = false, // Placeholder for participant presence
    val errorMessage: String? = null,
    val isLoading: Boolean = true // Initially true until chat room and user data are loaded
) 