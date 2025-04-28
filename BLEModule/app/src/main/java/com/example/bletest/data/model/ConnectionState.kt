package com.example.bletest.data.model

/**
 * BLE 장치 연결 상태를 나타내는 열거형
 * 이전에 ConnectionStatus와 중복되는 내용을 통합 -> 이제 관련 없음
 */
enum class ConnectionState {
    DISCONNECTED,    // 연결이 끊어진 상태
    CONNECTING,      // 연결 시도 중인 상태
    CONNECTED,       // 연결된 상태
    DISCONNECTING,   // 연결 해제 중인 상태
    READY,           // 연결되고 서비스 검색까지 완료된 상태
    ERROR;           // 연결 오류 상태
    
    companion object {
        /**
         * GATT 상태 코드로부터 ConnectionState를 반환
         * 
         * @param status GATT 상태 코드
         * @return 해당하는 ConnectionState
         */
        fun fromGattStatus(status: Int): ConnectionState {
            return when (status) {
                0 -> CONNECTED
                133 -> DISCONNECTED
                else -> ERROR
            }
        }
        
        /**
         * ServiceState를 ConnectionState로 변환
         * 
         * @param serviceState 변환할 ServiceState
         * @return 해당하는 ConnectionState
         */
        fun fromServiceState(serviceState: ServiceState): ConnectionState {
            return when (serviceState) {
                ServiceState.DISCONNECTED -> DISCONNECTED
                ServiceState.CONNECTING -> CONNECTING
                ServiceState.CONNECTED -> CONNECTED
                ServiceState.RUNNING -> READY
                ServiceState.ERROR -> ERROR
            }
        }
    }
} 