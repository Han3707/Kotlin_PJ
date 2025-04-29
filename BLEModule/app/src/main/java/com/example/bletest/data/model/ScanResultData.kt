package com.example.bletest.data.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.util.Log
import com.example.bletest.utils.PermissionHelper.withBleConnectPermission
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * BLE 스캔 결과 데이터
 */
@Parcelize
data class ScanResultData(
    val device: BluetoothDevice,
    val address: String,
    val name: String?,
    val rssi: Int,
    val deviceId: String = "",
    val manufacturerData: ByteArray? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    @SuppressLint("MissingPermission")
    constructor(
        device: BluetoothDevice,
        rssi: Int,
        deviceId: String = "",
        manufacturerData: ByteArray? = null,
        serviceUuids: List<UUID> = emptyList()
    ) : this(
        device = device,
        address = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) device.address else {
            try {
                device.address
            } catch (e: SecurityException) {
                Log.e("ScanResultData", "권한 없음: ${e.message}")
                "unknown"
            }
        },
        name = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) device.name else {
            try {
                device.name
            } catch (e: SecurityException) {
                Log.e("ScanResultData", "권한 없음: ${e.message}")
                null
            }
        },
        rssi = rssi,
        deviceId = deviceId,
        manufacturerData = manufacturerData,
        serviceUuids = serviceUuids
    )
    
    companion object {
        /**
         * 권한 체크 후 기기 정보를 안전하게 가져오는 팩토리 메서드
         */
        fun safeCreate(context: Context, device: BluetoothDevice, rssi: Int, deviceId: String = ""): ScanResultData {
            val address = context.withBleConnectPermission { device.address } ?: "unknown"
            val name = context.withBleConnectPermission { device.name }
            
            return ScanResultData(
                device = device,
                address = address,
                name = name,
                rssi = rssi,
                deviceId = deviceId
            )
        }
    }
    
    @SuppressLint("MissingPermission")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanResultData) return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
} 