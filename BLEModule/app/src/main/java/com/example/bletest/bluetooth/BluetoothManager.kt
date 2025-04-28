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
    
    private fun hasBluetoothAdapter(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth 어댑터를 사용할 수 없습니다")
            return false
        }
        return true
    }
    
    /**
     * BLE 장치 스캔 시작
     */
    @SuppressLint("MissingPermission")
    fun startScan(onResult: ((ScanResult) -> Unit)? = null) {
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
        
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanResultData = ScanResultData(
                    device = result.device,
                    rssi = result.rssi,
                    name = result.device.name ?: "Unknown"
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
                
                onResult?.invoke(result)
                
                // 발견된 기기가 자동 연결 조건을 만족하면 연결 시도
                if (result.device != null && shouldAutoConnect(result)) {
                    Log.d(TAG, "자동 연결 시도: ${result.device.address}, 이름: ${result.device.name ?: "Unknown"}")
                    connect(result.device)
                    stopScan() // 연결 시도 후 스캔 중지
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "스캔 실패: $errorCode")
                _isScanning.value = false
            }
        })
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
        
        _connectionState.value = DeviceConnectionState(
            device = device,
            address = device.address,
            state = ConnectionState.CONNECTING
        )
        
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = bluetoothGatt?.writeCharacteristic(writeCharacteristic!!, data, writeType)
            result == 0 // GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            writeCharacteristic?.value = data
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(writeCharacteristic!!) ?: false
        }
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
                name = result.device.name
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
            val deviceAddress = gatt.device.address
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "장치에 연결됨: $deviceAddress")
                    
                    // 서비스 검색 시작
                    val result = gatt.discoverServices()
                    if (result) {
                        Log.d(TAG, "서비스 검색 시작됨")
                        _connectionState.update {
                            it.copy(state = ConnectionState.CONNECTED)
                        }
                    } else {
                        Log.e(TAG, "서비스 검색 시작 실패")
                        _connectionState.update {
                            it.copy(
                                state = ConnectionState.ERROR, 
                                errorMessage = "서비스 검색 시작 실패"
                            )
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "장치 연결 해제됨: $deviceAddress")
                    
                    // 연결 해제 후 정리
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    writeCharacteristic = null
                    notifyCharacteristic = null
                    
                    _connectionState.update {
                        it.copy(state = ConnectionState.DISCONNECTED)
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "장치 연결 중: $deviceAddress")
                    _connectionState.update {
                        it.copy(state = ConnectionState.CONNECTING)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.d(TAG, "장치 연결 해제 중: $deviceAddress")
                    _connectionState.update {
                        it.copy(state = ConnectionState.DISCONNECTING)
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 검색 완료")
                
                // 필요한 서비스 및 특성 찾기
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    // 쓰기 특성 찾기
                    writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                    
                    // 알림 특성 찾기 및 알림 활성화
                    notifyCharacteristic = service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                    if (notifyCharacteristic != null) {
                        enableNotifications(notifyCharacteristic!!)
                    }
                    
                    if (writeCharacteristic != null && notifyCharacteristic != null) {
                        Log.d(TAG, "서비스 및 특성 설정 완료")
                        _connectionState.update {
                            it.copy(state = ConnectionState.CONNECTED)
                        }
                    } else {
                        Log.e(TAG, "필요한 특성을 찾을 수 없음")
                        _connectionState.update {
                            it.copy(
                                state = ConnectionState.ERROR,
                                errorMessage = "필요한 특성을 찾을 수 없음"
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "필요한 서비스를 찾을 수 없음")
                    _connectionState.update {
                        it.copy(
                            state = ConnectionState.ERROR,
                            errorMessage = "필요한 서비스를 찾을 수 없음"
                        )
                    }
                }
            } else {
                Log.e(TAG, "서비스 검색 실패: $status")
                _connectionState.update {
                    it.copy(
                        state = ConnectionState.ERROR,
                        errorMessage = "서비스 검색 실패: $status"
                    )
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value
            Log.d(TAG, "특성 값 변경됨 (레거시): ${characteristic.uuid}")
            handleReceivedData(value)
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 쓰기 성공: $address, ${characteristic.uuid}")
                // TODO: 필요하다면 성공적으로 보낸 메시지의 상태 업데이트 (예: _messages Flow에서 해당 MessageData의 isSent 업데이트)
            } else {
                Log.e(TAG, "특성 쓰기 실패: $address, 상태: $status")
                // TODO: 필요하다면 메시지 전송 실패 상태 업데이트
            }
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "디스크립터 쓰기 완료: ${descriptor.uuid}, 상태: $status")
        }
    }
    
    /**
     * 알림 활성화
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val gatt = bluetoothGatt ?: return
        
        // 알림 활성화
        gatt.setCharacteristicNotification(characteristic, true)
        
        // 디스크립터 설정
        val descriptor = characteristic.getDescriptor(DESCRIPTOR_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
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
                messageId = parsedMessage.messageId,
                sourceId = parsedMessage.sourceId,
                targetId = parsedMessage.targetId,
                messageType = parsedMessage.messageType,
                content = parsedMessage.content,
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
     * 장치 ID 가져오기
     */
    fun getDeviceId(): String {
        return deviceId
    }
    
    /**
     * 자동 연결이 필요한 기기인지 확인
     */
    private fun shouldAutoConnect(result: ScanResult): Boolean {
        // 스캔 레코드에서 서비스 UUID 확인
        val serviceUuids = result.scanRecord?.serviceUuids
        
        // 우리 앱의 메시 서비스 UUID를 광고하는지 확인
        val hasTargetService = serviceUuids?.any { 
            it.uuid == SERVICE_UUID 
        } == true
        
        // 이미 연결되어 있는지 확인
        val isAlreadyConnected = bluetoothGatt?.device?.address == result.device.address
        
        return hasTargetService && !isAlreadyConnected
    }
} 