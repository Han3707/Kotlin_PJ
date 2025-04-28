package com.example.bletest.data.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * BLE 스캔 결과 데이터
 */
@Parcelize
data class ScanResultData(
    val device: BluetoothDevice,
    val address: String = device.address,
    val name: String? = device.name,
    val rssi: Int,
    val deviceId: String = "",
    val manufacturerData: ByteArray? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
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