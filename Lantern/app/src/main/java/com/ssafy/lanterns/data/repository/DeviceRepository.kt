package com.ssafy.lanterns.data.repository

/**
 * 기기 정보를 관리하는 Repository 인터페이스
 */
interface DeviceRepository {
    /**
     * 기기 등록/업데이트
     * @param deviceId 기기 고유 ID
     * @param userId 사용자 ID
     * @param deviceName 기기 이름
     * @param deviceModel 기기 모델명
     */
    suspend fun registerDevice(
        deviceId: String,
        userId: String,
        deviceName: String,
        deviceModel: String
    )
    
    /**
     * 기기 삭제
     * @param deviceId 삭제할 기기 ID
     */
    suspend fun unregisterDevice(deviceId: String)
    
    /**
     * 사용자의 모든 기기 목록 조회
     * @param userId 사용자 ID
     * @return 사용자의 기기 목록
     */
    suspend fun getUserDevices(userId: String): List<Device>
}

/**
 * 기기 정보 모델 클래스
 */
data class Device(
    val id: String,           // 기기 고유 ID
    val userId: String,       // 사용자 ID
    val name: String,         // 기기 이름
    val model: String,        // 기기 모델
    val lastSeen: Long = 0    // 마지막 접속 시간
) 