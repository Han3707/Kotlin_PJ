package com.example.bletest.data.model

/**
 * 연결 상태를 나타내는 열거형
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR;
    
    companion object {
        fun fromGattStatus(status: Int): ConnectionStatus {
            return when (status) {
                0 -> CONNECTED
                133 -> DISCONNECTED
                else -> ERROR
            }
        }
    }
} 