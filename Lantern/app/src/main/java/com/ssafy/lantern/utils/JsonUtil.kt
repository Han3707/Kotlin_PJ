package com.ssafy.lantern.utils

import android.util.Log
import com.google.android.play.core.integrity.g
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.ssafy.lantern.data.model.ChatMessage
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

/**
 * JSON 변환 유틸리티 클래스
 */
object JsonUtil {
    public const val TAG = "JsonUtil"
    public val gson = Gson()
    
    /**
     * 채팅 메시지 객체를 JSON 문자열로 변환
     */
    fun toJson(message: Any): String {
        return try {
            val json = gson.toJson(message)
            Log.d(TAG, "객체를 JSON으로 변환 성공: ${json.take(50)}${if (json.length > 50) "..." else ""}")
            json
        } catch (e: Exception) {
            Log.e(TAG, "객체를 JSON으로 변환 중 오류 발생", e)
            // 기본 정보만 포함한 간단한 JSON 반환
            "{\"error\":\"변환 오류\",\"message\":\"${e.message}\"}"
        }
    }
    
    /**
     * JSON 문자열을 채팅 메시지 객체로 변환
     */
    fun fromJson(json: String): ChatMessage? {
        return try {
            val message = gson.fromJson(json, ChatMessage::class.java)
            Log.d(TAG, "JSON을 객체로 변환 성공: $message")
            message
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON 구문 오류: $json", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "JSON을 객체로 변환 중 오류 발생", e)
            null
        }
    }
    
    /**
     * JSON 문자열을 지정된 타입의 객체로 변환 (제네릭 사용)
     */
    inline fun <reified T> fromJsonGeneric(json: String): T? {
        return try {
            val type: Type = object : TypeToken<T>() {}.type
            val obj = gson.fromJson<T>(json, type)
            Log.d(TAG, "JSON을 ${T::class.java.simpleName}으로 변환 성공: $obj")
            obj
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON 구문 오류 (${T::class.java.simpleName}): $json", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "JSON을 ${T::class.java.simpleName}으로 변환 중 오류 발생", e)
            null
        }
    }
    
    /**
     * JSON 문자열을 바이트 배열로 변환
     */
    fun toBytes(message: Any): ByteArray {
        val json = toJson(message)
        return try {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            Log.d(TAG, "JSON을 바이트 배열로 변환 성공: ${bytes.size} 바이트")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "JSON을 바이트 배열로 변환 중 오류 발생", e)
            ByteArray(0)
        }
    }
    
    /**
     * 바이트 배열을 채팅 메시지 객체로 변환
     */
    fun fromBytes(bytes: ByteArray): ChatMessage? {
        return try {
            val json = String(bytes, StandardCharsets.UTF_8)
            Log.d(TAG, "바이트 배열을 JSON으로 변환: ${json.take(50)}${if (json.length > 50) "..." else ""}")
            fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "바이트 배열을 JSON으로 변환 중 오류 발생", e)
            null
        }
    }
    
    /**
     * 바이트 배열을 지정된 타입의 객체로 변환 (제네릭 사용)
     */
    inline fun <reified T> fromBytesGeneric(bytes: ByteArray): T? {
        return try {
            val json = String(bytes, StandardCharsets.UTF_8)
            Log.d(TAG, "바이트 배열을 JSON으로 변환 (Generic): ${json.take(50)}${if (json.length > 50) "..." else ""}")
            fromJsonGeneric<T>(json)
        } catch (e: Exception) {
            Log.e(TAG, "바이트 배열을 JSON (Generic)으로 변환 중 오류 발생", e)
            null
        }
    }
    
    /**
     * 바이트 배열을 JSON 문자열로 변환
     */
    fun bytesToString(bytes: ByteArray): String {
        return try {
            val result = String(bytes, StandardCharsets.UTF_8)
            Log.d(TAG, "바이트 배열을 문자열로 변환 성공: ${result.take(50)}${if (result.length > 50) "..." else ""}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "바이트 배열을 문자열로 변환 중 오류 발생", e)
            "{\"error\":\"변환 오류\"}"
        }
    }
    
    /**
     * 바이트 배열을 채팅 메시지 객체로 안전하게 변환
     * fromBytes의 별칭으로 기존 코드와의 호환성을 위해 제공
     */
    fun fromBytesSafe(bytes: ByteArray): ChatMessage? {
        return fromBytes(bytes)
    }
} 