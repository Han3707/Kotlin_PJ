package com.example.bletest.utils

import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ParsedMessage
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

/**
 * BLE 메시지 파싱 및 생성을 위한 유틸리티 클래스
 */
object MessageParser {
    private const val FIELD_SEPARATOR = "|"
    private const val PROTOCOL_VERSION = 1
    
    /**
     * 메시지를 준비하여 바이트 배열로 변환
     *
     * @param messageType 메시지 타입
     * @param sourceId 발신자 ID
     * @param targetId 수신자 ID (null인 경우 브로드캐스트)
     * @param content 메시지 내용
     * @param ttl Time-to-live 값 (홉 수 제한)
     * @return 인코딩된 메시지 바이트 배열, 실패 시 null
     */
    fun prepareMessage(
        messageType: Int,
        sourceId: String,
        targetId: String? = null,
        content: String = "",
        ttl: Int = 5
    ): ByteArray? {
        try {
            // 메시지 ID 생성
            val messageId = UUID.randomUUID().toString()
            
            // 필드 조합
            val fields = listOf(
                PROTOCOL_VERSION.toString(),
                messageId,
                sourceId,
                targetId ?: "*", // 타겟이 없으면 브로드캐스트
                messageType.toString(),
                ttl.toString(),
                content
            )
            
            // 메시지 문자열 생성
            val messageString = fields.joinToString(FIELD_SEPARATOR)
            
            // 문자열을 바이트 배열로 변환
            return messageString.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 수신된 바이트 배열을 파싱하여 메시지 객체로 변환
     *
     * @param data 메시지 바이트 배열
     * @return 파싱된 메시지 객체, 파싱 실패 시 null
     */
    fun parseMessage(data: ByteArray): ParsedMessage? {
        try {
            // 바이트 배열을 문자열로 변환
            val messageString = String(data, StandardCharsets.UTF_8)
            
            // 필드 분리
            val fields = messageString.split(FIELD_SEPARATOR)
            
            // 필드 개수 확인
            if (fields.size < 7) {
                return ParsedMessage(error = "Invalid message format")
            }
            
            // 필드 추출
            val protocolVersion = fields[0].toIntOrNull() ?: 0
            val messageId = fields[1]
            val sourceId = fields[2]
            val targetId = fields[3].takeIf { it != "*" } // "*"는 브로드캐스트
            val messageTypeValue = fields[4].toIntOrNull() ?: -1
            val ttl = fields[5].toIntOrNull() ?: 0
            val content = fields[6]
            
            // MessageType 열거형으로 변환
            val messageType = MessageType.fromValue(messageTypeValue)
            
            // 메시지 객체 생성 및 반환
            return ParsedMessage(
                messageId = messageId,
                sourceId = sourceId,
                targetId = targetId,
                messageType = messageType,
                content = content,
                ttl = ttl,
                protocolVersion = protocolVersion
            )
        } catch (e: Exception) {
            return ParsedMessage(error = "Parsing error: ${e.message}")
        }
    }
} 