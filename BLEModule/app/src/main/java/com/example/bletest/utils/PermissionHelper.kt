package com.example.bletest.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BLE 관련 권한 확인 및 관리를 위한 유틸리티 클래스
 */
object PermissionHelper {
    private const val TAG = "PermissionHelper"

    // 인라인 함수에서 사용할 로깅 태그 (public)
    const val LOG_TAG = "PermissionHelper"

    /**
     * 권한이 있는 경우에만 블록을 실행하는 인라인 함수
     * @param permission 필요한 권한
     * @param block 권한이 있을 때 실행할 코드 블록
     * @return 블록 실행 결과 또는 권한이 없으면 null
     */
    inline fun <T> Context.withPermission(permission: String, block: () -> T): T? {
        return if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            block()
        } else {
            Log.d(LOG_TAG, "권한 없음: $permission")
            null
        }
    }

    /**
     * BLUETOOTH_CONNECT 권한이 있는 경우에만 블록을 실행하는 인라인 함수
     * @param block 권한이 있을 때 실행할 코드 블록
     * @return 블록 실행 결과 또는 권한이 없으면 null
     */
    inline fun <T> Context.withBleConnectPermission(block: () -> T): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            withPermission(Manifest.permission.BLUETOOTH_CONNECT, block)
        } else {
            // Android 12 미만에서는 권한 검사 없이 실행
            block()
        }
    }

    /**
     * BLUETOOTH_SCAN 권한이 있는 경우에만 블록을 실행하는 인라인 함수
     * @param block 권한이 있을 때 실행할 코드 블록
     * @return 블록 실행 결과 또는 권한이 없으면 null
     */
    inline fun <T> Context.withBleScanPermission(block: () -> T): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            withPermission(Manifest.permission.BLUETOOTH_SCAN, block)
        } else {
            // Android 12 미만에서는 권한 검사 없이 실행
            block()
        }
    }

    /**
     * BLUETOOTH_ADVERTISE 권한이 있는 경우에만 블록을 실행하는 인라인 함수
     * @param block 권한이 있을 때 실행할 코드 블록
     * @return 블록 실행 결과 또는 권한이 없으면 null
     */
    inline fun <T> Context.withBleAdvertisePermission(block: () -> T): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            withPermission(Manifest.permission.BLUETOOTH_ADVERTISE, block)
        } else {
            // Android 12 미만에서는 권한 검사 없이 실행
            block()
        }
    }

    /**
     * 필요한 모든 권한이 있는지 확인
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }

    /**
     * 위치 권한이 있는지 확인
     */
    fun hasLocationPermission(context: Context): Boolean {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasFineLocationPermission(context)
            } else {
                hasCoarseLocationPermission(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "위치 권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }

    /**
     * 블루투스 권한이 있는지 확인 (Android 12 이상)
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scanPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
                
                val connectPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                
                val advertisePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
                
                return scanPermission && connectPermission && advertisePermission
            }
            return true  // Android 12 미만에서는 별도의 블루투스 권한이 필요 없음
        } catch (e: Exception) {
            Log.e(TAG, "블루투스 권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }

    /**
     * FINE_LOCATION 권한이 있는지 확인
     */
    fun hasFineLocationPermission(context: Context): Boolean {
        try {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "FINE_LOCATION 권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }

    /**
     * COARSE_LOCATION 권한이 있는지 확인
     */
    fun hasCoarseLocationPermission(context: Context): Boolean {
        try {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "COARSE_LOCATION 권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }

    /**
     * 필요한 권한 배열 반환
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    
    /**
     * BLE 기능 사용 가능 여부 확인
     * 필수 권한이 모두 있는지 확인
     */
    fun isBleUsable(context: Context): Boolean {
        return hasRequiredPermissions(context)
    }

    /**
     * 특정 권한이 있는지 확인
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        try {
            return ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "$permission 권한 확인 중 오류 발생: ${e.message}")
            return false
        }
    }
} 