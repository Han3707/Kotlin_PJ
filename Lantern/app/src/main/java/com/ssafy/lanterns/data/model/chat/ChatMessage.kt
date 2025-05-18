package com.ssafy.lanterns.data.model.chat

/**
 * 채팅 메시지 모델 클래스 - BLE 및 채팅 화면에서 공통으로 사용
 * @param id 메시지 ID
 * @param sender 발신자 이름
 * @param text 메시지 내용
 * @param time 전송 시간 (밀리초 타임스탬프)
 * @param isMe 내가 보낸 메시지 여부
 * @param senderProfileId 발신자 프로필 ID
 * @param rssi 수신 신호 강도 (BLE)
 * @param isPublic 공개 채팅 여부
 */
data class ChatMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val time: Long, // 밀리초 타임스탬프
    val isMe: Boolean = false,
    val senderProfileId: Int? = null,
    val rssi: Int = -1,
    val isPublic: Boolean = false
) 