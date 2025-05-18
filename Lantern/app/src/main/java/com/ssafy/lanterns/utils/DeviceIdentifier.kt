package com.ssafy.lanterns.utils

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * 기기 ID 생성 및 관리 유틸리티 클래스
 * - 앱 설치 시 고유 ID 생성 및 저장
 * - 안정적인 기기 식별을 위한 기능 제공
 */
object DeviceIdentifier {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    
    /**
     * 기기 고유 ID 조회 또는 생성
     * - 앱 설치 시 생성한 ID를 SharedPreferences에 저장하고 재사용
     */
    fun getDeviceId(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDeviceId = sharedPrefs.getString(KEY_DEVICE_ID, null)
        
        // 저장된 ID가 있으면 그대로 사용
        if (!savedDeviceId.isNullOrEmpty()) {
            return savedDeviceId
        }
        
        // 새 ID 생성 및 저장
        val newDeviceId = generateUniqueDeviceId(context)
        sharedPrefs.edit().putString(KEY_DEVICE_ID, newDeviceId).apply()
        
        return newDeviceId
    }
    
    /**
     * 고유 기기 ID 생성
     * - 기기 하드웨어 정보와 앱 설치 시간을 조합하여 고유 ID 생성
     */
    private fun generateUniqueDeviceId(context: Context): String {
        val deviceInfo = StringBuilder()
        
        // 기기 정보 조합
        deviceInfo.append(Build.BOARD)
        deviceInfo.append(Build.BRAND)
        deviceInfo.append(Build.DEVICE)
        deviceInfo.append(Build.HARDWARE)
        deviceInfo.append(Build.MANUFACTURER)
        deviceInfo.append(Build.MODEL)
        deviceInfo.append(Build.PRODUCT)
        
        // 앱 설치 시간 추가
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            deviceInfo.append(packageInfo.firstInstallTime)
        } catch (e: Exception) {
            deviceInfo.append(System.currentTimeMillis())
        }
        
        // UUID 생성을 위한 해시화
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(deviceInfo.toString().toByteArray())
        
        // UUID 형식으로 변환
        return UUID.nameUUIDFromBytes(hash).toString()
    }
} 