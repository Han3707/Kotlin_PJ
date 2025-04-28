package com.example.bletest.utils

import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.MessageData
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * BLE 메시지 파싱 및 생성 유틸리티
 */
object MessageParser {
    private const val HEADER_SIZE = 16
    private const val UUID_SIZE = 16
    private const val MAX_CONTENT_SIZE = 100
    
    /**
     * 메시지 파싱
     * 
     * @param data 파싱할 바이트 배열
     * @return 파싱된 메시지 객체 (파싱 실패 시 null)
     */
    fun parseMessage(data: ByteArray?): MessageData? {
        if (data == null || data.size < HEADER_SIZE) {
            return null
        }
        
        try {
            val buffer = ByteBuffer.wrap(data)
            
            // 메시지 타입 (첫 1바이트)
            val messageTypeValue = buffer.get().toInt()
            val messageType = MessageType.fromValue(messageTypeValue)
            
            // 메시지 ID (16바이트 UUID)
            val messageIdBytes = ByteArray(UUID_SIZE)
            buffer.get(messageIdBytes)
            val messageId = UUID.nameUUIDFromBytes(messageIdBytes).toString()
            
            // 소스 ID (16바이트)
            val sourceIdBytes = ByteArray(UUID_SIZE)
            buffer.get(sourceIdBytes)
            val sourceId = UUID.nameUUIDFromBytes(sourceIdBytes).toString()
            
            // 타겟 ID (16바이트) - 브로드캐스트면 0으로 채워진 바이트 배열
            val targetIdBytes = ByteArray(UUID_SIZE)
            buffer.get(targetIdBytes)
            
            // 모두 0인지 체크하여 브로드캐스트 여부 결정
            val isAllZero = targetIdBytes.all { it == 0.toByte() }
            val targetId = if (isAllZero) null else UUID.nameUUIDFromBytes(targetIdBytes).toString()
            
            // 콘텐츠 (나머지 바이트들)
            val contentLength = buffer.int
            
            // 유효성 검사
            if (contentLength < 0 || contentLength > MAX_CONTENT_SIZE) {
                return null
            }
            
            val contentBytes = ByteArray(contentLength)
            buffer.get(contentBytes)
            
            val content = String(contentBytes, StandardCharsets.UTF_8)
            
            return MessageData(
                messageId = messageId,
                messageType = messageType,
                sourceId = sourceId,
                targetId = targetId,
                content = content
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 메시지 생성
     * 
     * @param sourceId 소스 ID
     * @param targetId 타겟 ID (null이면 브로드캐스트)
     * @param messageType 메시지 타입
     * @param content 내용
     * @return 생성된 바이트 배열 (생성 실패 시 null)
     */
    fun prepareMessage(
        sourceId: String,
        targetId: String? = null,
        messageType: Int,
        content: String
    ): ByteArray? {
        if (content.length > MAX_CONTENT_SIZE) {
            return null
        }
        
        try {
            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            val totalSize = HEADER_SIZE + UUID_SIZE * 2 + 4 + contentBytes.size
            
            val buffer = ByteBuffer.allocate(totalSize)
            
            // 메시지 타입
            buffer.put(messageType.toByte())
            
            // 메시지 ID (랜덤 UUID)
            val messageIdBytes = UUID.randomUUID().toString().toByteArray()
            buffer.put(messageIdBytes, 0, UUID_SIZE)
            
            // 소스 ID
            val sourceIdBytes = sourceId.toByteArray()
            if (sourceIdBytes.size >= UUID_SIZE) {
                buffer.put(sourceIdBytes, 0, UUID_SIZE)
            } else {
                buffer.put(sourceIdBytes)
                // 나머지 부분 0으로 채움
                for (i in sourceIdBytes.size until UUID_SIZE) {
                    buffer.put(0)
                }
            }
            
            // 타겟 ID (null이면 0으로 채움)
            if (targetId != null) {
                val targetIdBytes = targetId.toByteArray()
                if (targetIdBytes.size >= UUID_SIZE) {
                    buffer.put(targetIdBytes, 0, UUID_SIZE)
                } else {
                    buffer.put(targetIdBytes)
                    // 나머지 부분 0으로 채움
                    for (i in targetIdBytes.size until UUID_SIZE) {
                        buffer.put(0)
                    }
                }
            } else {
                // 브로드캐스트: 0으로 채움
                for (i in 0 until UUID_SIZE) {
                    buffer.put(0)
                }
            }
            
            // 콘텐츠 길이
            buffer.putInt(contentBytes.size)
            
            // 콘텐츠
            buffer.put(contentBytes)
            
            return buffer.array()
        } catch (e: Exception) {
            return null
        }
    }
} 