package com.ssafy.lantern.utils

import com.google.gson.Gson
import com.ssafy.lantern.data.model.ChatMessage

/**
 * 채팅 메시지와 바이트 배열 간의 변환을 처리하는 유틸리티 클래스
 */
object JsonUtil {
    private val gson = Gson()
    
    /**
     * ChatMessage 객체를 바이트 배열로 변환
     *
     * @param msg 변환할 ChatMessage 객체
     * @return 직렬화된 바이트 배열
     */
    fun toBytes(msg: ChatMessage): ByteArray =
        gson.toJson(msg).toByteArray(Charsets.UTF_8)

    /**
     * 바이트 배열을 ChatMessage 객체로 변환
     *
     * @param bytes 역직렬화할 바이트 배열
     * @return 변환된 ChatMessage 객체
     * @throws Exception JSON 변환 중 오류 발생 시
     */
    fun fromBytes(bytes: ByteArray): ChatMessage =
        gson.fromJson(bytes.toString(Charsets.UTF_8), ChatMessage::class.java)
        
    /**
     * 바이트 배열을 ChatMessage 객체로 안전하게 변환
     * 예외 발생 시 null 반환
     *
     * @param bytes 역직렬화할 바이트 배열
     * @return 변환된 ChatMessage 객체 또는 null (변환 실패 시)
     */
    fun fromBytesSafe(bytes: ByteArray): ChatMessage? {
        return try {
            fromBytes(bytes)
        } catch (e: Exception) {
            null
        }
    }
} 