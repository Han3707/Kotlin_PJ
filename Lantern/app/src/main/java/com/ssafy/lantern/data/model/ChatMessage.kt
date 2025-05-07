package com.ssafy.lantern.data.model

import java.util.Date

/**
 * BLE Mesh 네트워크를 통해 전송되는 채팅 메시지 모델
 *
 * @param fromAddress 발신자 유니캐스트 주소
 * @param toAddress 수신자 유니캐스트 주소 (0xFFFF는 브로드캐스트 - 공용 채팅)
 * @param timestamp 메시지 전송 시간 (밀리초)
 * @param content 메시지 내용
 * @param senderName 발신자 이름 (선택사항)
 */
data class ChatMessage(
    val fromAddress: Int,
    val toAddress: Int,      // 0xFFFF: 브로드캐스트(공용 채팅)
    val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val senderName: String = ""  // 선택적 필드: 사용자 이름 표시용
) {
    val isPublic: Boolean
        get() = toAddress == 0xFFFF
        
    val dateTime: Date
        get() = Date(timestamp)
} 