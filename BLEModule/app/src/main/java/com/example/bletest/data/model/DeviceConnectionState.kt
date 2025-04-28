package com.example.bletest.data.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BLE 장치 상태 정보를 종합적으로 표현하는 클래스
 * 기존의 DeviceConnectionState와 NetworkNode 클래스 내용 통합 -> 이제 관련 없음
 */
@Parcelize
data class DeviceConnectionState(
    // 기본 디바이스 식별 정보
    val device: BluetoothDevice? = null,
    val address: String = device?.address ?: "",
    val name: String? = device?.name,
    
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
        @SuppressLint("MissingPermission")
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
    }
} 