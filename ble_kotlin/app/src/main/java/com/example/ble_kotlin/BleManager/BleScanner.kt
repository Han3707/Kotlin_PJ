package com.example.ble_kotlin.BleManager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.ble_kotlin.Utils.Constants
import com.example.ble_kotlin.Utils.DistanceEstimator
import com.example.ble_kotlin.Utils.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * BLE 스캔 기능을 관리하는 클래스.
 * 주변의 BLE 장치를 스캔하고 필터링합니다.
 *
 * @property context 애플리케이션 컨텍스트
 */
class BleScanner(private val context: Context) {
    
    private val TAG = "BleScanner"
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    
    /**
     * 특정 서비스 UUID를 가진 BLE 기기 스캔 시작.
     * Flow를 통해 스캔된 기기 정보를 스트림으로 제공합니다.
     *
     * @param uuid 필터링할 서비스 UUID
     * @return 스캔된 기기 Flow
     */
    fun startScan(uuid: UUID = Constants.APP_SERVICE_UUID): Flow<ScannedDevice> = callbackFlow {
        try {
            // 이미 스캔 중이면 중지
            if (isScanning) {
                stopScan()
            }
            
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            
            if (bluetoothLeScanner == null) {
                close(IllegalStateException("BluetoothLeScanner is not available on this device"))
                return@callbackFlow
            }
            
            // 스캔 콜백 설정
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    Log.d(TAG, "onScanResult: ${result.device.address}")
                    
                    // 스캔된 기기 정보 생성 및 전송
                    val scannedDevice = ScannedDevice(
                        deviceName = result.device.name,
                        deviceAddress = result.device.address,
                        rssi = result.rssi,
                        // DistanceEstimator를 사용하여 거리 추정
                        estimatedDistance = DistanceEstimator.estimateDistance(result.rssi)
                    )
                    
                    // Flow로 기기 정보 전송
                    trySend(scannedDevice)
                }
                
                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed with error: $errorCode")
                    close(Exception("Scan failed with error: $errorCode"))
                }
            }
            
            // 스캔 설정
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            // 스캔 필터 (특정 UUID만 스캔하도록)
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(uuid))
                .build()
            
            // 스캔 시작
            bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            
            Log.d(TAG, "BLE scan started for UUID: $uuid")
            
            // Flow가 수집되지 않을 때 스캔 중지
            awaitClose {
                CoroutineScope(Dispatchers.IO).launch {
                    stopScan()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            close(e)
        }
    }.catch { e ->
        Log.e(TAG, "Error in scan flow", e)
        throw e
    }.flowOn(Dispatchers.IO)
    
    /**
     * BLE 스캔 중지
     *
     * @return 작업 성공/실패 Result
     */
    suspend fun stopScan(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isScanning && bluetoothLeScanner != null) {
                bluetoothLeScanner?.stopScan(object : ScanCallback() {})
                isScanning = false
                Log.d(TAG, "BLE scan stopped")
            }
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 현재 스캔 중인지 확인
     *
     * @return 스캔 상태
     */
    fun isScanning(): Boolean = isScanning
} 