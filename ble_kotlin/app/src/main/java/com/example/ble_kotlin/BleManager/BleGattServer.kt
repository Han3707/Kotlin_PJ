package com.example.ble_kotlin.BleManager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.ble_kotlin.Utils.ChatMessage
import com.example.ble_kotlin.Utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * BLE GATT 서버 기능을 관리하는 클래스.
 * GATT 서비스와 특성을 정의하고 클라이언트의 연결 및 데이터 요청을 처리합니다.
 *
 * @property context 애플리케이션 컨텍스트
 * @property bluetoothManager Bluetooth 관리자
 */
class BleGattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private val TAG = "BleGattServer"
    
    private var gattServer: BluetoothGattServer? = null
    private var gattService: BluetoothGattService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    
    // 연결 상태 및 메시지 수신을 위한 SharedFlow
    private val _connectionStatus = MutableSharedFlow<DeviceConnectionEvent>(replay = 1)
    val connectionStatus: SharedFlow<DeviceConnectionEvent> = _connectionStatus.asSharedFlow()
    
    private val _receivedMessages = MutableSharedFlow<ChatMessage>(replay = 10)
    val receivedMessages: SharedFlow<ChatMessage> = _receivedMessages.asSharedFlow()
    
    /**
     * GATT 서버 콜백 구현
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGattServer.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device.address}")
                        connectedDevices.add(device)
                        _connectionStatus.tryEmit(DeviceConnectionEvent(device, ConnectionState.CONNECTED))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Device disconnected: ${device.address}")
                        connectedDevices.remove(device)
                        _connectionStatus.tryEmit(DeviceConnectionEvent(device, ConnectionState.DISCONNECTED))
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                _connectionStatus.tryEmit(DeviceConnectionEvent(device, ConnectionState.ERROR))
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Read request from ${device.address} for ${characteristic.uuid}")
            
            if (characteristic.uuid == Constants.GATT_CHARACTERISTIC_UUID) {
                // 일반적으로 기본값을 반환하거나 응용 프로그램 상태에 따라 값을 반환
                val response = "Hello from server".toByteArray(StandardCharsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGattServer.GATT_SUCCESS, offset, response)
            } else {
                // 지원하지 않는 특성
                gattServer?.sendResponse(device, requestId, BluetoothGattServer.GATT_FAILURE, offset, null)
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            Log.d(TAG, "Write request from ${device.address} for ${characteristic.uuid}")
            
            if (characteristic.uuid == Constants.GATT_CHARACTERISTIC_UUID) {
                // 클라이언트로부터 메시지 수신
                val message = value?.let { String(it, StandardCharsets.UTF_8) } ?: ""
                
                if (message.isNotEmpty()) {
                    Log.d(TAG, "Received message: $message from ${device.address}")
                    
                    // 메시지 이벤트 발행
                    _receivedMessages.tryEmit(
                        ChatMessage(
                            sender = device.address,
                            message = message,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                // 필요한 경우 응답 전송
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGattServer.GATT_SUCCESS, offset, null)
                }
            } else {
                // 지원하지 않는 특성
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGattServer.GATT_FAILURE, offset, null)
                }
            }
        }
    }
    
    /**
     * GATT 서버 초기화 및 시작
     *
     * @return 서버 시작 성공 여부
     */
    suspend fun startServer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 이미 실행 중인 서버 닫기
            stopServer()
            
            // 서버 인스턴스 생성
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            if (gattServer == null) {
                return@withContext Result.failure(IllegalStateException("Failed to open GATT server"))
            }
            
            // 서비스 생성
            gattService = createGattService()
            
            // 서비스 등록
            val added = gattServer?.addService(gattService)
            
            if (added == true) {
                Log.d(TAG, "GATT server started and service added successfully")
                return@withContext Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to add service to GATT server")
                return@withContext Result.failure(IllegalStateException("Failed to add service to GATT server"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT server", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * GATT 서버 중지
     */
    suspend fun stopServer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (gattServer != null) {
                gattServer?.close()
                gattServer = null
                gattService = null
                gattCharacteristic = null
                connectedDevices.clear()
                
                Log.d(TAG, "GATT server stopped")
            }
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT server", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * GATT 서비스 생성
     *
     * @return 생성된 BluetoothGattService
     */
    @Suppress("DEPRECATION") // 하위 호환성을 위한 코드
    private fun createGattService(): BluetoothGattService {
        // 서비스 생성
        val service = BluetoothGattService(
            Constants.GATT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // 특성 생성
        gattCharacteristic = BluetoothGattCharacteristic(
            Constants.GATT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // 특성을 서비스에 추가
        service.addCharacteristic(gattCharacteristic)
        
        return service
    }
    
    /**
     * 연결된 모든 기기에 메시지 전송
     *
     * @param message 전송할 메시지
     * @return 작업 성공/실패 Result
     */
    suspend fun sendMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (gattServer == null || gattCharacteristic == null) {
                return@withContext Result.failure(IllegalStateException("GATT server not ready"))
            }
            
            if (connectedDevices.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No connected devices"))
            }
            
            // 메시지를 바이트 배열로 변환
            val data = message.toByteArray(StandardCharsets.UTF_8)
            
            // 특성 값 업데이트
            gattCharacteristic?.value = data
            
            // 연결된 모든 기기에 알림 전송
            var allSuccessful = true
            for (device in connectedDevices) {
                val notified = gattServer?.notifyCharacteristicChanged(
                    device, gattCharacteristic, false
                )
                
                if (notified != true) {
                    Log.e(TAG, "Failed to notify device: ${device.address}")
                    allSuccessful = false
                }
            }
            
            // 메시지를 Flow에도 추가 (내가 보낸 메시지)
            _receivedMessages.emit(
                ChatMessage(
                    sender = "Me",
                    message = message,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            return@withContext if (allSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to notify some devices"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 현재 연결된 장치 수를 반환합니다.
     *
     * @return 연결된 장치 수
     */
    fun getConnectedDevicesCount(): Int {
        return connectedDevices.size
    }
    
    /**
     * 서버 실행 중 여부 확인
     *
     * @return 서버 실행 중 여부
     */
    fun isRunning(): Boolean = gattServer != null
}

/**
 * 디바이스 연결 상태 이벤트
 */
data class DeviceConnectionEvent(
    val device: BluetoothDevice,
    val state: ConnectionState
)

/**
 * 채팅 메시지
 */
data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: Long
)

/**
 * 연결 상태 열거형
 */
enum class ConnectionState {
    CONNECTED,
    DISCONNECTED,
    ERROR
} 