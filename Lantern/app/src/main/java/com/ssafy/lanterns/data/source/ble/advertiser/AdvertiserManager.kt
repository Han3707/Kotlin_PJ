package com.ssafy.lanterns.data.source.ble.advertiser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import com.ssafy.lanterns.data.source.ble.gatt.GattServerManager

class AdvertiserManager(private val context: Context){
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback : AdvertiseCallback? = null
    private var isAdvertising = false

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e("AdvertiserManager", "BluetoothLeAdvertiser is not available.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(){
        if (isAdvertising) {
            Log.d("AdvertiserManager", "Advertising is already active.")
            return
        }
        if (bluetoothLeAdvertiser == null) {
            Log.e("AdvertiserManager", "BluetoothLeAdvertiser not initialized. Cannot start advertising.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AdvertiserManager", "Permission denied: BLUETOOTH_ADVERTISE. Cannot start advertising.")
            return
        }

        // Advertise Setting
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // 랜턴 UUID
        val SERVICE_UUID = GattServerManager.SERVICE_UUID

        // Advertising 시 소량의 데이터
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        // advertise 성공 실패 코드
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.i("AdvertiserManager", "Advertising started successfully.")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val errorReason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data Too Large"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too Many Advertisers"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already Started"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal Error"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature Unsupported"
                    else -> "Unknown Error ($errorCode)"
                }
                Log.e("AdvertiserManager", "Advertising failed: $errorReason")
            }
        }

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
        } catch (e: Exception) {
            Log.e("AdvertiserManager", "Exception starting advertising", e)
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising || bluetoothLeAdvertiser == null || advertiseCallback == null) {
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AdvertiserManager", "Permission denied: BLUETOOTH_ADVERTISE. Cannot stop advertising.")
            return
        }

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.i("AdvertiserManager", "Advertising stopped.")
        } catch (e: Exception) {
            Log.e("AdvertiserManager", "Exception stopping advertising", e)
        }
        isAdvertising = false
        advertiseCallback = null
    }
}