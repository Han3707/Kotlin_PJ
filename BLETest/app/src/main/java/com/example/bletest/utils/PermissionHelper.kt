package com.example.bletest.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    /**
     * 특정 권한이 부여되었는지 확인
     * 
     * @param context 권한 확인에 사용할 컨텍스트
     * @param permission 확인할 권한
     * @return 권한이 부여된 경우 true, 아닌 경우 false
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * BLE 메시 서비스에 필요한 모든 권한이 부여되었는지 확인
     * 
     * @param context 권한 확인에 사용할 컨텍스트
     * @return 모든 필수 권한이 부여된 경우 true, 아닌 경우 false
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        // COARSE 권한도 필요하면 추가
        // required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        return required.all { hasPermission(context, it) }
    }
    
    /**
     * Android 12 이상에서 필요한 BLE 권한 목록 반환
     * 
     * @param context 권한 확인에 사용할 컨텍스트
     * @return 필요한 권한 목록
     */
    fun getRequiredPermissions(context: Context): List<String> {
        val requiredPermissions = mutableListOf<String>()

        // Android 12 (S, API 31) 이상 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        // 위치 권한 (스캔에 필요)
        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        
        return requiredPermissions
    }
} 