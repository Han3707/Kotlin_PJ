package com.example.bletest.data.model

/**
 * 서비스의 상태를 나타내는 열거형
 */
enum class ServiceState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RUNNING,
    ERROR;
    
    /**
     * 이 ServiceState를 ConnectionState로 변환
     * 
     * @return 상응하는 ConnectionState
     */
    fun toConnectionState(): ConnectionState {
        return ConnectionState.fromServiceState(this)
    }
    
    companion object {
        /**
         * ConnectionState에서 ServiceState 생성
         * 
         * @param state 변환할 ConnectionState
         * @return 상응하는 ServiceState
         */
        fun fromConnectionState(state: ConnectionState): ServiceState {
            return when (state) {
                ConnectionState.DISCONNECTED -> DISCONNECTED
                ConnectionState.CONNECTING -> CONNECTING
                ConnectionState.CONNECTED -> CONNECTED
                ConnectionState.READY -> RUNNING
                ConnectionState.DISCONNECTING -> DISCONNECTED
                ConnectionState.ERROR -> ERROR
            }
        }
    }
} 