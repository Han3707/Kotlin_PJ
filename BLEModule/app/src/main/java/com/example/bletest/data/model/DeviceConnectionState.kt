package com.example.bletest.data.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.util.Log
import com.example.bletest.utils.PermissionHelper.withBleConnectPermission
import kotlinx.parcelize.Parcelize

/**
 * BLE 장치 상태 정보를 종합적으로 표현하는 클래스
 * 기존의 DeviceConnectionState와 NetworkNode 클래스 내용 통합 -> 이제 관련 없음
 */
@Parcelize
data class DeviceConnectionState(
    // 기본 디바이스 식별 정보
    val device: BluetoothDevice? = null,
    val address: String = "",  // 생성자에서 명시적으로 설정
    val name: String? = null,  // 생성자에서 명시적으로 설정
    
    // 메시 네트워크 식별 정보
    val nodeId: String = "",
    val deviceId: String = "",
    
    // 연결 상태 정보
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    
    // 네트워크 정보
    val hopCount: Int = 0,
    val rssi: Int = 0,
    
    // 메타데이터
    val lastUpdatedTimeMillis: Long = System.currentTimeMillis()
) : Parcelable {

    constructor(
        device: BluetoothDevice?,
        state: ConnectionState = ConnectionState.DISCONNECTED,
        errorMessage: String? = null,
        nodeId: String = "",
        deviceId: String = "",
        hopCount: Int = 0,
        rssi: Int = 0
    ) : this(
        device = device,
        address = if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            device?.address ?: ""
        } else {
            try {
                device.address
            } catch (e: SecurityException) {
                Log.e("DeviceConnectionState", "권한 없음: ${e.message}")
                ""
            }
        },
        name = if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            device?.name
        } else {
            try {
                device.name
            } catch (e: SecurityException) {
                Log.e("DeviceConnectionState", "권한 없음: ${e.message}")
                null
            }
        },
        state = state,
        errorMessage = errorMessage,
        nodeId = nodeId,
        deviceId = deviceId,
        hopCount = hopCount,
        rssi = rssi
    )

    /**
     * 현재 상태가 연결됨인지 확인
     */
    fun isConnected(): Boolean {
        return state == ConnectionState.CONNECTED || state == ConnectionState.READY
    }

    /**
     * 현재 상태가 연결 중인지 확인
     */
    fun isConnecting(): Boolean {
        return state == ConnectionState.CONNECTING
    }

    companion object {
        /**
         * ScanResultData에서 DeviceConnectionState 생성
         */
        fun fromScanResult(scanResult: ScanResultData): DeviceConnectionState {
            return DeviceConnectionState(
                device = scanResult.device,
                address = scanResult.address,
                name = scanResult.name,
                deviceId = scanResult.deviceId,
                rssi = scanResult.rssi,
                lastUpdatedTimeMillis = scanResult.timestamp
            )
        }
        
        /**
         * Context를 통해 안전하게 DeviceConnectionState 생성
         */
        fun safeCreate(context: Context, device: BluetoothDevice?): DeviceConnectionState {
            val address = if (device == null) "" else {
                context.withBleConnectPermission { device.address } ?: ""
            }
            
            val name = if (device == null) null else {
                context.withBleConnectPermission { device.name }
            }
            
            return DeviceConnectionState(
                device = device,
                address = address,
                name = name
            )
        }
    }
} 