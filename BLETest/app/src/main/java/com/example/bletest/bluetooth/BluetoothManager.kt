package com.example.bletest.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ParsedMessage
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.utils.MessageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Date
import java.util.UUID

/**
 * BLE 통신을 관리하는 매니저 클래스
 */
class BluetoothManager(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothManager"
        
        // BLE 서비스 및 특성 UUID (필요에 따라 수정 필요)
        private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        // 스캔 타임아웃 (밀리초)
        private const val SCAN_PERIOD = 10000L
    }
    
    // 블루투스 어댑터
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        btManager?.adapter
    }
    
    // 스캔 결과 State Flow
    private val _scanResults = MutableStateFlow<List<ScanResultData>>(emptyList())
    val scanResults: StateFlow<List<ScanResultData>> = _scanResults.asStateFlow()
    
    // 연결 상태 State Flow
    private val _connectionState = MutableStateFlow(DeviceConnectionState())
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()
    
    // 메시지 State Flow
    private val _messages = MutableStateFlow<List<MessageData>>(emptyList())
    val messages: StateFlow<List<MessageData>> = _messages.asStateFlow()
    
    // 스캔 상태 State Flow
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Handler for timeout
    private val handler = Handler(Looper.getMainLooper())
    
    // GATT 연결 객체
    private var bluetoothGatt: BluetoothGatt? = null
    
    // 알림을 위한 특성 객체
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    
    // 쓰기를 위한 특성 객체
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    
    // 장치 ID (이 앱 인스턴스의 고유 ID)
    private val deviceId = UUID.randomUUID().toString()
    
    /**
     * BLE 장치 스캔 시작
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothAdapter()) return
        
        if (_isScanning.value) {
            stopScan()
        }
        
        _scanResults.value = emptyList()
        _isScanning.value = true
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // 스캔 타임아웃 설정
        handler.postDelayed({ stopScan() }, SCAN_PERIOD)
        
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        Log.d(TAG, "BLE 스캔 시작")
    }
    
    /**
     * BLE 장치 스캔 중지
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasBluetoothAdapter() || !_isScanning.value) return
        
        _isScanning.value = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "BLE 스캔 중지")
    }
    
    /**
     * 특정 BLE 장치에 연결
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "장치 ${device.name ?: device.address}에 연결 시도")
        
        // 이미 연결된 GATT가 있으면 닫기
        disconnect()
        
        _connectionState.value = DeviceConnectionState(device, ConnectionState.CONNECTING)
        
        // GATT 서버에 연결
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    /**
     * BLE 장치 연결 해제
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (bluetoothGatt == null) return
        
        _connectionState.update {
            it.copy(state = ConnectionState.DISCONNECTING)
        }
        
        bluetoothGatt?.disconnect()
    }
    
    /**
     * 리소스 해제
     */
    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = DeviceConnectionState()
    }
    
    /**
     * 메시지 전송
     */
    @SuppressLint("MissingPermission")
    fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            Log.e(TAG, "GATT 연결 또는 쓰기 특성이 null입니다")
            return false
        }
        
        if (connectionState.value.state != ConnectionState.CONNECTED) {
            Log.e(TAG, "장치가 연결되지 않았습니다")
            return false
        }
        
        // 메시지 준비
        val message = MessageParser.prepareMessage(
            messageType = messageType.value,
            sourceId = deviceId,
            targetId = targetId,
            content = content
        )
        
        if (message == null) {
            Log.e(TAG, "메시지 생성 실패")
            return false
        }
        
        return sendData(message)
    }
    
    /**
     * 데이터 전송
     */
    @SuppressLint("MissingPermission")
    private fun sendData(data: ByteArray): Boolean {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            return false
        }
        
        // 특성 속성 확인 및 쓰기 타입 결정
        val writeType = if (writeCharacteristic?.properties?.and(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        
        // API 수준에 따라 적절한 메소드 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(writeCharacteristic!!, data, writeType)
        } else {
            @Suppress("DEPRECATION")
            writeCharacteristic?.value = data
            @Suppress("DEPRECATION")
            writeCharacteristic?.writeType = writeType
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
        
        // 메시지 파싱 및 상태 업데이트
        val parsedMessage = MessageParser.parseMessage(data)
        
        if (parsedMessage != null) {
            val messageData = MessageData(
                message = parsedMessage,
                rawData = data,
                device = connectionState.value.device,
                isOutgoing = true
            )
            
            _messages.update { currentMessages ->
                currentMessages + messageData
            }
            
            return true
        }
        
        return false
    }
    
    /**
     * 블루투스 어댑터 존재 여부 확인
     */
    private fun hasBluetoothAdapter(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "블루투스 어댑터를 사용할 수 없습니다")
            return false
        }
        return true
    }
    
    /**
     * 스캔 콜백
     */
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanResultData = ScanResultData(
                device = result.device,
                rssi = result.rssi,
                deviceName = result.device.name
            )
            
            // 기존 결과에 추가 (중복 제거)
            _scanResults.update { currentResults ->
                val updatedResults = currentResults.toMutableList()
                val existingIndex = updatedResults.indexOfFirst { it.device.address == scanResultData.device.address }
                
                if (existingIndex >= 0) {
                    updatedResults[existingIndex] = scanResultData
                } else {
                    updatedResults.add(scanResultData)
                }
                
                updatedResults
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "스캔 실패: $errorCode")
            _isScanning.value = false
        }
    }
    
    /**
     * GATT 콜백
     */
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "장치 ${device.name ?: device.address}에 연결됨")
                    _connectionState.value = DeviceConnectionState(device, ConnectionState.CONNECTED)
                    
                    // 서비스 검색 시작
                    bluetoothGatt?.discoverServices()
                }
                
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "장치 ${device.name ?: device.address}에서 연결 해제됨")
                    
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _connectionState.value = DeviceConnectionState(
                            device = device,
                            state = ConnectionState.CONNECTION_ERROR,
                            errorMessage = "연결 오류: $status"
                        )
                    } else {
                        _connectionState.value = DeviceConnectionState(device, ConnectionState.DISCONNECTED)
                    }
                    
                    close()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupGattServices(gatt.services)
            } else {
                Log.e(TAG, "서비스 검색 실패: $status")
                _connectionState.update {
                    it.copy(
                        state = ConnectionState.CONNECTION_ERROR,
                        errorMessage = "서비스 검색 실패: $status"
                    )
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value
                }
                
                handleReceivedData(data)
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                characteristic.value
            } else {
                @Suppress("DEPRECATION")
                characteristic.value
            }
            
            handleReceivedData(data)
        }
        
        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "알림 활성화 성공")
            } else {
                Log.e(TAG, "알림 활성화 실패: $status")
            }
        }
    }
    
    /**
     * 수신된 데이터 처리
     */
    private fun handleReceivedData(data: ByteArray?) {
        if (data == null) return
        
        val parsedMessage = MessageParser.parseMessage(data)
        
        if (parsedMessage != null) {
            val messageData = MessageData(
                message = parsedMessage,
                rawData = data,
                device = connectionState.value.device,
                isOutgoing = false
            )
            
            _messages.update { currentMessages ->
                currentMessages + messageData
            }
        }
    }
    
    /**
     * GATT 서비스 설정
     */
    @SuppressLint("MissingPermission")
    private fun setupGattServices(services: List<BluetoothGattService>?) {
        services?.forEach { service ->
            if (service.uuid == SERVICE_UUID) {
                Log.d(TAG, "필요한 서비스 발견: ${service.uuid}")
                
                // 쓰기 특성 찾기
                writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                if (writeCharacteristic != null) {
                    Log.d(TAG, "쓰기 특성 발견: ${writeCharacteristic?.uuid}")
                }
                
                // 알림 특성 찾기
                notifyCharacteristic = service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                if (notifyCharacteristic != null) {
                    Log.d(TAG, "알림 특성 발견: ${notifyCharacteristic?.uuid}")
                    
                    // 알림 활성화
                    bluetoothGatt?.setCharacteristicNotification(notifyCharacteristic, true)
                    
                    // 디스크립터 설정
                    val descriptor = notifyCharacteristic?.getDescriptor(DESCRIPTOR_CONFIG_UUID)
                    
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            bluetoothGatt?.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            bluetoothGatt?.writeDescriptor(descriptor)
                        }
                    }
                }
                
                return@forEach
            }
        }
        
        if (writeCharacteristic == null || notifyCharacteristic == null) {
            Log.e(TAG, "필요한 특성을 찾을 수 없습니다")
            _connectionState.update {
                it.copy(
                    state = ConnectionState.CONNECTION_ERROR,
                    errorMessage = "필요한 특성을 찾을 수 없습니다"
                )
            }
        }
    }
} 