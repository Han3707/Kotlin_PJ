package com.example.bletest.data.model

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BLE 장치 연결 상태를 표현하는 데이터 클래스
 */
@Parcelize
data class DeviceConnectionState(
    val device: BluetoothDevice? = null,
    val address: String = device?.address ?: "",
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val lastUpdatedTimeMillis: Long = System.currentTimeMillis()
) : Parcelable 