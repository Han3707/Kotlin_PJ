package com.example.bletest.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BLE 관련 권한 확인 및 관리를 위한 유틸리티 클래스
 */
object PermissionHelper {
    /**
     * 필요한 모든 권한이 있는지 확인
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // Android 12 (API 31) 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasBluetoothPermissions(context) && hasLocationPermission(context)
        }
        // Android 10 (API 29) 이상
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return hasFineLocationPermission(context)
        }
        // Android 9 (API 28) 이하
        else {
            return hasCoarseLocationPermission(context)
        }
    }

    /**
     * 위치 권한이 있는지 확인
     */
    fun hasLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasFineLocationPermission(context)
        } else {
            hasCoarseLocationPermission(context)
        }
    }

    /**
     * 블루투스 권한이 있는지 확인 (Android 12 이상)
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        }
        return true  // Android 12 미만에서는 별도의 블루투스 권한이 필요 없음
    }

    /**
     * FINE_LOCATION 권한이 있는지 확인
     */
    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * COARSE_LOCATION 권한이 있는지 확인
     */
    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 필요한 권한 배열 반환
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
} 