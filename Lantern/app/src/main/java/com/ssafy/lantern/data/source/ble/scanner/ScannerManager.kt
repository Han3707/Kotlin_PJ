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
    fun startScanning(callback: ScanCallback) {
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

        val SERVICE_UUID = GattServerManager.SERVICE_UUID

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanFilters = listOf(scanFilter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Log.i("ScannerManager", "Scanning started for service UUID: $SERVICE_UUID")
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