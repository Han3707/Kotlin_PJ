package com.example.ble_kotlin.BleManager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.ble_kotlin.Utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * BLE 광고(Advertising) 기능을 관리하는 클래스.
 * BLE 기기를 스캔 가능한 상태로 만들어 주변 기기에 발견될 수 있게 합니다.
 *
 * @property context 애플리케이션 컨텍스트
 */
class BleAdvertiser(private val context: Context) {
    
    private val TAG = "BleAdvertiser"
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    
    // 광고 상태 업데이트를 위한 SharedFlow
    private val _advertisingStatus = MutableSharedFlow<AdvertisingStatus>(replay = 1)
    val advertisingStatus: SharedFlow<AdvertisingStatus> = _advertisingStatus.asSharedFlow()
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE Advertising started successfully")
            isAdvertising = true
            _advertisingStatus.tryEmit(AdvertisingStatus.Started)
        }
        
        override fun onStartFailure(errorCode: Int) {
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Unknown error: $errorCode"
            }
            
            Log.e(TAG, "BLE Advertising failed to start: $errorMessage")
            isAdvertising = false
            _advertisingStatus.tryEmit(AdvertisingStatus.Failed(errorMessage))
        }
    }
    
    /**
     * BLE 광고 시작
     *
     * @param uuid 광고에 포함할 서비스 UUID
     * @return 작업 성공/실패 Result
     */
    suspend fun startAdvertising(uuid: UUID = Constants.APP_SERVICE_UUID): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 이미 광고 중이면 중지
            if (isAdvertising) {
                stopAdvertising()
            }
            
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                return@withContext Result.failure(
                    IllegalStateException("BluetoothLeAdvertiser is not available on this device")
                )
            }
            
            // 광고 설정
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0) // 무제한 광고
                .build()
            
            // 광고 데이터 설정
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(uuid))
                .build()
            
            // 스캔 응답 데이터
            val scanResponseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .build()
            
            // 광고 시작
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            
            _advertisingStatus.tryEmit(AdvertisingStatus.Starting)
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising", e)
            _advertisingStatus.tryEmit(AdvertisingStatus.Failed(e.message ?: "Unknown error"))
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * BLE 광고 중지
     *
     * @return 작업 성공/실패 Result
     */
    suspend fun stopAdvertising(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isAdvertising && bluetoothLeAdvertiser != null) {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
                _advertisingStatus.tryEmit(AdvertisingStatus.Stopped)
            }
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE advertising", e)
            _advertisingStatus.tryEmit(AdvertisingStatus.Failed(e.message ?: "Unknown error"))
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 현재 광고 중인지 확인
     *
     * @return 광고 상태
     */
    fun isAdvertising(): Boolean = isAdvertising
    
    /**
     * 광고 상태를 나타내는 sealed 클래스
     */
    sealed class AdvertisingStatus {
        object Starting : AdvertisingStatus()
        object Started : AdvertisingStatus()
        object Stopped : AdvertisingStatus()
        data class Failed(val reason: String) : AdvertisingStatus()
    }
} 