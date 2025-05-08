package com.ssafy.lantern.data.source.ble.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.lantern.data.source.ble.gatt.GattClientManager
import com.ssafy.lantern.data.source.ble.gatt.GattServerManager
import java.util.UUID

class ScannerManager(
    private val context: Context,
    private val onScanResultFound: (ScanResult) -> Unit
) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e("ScannerManager", "BluetoothLeScanner is not available.")
        }
        Log.d("ScannerManager", "ScannerManager initialized.")
    }

    @SuppressLint("MissingPermission")
    fun startScanning(callback: ScanCallback, useFilter: Boolean = false) {
        this.scanCallback = callback
        if (isScanning) {
            Log.d("ScannerManager", "Scanning is already active.")
            return
        }
        if (bluetoothLeScanner == null) {
            Log.e("ScannerManager", "BluetoothLeScanner not initialized. Cannot start scanning.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ScannerManager", "Permission denied: BLUETOOTH_SCAN. Cannot start scanning.")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // 지연 없이 즉시 결과 보고
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 적극적인 매칭 모드
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // 최대한 많은 기기 검색
            .build()

        try {
            // Lantern 앱이 설치된 기기만 검색하는 필터 설정
            val SERVICE_UUID = GattServerManager.SERVICE_UUID
            
            if (useFilter) {
                // 필터 적용 - Lantern 서비스 UUID가 있는 기기만 검색
                val scanFilter = ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
                val scanFilters = listOf(scanFilter)
                
                Log.i("ScannerManager", "Scanning started with service UUID filter: $SERVICE_UUID")
                bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            } else {
                // 필터 없이 모든 BLE 기기 검색 (테스트 목적)
                Log.i("ScannerManager", "Scanning started for all BLE devices (no filter)")
                bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            }
            
            isScanning = true
        } catch (e: Exception) {
            Log.e("ScannerManager", "Exception starting scan", e)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning || bluetoothLeScanner == null || scanCallback == null) {
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ScannerManager", "Permission denied: BLUETOOTH_SCAN. Cannot stop scanning.")
            return
        }

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.i("ScannerManager", "Scanning stopped.")
        } catch (e: Exception) {
            Log.e("ScannerManager", "Exception stopping scan", e)
        }
        isScanning = false
        scanCallback = null
    }
}