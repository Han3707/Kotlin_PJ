package com.ssafy.lanterns.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeviceRepository 구현체
 */
@Singleton
class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceRepository {
    
    companion object {
        private const val TAG = "DeviceRepositoryImpl"
    }
    
    // 기기 ID와 정보를 저장하는 맵
    private val deviceMap = mutableMapOf<String, Device>()
    
    override suspend fun registerDevice(
        deviceId: String,
        userId: String,
        deviceName: String,
        deviceModel: String
    ) {
        try {
            // 현재 시간을 마지막 접속 시간으로 설정
            val now = System.currentTimeMillis()
            
            // 장치 정보 생성 및 저장
            val device = Device(
                id = deviceId,
                userId = userId,
                name = deviceName,
                model = deviceModel,
                lastSeen = now
            )
            
            // 메모리에 저장 (실제 구현에서는 DB나 네트워크 API 호출로 대체)
            deviceMap[deviceId] = device
            
            Log.d(TAG, "기기 등록 완료: ID=$deviceId, 사용자=$userId, 모델=$deviceModel")
        } catch (e: Exception) {
            Log.e(TAG, "기기 등록 실패: ${e.message}")
        }
    }
    
    override suspend fun unregisterDevice(deviceId: String) {
        try {
            // 기기 등록 해제 (실제 구현에서는 DB나 네트워크 API 호출로 대체)
            deviceMap.remove(deviceId)
            
            Log.d(TAG, "기기 등록 해제 완료: ID=$deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "기기 등록 해제 실패: ${e.message}")
        }
    }
    
    override suspend fun getUserDevices(userId: String): List<Device> {
        try {
            // 특정 사용자의 모든 기기 목록 조회
            val devices = deviceMap.values.filter { it.userId == userId }
            
            Log.d(TAG, "사용자($userId)의 기기 목록 조회: ${devices.size}개")
            return devices
        } catch (e: Exception) {
            Log.e(TAG, "기기 목록 조회 실패: ${e.message}")
            return emptyList()
        }
    }
} 