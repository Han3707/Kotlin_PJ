package com.example.bletest.data.model

/**
 * BLE 장치 연결 상태를 나타내는 열거형
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    CONNECTION_ERROR;
    
    companion object {
        fun fromGattStatus(status: Int): ConnectionState {
            return when (status) {
                0 -> CONNECTED
                133 -> DISCONNECTED
                else -> CONNECTION_ERROR
            }
        }
    }
} 