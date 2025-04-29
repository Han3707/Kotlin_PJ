package com.example.bletest.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
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
import com.example.bletest.utils.Constants
import com.example.bletest.utils.PermissionHelper
import com.example.bletest.utils.PermissionHelper.withBleConnectPermission
import com.example.bletest.utils.PermissionHelper.withBleScanPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.ParcelUuid

/**
 * BLE 통신을 관리하는 매니저 클래스
 */
@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothManager"
        
        // BLE 서비스 및 특성 UUID - Constants에서 가져옴
        private val SERVICE_UUID = Constants.MESH_SERVICE_UUID.uuid
        private val CHARACTERISTIC_WRITE_UUID = Constants.MESH_CHARACTERISTIC_UUID
        private val CHARACTERISTIC_NOTIFY_UUID = Constants.MESH_CHARACTERISTIC_UUID
        private val DESCRIPTOR_CONFIG_UUID = Constants.CCCD_UUID
        
        // 스캔 타임아웃 (밀리초) - 20초로 늘림
        private const val SCAN_PERIOD = 20000L
    }
    
    // 블루투스 어댑터
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        context.withBleConnectPermission {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            btManager?.adapter
        } ?: run {
            Log.e(TAG, "블루투스 권한이 없습니다")
            null
        }
    }
    
    // 블루투스 스캐너
    private val bluetoothScanner: BluetoothLeScanner? by lazy {
        context.withBleScanPermission {
            bluetoothAdapter?.bluetoothLeScanner
        } ?: run {
            Log.e(TAG, "블루투스 스캔 권한이 없습니다")
            null
        }
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
    private var deviceId = UUID.randomUUID().toString()
    
    // Coroutine scope
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 장치 ID 설정
     * 
     * @param id 새로운 장치 ID
     */
    fun setDeviceId(id: String) {
        this.deviceId = id
        Log.d(TAG, "장치 ID가 설정됨: $id")
    }
    
    /**
     * 블루투스 어댑터가 사용 가능한지 확인
     * 
     * @return 블루투스 어댑터 사용 가능 여부
     */
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
    fun startScan() {
        if (!hasBluetoothAdapter()) return
        
        if (_isScanning.value) {
            Log.d(TAG, "이미 스캔 중입니다.")
            return 
        }
        
        context.withBleScanPermission {
            _scanResults.value = emptyList()
            _isScanning.value = true
            
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
                
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            val scanFilters = listOf(scanFilter)
            
            handler.postDelayed({ stopScan() }, SCAN_PERIOD)
            
            try {
                // bluetoothScanner?.startScan(scanFilters, scanSettings, scanCallback)
                bluetoothScanner?.startScan(null, scanSettings, scanCallback) // 필터 제거 테스트
                Log.d(TAG, "BLE 스캔 시작 (필터 제거 테스트)") // 로그 메시지 수정
            } catch (e: SecurityException) {
                Log.e(TAG, "BLE 스캔 시작 실패 - 권한 문제", e)
                _isScanning.value = false
            }
        } ?: run {
            Log.e(TAG, "BLE 스캔 권한이 없습니다")
            _isScanning.value = false
        }
    }
    
    /**
     * BLE 장치 스캔 중지
     */
    fun stopScan() {
        if (!hasBluetoothAdapter() || !_isScanning.value) return
        
        context.withBleScanPermission {
            try {
                bluetoothScanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE 스캔 중지")
            } catch (e: SecurityException) {
                Log.e(TAG, "BLE 스캔 중지 실패 - 권한 문제", e)
            } finally {
                // 핸들러 콜백 제거는 항상 실행
                handler.removeCallbacksAndMessages(null)
                // 스캔 상태는 권한 여부와 관계없이 false로 설정
                _isScanning.value = false
            }
        } ?: run {
            Log.e(TAG, "BLE 스캔 권한이 없습니다")
            // 권한이 없더라도 스캔 상태는 false로 설정
            _isScanning.value = false
            // 핸들러 콜백 제거 시도
            handler.removeCallbacksAndMessages(null)
        }
    }
    
    /**
     * 특정 BLE 장치에 연결
     */
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "장치 연결 시도")
        val deviceState = DeviceConnectionState.safeCreate(context, device)
        Log.d(TAG, "장치 ${deviceState.name ?: deviceState.address}에 연결 시도")
        disconnect()
        _connectionState.value = deviceState.copy(state = ConnectionState.CONNECTING)
        
        context.withBleConnectPermission {
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                Log.d(TAG, "GATT 연결 시도 시작: ${device.address}")
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT 연결 실패 - 권한 문제", e)
                _connectionState.value = deviceState.copy(
                    state = ConnectionState.DISCONNECTED,
                    errorMessage = "블루투스 연결 권한이 없습니다"
                )
            }
        } ?: run {
            Log.e(TAG, "BLE 연결 권한이 없습니다")
            _connectionState.value = deviceState.copy(
                state = ConnectionState.DISCONNECTED,
                errorMessage = "블루투스 연결 권한이 없습니다"
            )
        }
    }
    
    /**
     * BLE 장치 연결 해제
     */
    fun disconnect() {
        if (bluetoothGatt == null) return
        val currentDeviceAddress = bluetoothGatt?.device?.address ?: "알 수 없는 장치"
        Log.d(TAG, "장치 연결 해제 시도: $currentDeviceAddress")
        _connectionState.update { it.copy(state = ConnectionState.DISCONNECTING) }
        
        context.withBleConnectPermission {
            try {
                bluetoothGatt?.disconnect()
                Log.d(TAG, "GATT 연결 해제 시도 완료: $currentDeviceAddress")
                // 실제 연결 해제는 onConnectionStateChange 콜백에서 처리됨
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT 연결 해제 실패 - 권한 문제", e)
                // 즉시 연결 해제 상태로 변경
                bluetoothGatt?.close() // close도 호출하여 리소스 정리 시도
                bluetoothGatt = null
                _connectionState.value = _connectionState.value.copy(
                    state = ConnectionState.DISCONNECTED,
                    errorMessage = "블루투스 연결 해제 권한이 없습니다"
                )
            }
        } ?: run {
            Log.e(TAG, "BLE 연결 해제 권한이 없습니다")
            _connectionState.update {
                it.copy(
                    state = ConnectionState.DISCONNECTED,
                    errorMessage = "블루투스 연결 해제 권한이 없습니다"
                )
            }
        }
    }
    
    /**
     * 리소스 해제
     */
    fun close() {
        stopScan() // 스캔 중지 확인
        val gattDeviceAddress = bluetoothGatt?.device?.address
        Log.d(TAG, "BLE 리소스 해제 시도: ${gattDeviceAddress ?: "GATT 없음"}")
        context.withBleConnectPermission {
            try {
                bluetoothGatt?.close()
                Log.d(TAG, "GATT 리소스 해제 완료: ${gattDeviceAddress ?: ""}")
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT 리소스 해제 실패 - 권한 문제", e)
            } finally {
                 // 권한 문제와 상관없이 참조 제거
                bluetoothGatt = null
            }
        } ?: run {
            Log.e(TAG, "BLE 리소스 해제 권한이 없습니다")
            bluetoothGatt = null // 권한 없어도 참조 제거
        }
        
        writeCharacteristic = null
        notifyCharacteristic = null
        _connectionState.value = DeviceConnectionState() // 상태 초기화
        Log.d(TAG, "BluetoothManager 리소스 정리 완료")
    }
    
    /**
     * 메시지 전송
     */
    fun sendMessage(message: String, type: MessageType = MessageType.TEXT) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "메시지 전송 실패: GATT 연결 없음")
            return
        }
        
        if (writeCharacteristic == null) {
            Log.e(TAG, "메시지 전송 실패: 쓰기 특성 없음")
            return
        }
        
        context.withBleConnectPermission {
            // 메시지 형식: [장치ID]:[타입]:[메시지]
            val formattedMessage = "$deviceId:${type.value}:$message"
            val data = formattedMessage.toByteArray(Charsets.UTF_8)
            
            // 메시지 데이터 전송
            sendData(data)
            
            // 로컬 메시지 목록에 추가
            val messageData = MessageData(
                messageId = UUID.randomUUID().toString(),
                sourceId = deviceId,
                content = message,
                messageType = type,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true
            )
            
            _messages.update { currentMessages ->
                currentMessages + messageData
            }
        } ?: run {
            Log.e(TAG, "메시지 전송 권한이 없습니다")
        }
    }
    
    /**
     * 특정 타겟에게 메시지 전송 (확장된 버전)
     */
    fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        if (bluetoothGatt == null) {
            Log.e(TAG, "메시지 전송 실패: GATT 연결 없음")
            return false
        }
        
        if (writeCharacteristic == null) {
            Log.e(TAG, "메시지 전송 실패: 쓰기 특성 없음")
            return false
        }
        
        return context.withBleConnectPermission {
            // 메시지 형식: [장치ID]:[타입]:[타겟ID]:[메시지]
            val formattedMessage = if (targetId != null) {
                "$deviceId:${messageType.value}:$targetId:$content"
            } else {
                "$deviceId:${messageType.value}:$content"
            }
            val data = formattedMessage.toByteArray(Charsets.UTF_8)
            
            // 메시지 데이터 전송
            sendData(data)
            
            // 로컬 메시지 목록에 추가
            val messageData = MessageData(
                messageId = UUID.randomUUID().toString(),
                sourceId = deviceId,
                targetId = targetId,
                content = content,
                messageType = messageType,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true
            )
            
            _messages.update { currentMessages ->
                currentMessages + messageData
            }
            
            true
        } ?: false
    }
    
    /**
     * 데이터 전송 (바이트 배열) - 내부 사용
     */
    private fun sendData(data: ByteArray) {
        context.withBleConnectPermission {
            writeCharacteristic?.let { characteristic ->
                try {
                    // Deprecated writeCharacteristic(value) 사용 경고 수정 - API 레벨 체크 후 적절한 값 사용 시도
                    val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bluetoothGatt?.writeCharacteristic(
                            characteristic, 
                            data, 
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ) // Tiramisu 이상은 status code 반환
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = data
                        // 이전 API는 성공 여부(Boolean)를 반환하지 않음, true로 가정하거나 다른 방식 필요
                        @Suppress("DEPRECATION")
                        if (bluetoothGatt?.writeCharacteristic(characteristic) == true) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                    }
                    
                    if (writeResult == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "데이터 전송 성공: ${characteristic.uuid}")
                    } else {
                        Log.e(TAG, "데이터 전송 실패: ${characteristic.uuid}")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "데이터 전송 실패 - 권한 문제", e)
                }
            } ?: Log.e(TAG, "데이터 전송 실패: 쓰기 특성 없음")
        } ?: run {
            Log.e(TAG, "데이터 전송 실패: BLE 연결 권한이 없습니다")
        }
    }
    
    /**
     * 장치 ID 가져오기
     */
    fun getDeviceId(): String {
        return deviceId
    }
    
    /**
     * 스캔 콜백
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            context.withBleScanPermission {
                val device = result.device
                val deviceName = device.name ?: "알 수 없는 장치"
                val deviceAddress = device.address
                val rssi = result.rssi
                
                Log.d(TAG, "스캔 결과: $deviceName, $deviceAddress, RSSI: $rssi")
                
                // 이미 목록에 있는지 확인하고, 없으면 추가
                val existingDevice = _scanResults.value.find { it.address == deviceAddress }
                if (existingDevice == null) {
                    val scanResult = ScanResultData(
                        device = device,
                        rssi = rssi
                    )
                    
                    _scanResults.update { currentResults ->
                        currentResults + scanResult
                    }
                } else {
                    // 기존 장치 업데이트 (RSSI 등)
                    _scanResults.update { currentResults ->
                        currentResults.map {
                            if (it.address == deviceAddress) {
                                it.copy(rssi = rssi)
                            } else {
                                it
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG, "스캔 결과 처리 실패: BLE 스캔 권한이 없습니다")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            Log.e(TAG, "스캔 실패: 에러 코드 $errorCode")
        }
    }
    
    /**
     * GATT 콜백
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            val deviceAddress = gatt?.device?.address
            val deviceName = gatt?.device?.name // device가 null일 수 있으므로 안전 호출
            
            coroutineScope.launch {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(TAG, "연결 성공: $deviceAddress")
                            _connectionState.value = DeviceConnectionState(
                                address = deviceAddress ?: "",
                                name = deviceName ?: "알 수 없는 장치",
                                state = ConnectionState.CONNECTED
                            )
                            // 서비스 검색 시작
                            gatt?.discoverServices()
                        } else {
                            Log.e(TAG, "연결 실패 (상태 CONNECTED, GATT 오류): $deviceAddress, status=$status")
                            handleDisconnect(deviceAddress, deviceName, "연결 실패 (GATT 오류: $status)")
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "연결 해제됨: $deviceAddress")
                        handleDisconnect(deviceAddress, deviceName, "연결 해제됨 (상태: $status)")
                    }
                    else -> {
                        Log.w(TAG, "알 수 없는 연결 상태: $newState")
                        // 필요시 DISCONNECTED 상태로 처리
                        handleDisconnect(deviceAddress, deviceName, "알 수 없는 연결 상태 ($newState)")
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "서비스 검색 성공: ${gatt?.device?.address}")
                val service = gatt?.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "필요한 서비스(${SERVICE_UUID})를 찾을 수 없습니다")
                    disconnect()
                    return
                }
                
                writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                notifyCharacteristic = service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                
                if (writeCharacteristic == null || notifyCharacteristic == null) {
                    Log.e(TAG, "필요한 특성(Write/Notify)을 찾을 수 없습니다")
                    disconnect()
                    return
                }
                
                Log.d(TAG, "쓰기 특성: ${writeCharacteristic?.uuid}")
                Log.d(TAG, "알림 특성: ${notifyCharacteristic?.uuid}")
                
                // 알림 활성화
                enableNotifications(gatt, notifyCharacteristic)
            } else {
                Log.e(TAG, "서비스 검색 실패: status=$status")
                disconnect()
            }
        }
        
        // 특성 읽기 콜백
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            // Deprecated value 사용 경고 수정 - API 레벨 체크 후 적절한 값 사용 시도
            val readValue = try {
                 // 실제 읽기 요청은 콜백 전에 발생해야 하므로 여기서는 value를 사용
                 // Tiramisu 이상에서 API가 다르다면, 읽기 요청 로직에서 처리 필요
                 value
            } catch (e: Exception) {
                Log.e(TAG, "특성 값 읽기 중 오류 (onCharacteristicRead)", e)
                null
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "특성 읽기 성공 (${characteristic.uuid}): ${readValue?.toString(Charsets.UTF_8)}")
                // 읽은 데이터 처리 로직 (필요한 경우)
            } else {
                Log.e(TAG, "특성 읽기 실패 (${characteristic.uuid}): status=$status")
            }
        }
        
        // 특성 쓰기 콜백
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "특성 쓰기 성공: ${characteristic?.uuid}")
            } else {
                Log.e(TAG, "특성 쓰기 실패 (${characteristic?.uuid}): status=$status")
            }
        }
        
        // 특성 변경 콜백 (알림 수신)
        @Deprecated("Use onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, byte[])")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Deprecated API 호출 - 최신 콜백 사용 권장
            // 이 콜백은 TIRAMISU 미만에서만 호출되므로 characteristic.value 직접 사용 가능
            @Suppress("DEPRECATION")
            val dataValue = characteristic.value
            if (dataValue != null) {
                onCharacteristicChanged(gatt, characteristic, dataValue)
            }
        }
        
        // 특성 변경 콜백 (알림 수신 - Android 13 이상)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            Log.d(TAG, "특성 변경 알림 수신 (${characteristic.uuid}): ${value.size} bytes")
            processReceivedData(value)
        }
        
        // 디스크립터 쓰기 콜백
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor?.uuid == DESCRIPTOR_CONFIG_UUID) {
                    Log.i(TAG, "알림 디스크립터 쓰기 성공: ${descriptor.characteristic?.uuid}")
                    // 필요한 경우 여기서 추가 작업 수행 (예: 연결 완료 상태 업데이트)
                } else {
                    Log.d(TAG, "다른 디스크립터 쓰기 성공: ${descriptor?.uuid}")
                }
            } else {
                Log.e(TAG, "디스크립터 쓰기 실패 (${descriptor?.uuid}): status=$status")
                disconnect()
            }
        }
    }
    
    /**
     * 특성에 대한 알림 활성화
     */
    private fun enableNotifications(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        if (gatt == null || characteristic == null) {
            Log.e(TAG, "알림 활성화 실패: GATT 또는 특성이 null")
            return
        }
        
        val descriptor = characteristic.getDescriptor(DESCRIPTOR_CONFIG_UUID)
        if (descriptor == null) {
            Log.e(TAG, "알림 활성화 실패: CCCD 디스크립터를 찾을 수 없음 (${characteristic.uuid})")
            return
        }
        
        // 권한 확인 (setCharacteristicNotification 호출에 필요)
        if (!PermissionHelper.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "알림 활성화 실패: BLUETOOTH_CONNECT 권한 없음")
            return
        }
        
        // 알림 설정
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            Log.e(TAG, "setCharacteristicNotification 실패: ${characteristic.uuid}")
            return
        }
        
        // 디스크립터 값 설정 및 쓰기
        val descriptorValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (Tiramisu) 이상 - 필요한 경우 다른 값 사용
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        
        // Deprecated writeDescriptor 사용 경고 수정
        val writeSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             // gatt.writeDescriptor는 status 코드를 반환함
            val writeResult = gatt.writeDescriptor(descriptor, descriptorValue)
            Log.d(TAG, "writeDescriptor (API 33+) 결과 코드: $writeResult")
            writeResult == BluetoothGatt.GATT_SUCCESS // 성공 여부 반환
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = descriptorValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor) // 이전 버전 API는 boolean 반환
        }
        
        if (writeSuccess) {
            Log.i(TAG, "알림 활성화 요청 성공: ${characteristic.uuid}")
        } else {
            Log.e(TAG, "알림 디스크립터 쓰기 요청 실패: ${characteristic.uuid}")
        }
    }
    
    /**
     * 수신된 데이터 처리
     */
    private fun processReceivedData(data: ByteArray) {
        Log.d(TAG, "수신 데이터 처리 시작: ${data.size} bytes")
        
        // MessageParser.parse -> MessageParser.parseMessage 로 수정 (가정)
        val messageData: MessageData? = MessageParser.parseMessage(data) // 반환 타입을 명시적으로 MessageData? 로 가정
        
        if (messageData != null) {
            // messageData가 null이 아닐 때만 속성 접근
            Log.i(TAG, "메시지 수신 성공: from=${messageData.sourceId}, type=${messageData.messageType}, content=${messageData.content}")
            
            // 메시지 목록에 추가 (UI 업데이트를 위해 StateFlow 업데이트)
            _messages.update { currentMessages ->
                // 중복 메시지 방지 (간단한 방식: 최근 메시지와 내용 비교)
                // messageData가 non-null 이므로 안전하게 속성 접근 가능
                if (currentMessages.lastOrNull()?.content == messageData.content && 
                    currentMessages.lastOrNull()?.sourceId == messageData.sourceId) {
                    Log.d(TAG, "중복 메시지 수신 무시: ${messageData.content}")
                    currentMessages 
                } else {
                    currentMessages + messageData
                }
            }
        } else {
            Log.e(TAG, "수신 데이터 파싱 실패: ${data.toString(Charsets.UTF_8)}")
        }
    }
    
    /**
     * 연결 해제 처리 및 리소스 정리
     */
    private fun handleDisconnect(address: String?, name: String?, reason: String) {
        Log.w(TAG, "연결 해제 처리 시작: $address, 이유: $reason")
        _connectionState.value = DeviceConnectionState(
            address = address ?: "",
            name = name ?: "알 수 없는 장치",
            state = ConnectionState.DISCONNECTED,
            errorMessage = reason
        )
        closeGatt()
        // 추가 정리 로직 (예: 알림 특성 초기화)
        notifyCharacteristic = null
        writeCharacteristic = null
    }

    /**
     * GATT 연결 닫기
     */
    private fun closeGatt() {
        if (bluetoothGatt == null) return
        val deviceAddress = bluetoothGatt?.device?.address ?: "알 수 없는 장치"
        Log.d(TAG, "GATT 연결 닫기 시도: $deviceAddress")
        
        context.withBleConnectPermission {
            try {
                bluetoothGatt?.close()
                Log.i(TAG, "GATT 연결 닫기 성공: $deviceAddress")
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT 연결 닫기 실패 - 권한 문제", e)
            } finally {
                bluetoothGatt = null // 참조 해제
            }
        } ?: run {
            Log.e(TAG, "BLE 연결 권한이 없어 GATT를 닫을 수 없습니다")
            // 권한이 없더라도 참조는 해제
            bluetoothGatt = null
        }
    }

    /**
     * 특성 읽기 요청 처리
     * GATT 서버가 원격 디바이스로부터 특성 읽기 요청을 받았을 때 호출됨
     */
    fun handleCharacteristicReadRequest(characteristic: BluetoothGattCharacteristic): ByteArray? {
        // 현재 구현에서는 단순히 기본 메시지를 반환
        return when {
            characteristic.uuid == CHARACTERISTIC_WRITE_UUID -> {
                val defaultMessage = "$deviceId:INFO:Default read value from ${deviceId}"
                defaultMessage.toByteArray(Charsets.UTF_8)
            }
            else -> null
        }
    }

    /**
     * 특성 쓰기 요청 처리
     * GATT 서버가 원격 디바이스로부터 특성 쓰기 요청을 받았을 때 호출됨
     */
    fun handleCharacteristicWriteRequest(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        _device: BluetoothDevice? // 사용되지 않는 파라미터 _ 처리
    ): Boolean {
        if (characteristic.uuid != CHARACTERISTIC_WRITE_UUID) {
            return false
        }
        
        try {
            // 메시지 파싱 및 처리 로직을 추가할 수 있음
            val messageString = String(value, Charsets.UTF_8)
            Log.d(TAG, "수신된 메시지: $messageString")
            
            // 로컬 메시지 파싱 및 추가
            val parsedMessage = MessageParser.parseMessage(value)
            if (parsedMessage != null) {
                coroutineScope.launch(Dispatchers.Main) {
                    _messages.update { currentMessages ->
                        currentMessages + parsedMessage
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "메시지 처리 중 오류: ${e.message}")
        }
        
        return false
    }

    /**
     * 디스크립터 읽기 요청 처리
     * GATT 서버가 원격 디바이스로부터 디스크립터 읽기 요청을 받았을 때 호출됨
     */
    fun handleDescriptorReadRequest(descriptor: BluetoothGattDescriptor): ByteArray? {
        return if (descriptor.uuid == DESCRIPTOR_CONFIG_UUID) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            null
        }
    }

    /**
     * 디스크립터 쓰기 요청 처리
     * GATT 서버가 원격 디바이스로부터 디스크립터 쓰기 요청을 받았을 때 호출됨
     */
    fun handleDescriptorWriteRequest(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        _device: BluetoothDevice? // 사용되지 않는 파라미터 _ 처리
    ): Boolean {
        if (descriptor.uuid != DESCRIPTOR_CONFIG_UUID) {
            return false
        }
        
        // 알림 활성화/비활성화 처리
        try {
            val isEnableNotification = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.d(TAG, "알림 ${if (isEnableNotification) "활성화" else "비활성화"} 요청: ${_device?.address}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "디스크립터 쓰기 처리 중 오류: ${e.message}")
        }
        
        return false
    }

    /**
     * 메시 서비스 생성
     * GATT 서버에서 제공할 메시 네트워킹용 서비스 생성
     */
    fun createMeshService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // 쓰기/알림용 특성 추가
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // 알림 설정을 위한 디스크립터 추가
        val descriptor = BluetoothGattDescriptor(
            DESCRIPTOR_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        
        service.addCharacteristic(characteristic)
        return service
    }
} 