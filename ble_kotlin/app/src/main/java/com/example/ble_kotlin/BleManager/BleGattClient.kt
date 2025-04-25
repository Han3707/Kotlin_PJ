package com.example.ble_kotlin.BleManager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.ble_kotlin.Utils.ChatMessage
import com.example.ble_kotlin.Utils.Constants
import com.example.ble_kotlin.Utils.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * BLE GATT 클라이언트 기능을 관리하는 클래스.
 * BLE 장치에 연결하고, 서비스와 특성을 탐색하며, 데이터를 주고받습니다.
 *
 * @property context 애플리케이션 컨텍스트
 * @property bluetoothAdapter Bluetooth 어댑터
 */
class BleGattClient(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val TAG = "BleGattClient"
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    
    private val messageFlow = MutableSharedFlow<ChatMessage>(replay = 10)
    
    // GATT 클라이언트 및 연결 상태 관리
    private var connectedDevice: BluetoothDevice? = null
    private var serviceDiscovered = false
    
    // 상태 및 메시지 관리를 위한 Flow
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _receivedMessages = MutableSharedFlow<ChatMessage>(replay = 10)
    val receivedMessages: SharedFlow<ChatMessage> = _receivedMessages.asSharedFlow()
    
    /**
     * GATT 연결 상태를 나타내는 Sealed 클래스
     */
    sealed class GattState {
        object Disconnected : GattState()
        object Connecting : GattState()
        object Connected : GattState()
        object ServicesDiscovered : GattState()
        object Ready : GattState()
        data class Error(val message: String) : GattState()
    }
    
    /**
     * GATT 콜백 구현
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to $deviceAddress")
                        connectedDevice = gatt.device
                        _connectionState.tryEmit(ConnectionState.CONNECTED)
                        
                        // 연결 후 서비스 검색 시작
                        serviceDiscovered = false
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $deviceAddress")
                        serviceDiscovered = false
                        connectedDevice = null
                        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                        
                        // 연결 해제 후 GATT 정리
                        closeGatt()
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                _connectionState.tryEmit(ConnectionState.ERROR)
                closeGatt()
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for ${gatt.device.address}")
                serviceDiscovered = true
                
                // 타겟 서비스와 특성 찾기
                val service = gatt.getService(Constants.GATT_SERVICE_UUID)
                
                if (service != null) {
                    Log.d(TAG, "Found target service: ${service.uuid}")
                    
                    // 특성에 대한 알림 활성화
                    enableCharacteristicNotification(service, Constants.GATT_CHARACTERISTIC_UUID)
                } else {
                    Log.e(TAG, "Target service not found")
                    _connectionState.tryEmit(ConnectionState.ERROR)
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.tryEmit(ConnectionState.ERROR)
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                if (value != null) {
                    val message = String(value, StandardCharsets.UTF_8)
                    Log.d(TAG, "Read characteristic: $message from ${characteristic.uuid}")
                    
                    if (characteristic.uuid == Constants.GATT_CHARACTERISTIC_UUID) {
                        processReceivedMessage(gatt.device, message)
                    }
                }
            } else {
                Log.e(TAG, "Characteristic read failed: $status")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value
            if (value != null) {
                val message = String(value, StandardCharsets.UTF_8)
                Log.d(TAG, "Characteristic changed: $message from ${characteristic.uuid}")
                
                if (characteristic.uuid == Constants.GATT_CHARACTERISTIC_UUID) {
                    processReceivedMessage(gatt.device, message)
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful to ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Write failed: $status to ${characteristic.uuid}")
            }
        }
    }
    
    /**
     * BLE 기기에 연결
     *
     * @param device 연결할 스캔된 BLE 기기
     * @return GATT 상태 Flow
     */
    fun connectToDevice(device: ScannedDevice): Flow<GattState> = callbackFlow {
        Log.d(TAG, "Connecting to device: ${device.deviceAddress}")
        
        // 이미 연결되어 있으면 현재 연결 해제
        if (bluetoothGatt != null) {
            close()
        }
        
        // 콜백 생성
        val callback = GattClientCallback { state ->
            trySend(state)
        }
        
        // 연결 상태 변경
        trySend(GattState.Connecting)
        
        try {
            // BluetoothDevice 객체 가져오기
            val bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.deviceAddress)
            
            // GATT 서버에 연결
            bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback)
            
            if (bluetoothGatt == null) {
                close(IllegalStateException("Failed to connect to GATT server"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            close(e)
        }
        
        awaitClose {
            Log.d(TAG, "Closing GATT connection")
            close()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * GATT 연결 종료
     */
    fun close() {
        try {
            closeGatt()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT client", e)
        }
    }
    
    /**
     * 메시지 전송
     *
     * @param message 전송할 메시지
     * @return 작업 성공/실패 Result
     */
    @Suppress("DEPRECATION") // 하위 호환성을 위한 코드
    suspend fun sendMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (bluetoothGatt == null || targetCharacteristic == null) {
                return@withContext Result.failure(IllegalStateException("GATT not ready"))
            }
            
            // 데이터 설정
            targetCharacteristic!!.value = message.toByteArray()
            
            // 데이터 쓰기
            val success = bluetoothGatt!!.writeCharacteristic(targetCharacteristic!!)
            
            if (success) {
                Log.d(TAG, "Message sent successfully: $message")
                
                // 내가 보낸 메시지도 Flow에 추가
                messageFlow.emit(
                    ChatMessage(
                        sender = "Me",
                        text = message
                    )
                )
                
                return@withContext Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send message")
                return@withContext Result.failure(Exception("Failed to send message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 수신 메시지 Flow 반환
     *
     * @return 메시지 Flow
     */
    fun receiveMessages(): SharedFlow<ChatMessage> = messageFlow
    
    /**
     * 현재 GATT 클라이언트의 연결 상태를 확인합니다.
     *
     * @return 연결되어 있으면 true, 그렇지 않으면 false
     */
    fun isConnected(): Boolean {
        return bluetoothGatt != null && connectedDevice != null &&
               (_connectionState.value == ConnectionState.CONNECTED || 
                _connectionState.value == ConnectionState.READY)
    }
    
    /**
     * 특성 알림을 활성화합니다.
     *
     * @param service 대상 서비스
     * @param characteristicUuid 알림을 활성화할 특성 UUID
     * @return 활성화 성공 여부
     */
    private fun enableCharacteristicNotification(
        service: BluetoothGattService,
        characteristicUuid: UUID
    ): Boolean {
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false
        
        // 특성에 대한 알림 활성화
        val success = bluetoothGatt?.setCharacteristicNotification(characteristic, true) ?: false
        
        if (success) {
            Log.d(TAG, "Notification enabled for ${characteristic.uuid}")
        } else {
            Log.e(TAG, "Failed to enable notification for ${characteristic.uuid}")
        }
        
        return success
    }
    
    /**
     * GATT 클라이언트를 닫고 자원을 해제합니다.
     */
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
    }
    
    /**
     * 수신된 메시지를 처리합니다.
     *
     * @param device 메시지를 보낸 디바이스
     * @param message 수신된 메시지
     */
    private fun processReceivedMessage(device: BluetoothDevice, message: String) {
        if (message.isNotEmpty()) {
            _receivedMessages.tryEmit(
                ChatMessage(
                    sender = device.address,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
} 