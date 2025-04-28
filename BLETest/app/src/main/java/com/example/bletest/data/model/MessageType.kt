package com.example.bletest.data.model

/**
 * 메시지 타입 열거형
 */
enum class MessageType(val value: Int) {
    UNKNOWN(-1),
    TEXT(0),
    COMMAND(1),
    STATUS(2),
    ACK(3),
    DATA_REQUEST(4),
    DATA_RESPONSE(5),
    ERROR(6);
    
    companion object {
        fun fromValue(value: Int): MessageType {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
} 