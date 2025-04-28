package com.example.bletest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.bletest.R
import com.example.bletest.data.model.ConnectionStatus
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageRequest
import com.example.bletest.data.model.MessageResult
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.NetworkNode
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.ui.MainActivity
import com.example.bletest.utils.MessageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 통신을 관리하는 서비스 클래스
 */
class BleService : Service() {
    
    companion object {
        private const val TAG = "BleService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "BleServiceChannel"
        
        // BLE 스캔 타임아웃 (15초)
        private const val SCAN_PERIOD: Long = 15000
        
        // BLE 서비스 및 특성 UUID (예시, 실제 기기에 맞게 수정 필요)
        private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private val TX_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        private val RX_CHAR_UUID = UUID.fromString("00002A18-0000-1000-8000-00805F9B34FB")
    }
    
    // Binder 제공
    private val binder = LocalBinder()
    
    // Bluetooth 관련
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    
    // 스캔 상태 관리
    private val _isScanningState = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = _isScanningState.asStateFlow()
    
    // 스캔 결과
    private val _scanResults = MutableStateFlow<List<ScanResultData>>(emptyList())
    val scanResults: StateFlow<List<ScanResultData>> = _scanResults.asStateFlow()
    
    // 연결된 장치 상태 관리
    private val _connectionStates = MutableStateFlow<List<DeviceConnectionState>>(emptyList())
    val connectionStates: StateFlow<List<DeviceConnectionState>> = _connectionStates.asStateFlow()
    
    // 메시지 관리
    private val _messages = MutableStateFlow<List<MessageData>>(emptyList())
    val messages: StateFlow<List<MessageData>> = _messages.asStateFlow()
    
    // 네트워크 노드 정보
    private val _networkNodes = MutableStateFlow<List<NetworkNode>>(emptyList())
    val networkNodes: StateFlow<List<NetworkNode>> = _networkNodes.asStateFlow()
    
    // 핸들러
    private val handler = Handler(Looper.getMainLooper())
    
    // 연결된 GATT 서버들
    private val gattServers = ConcurrentHashMap<String, BluetoothGatt>()
    
    // 연결된 장치의 특성들
    private val deviceCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()
    
    // 장치 이름
    private var deviceName = "안드로이드 기기"
    
    // 장치 ID (UUID)
    private var deviceId: String? = null
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "BLE 서비스 생성됨")
        
        // Bluetooth 관리자 초기화
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // 고유 장치 ID 생성 (앱 설치마다 다른 ID)
        initDeviceId()
        
        // 장치 이름 설정
        bluetoothAdapter?.name?.let {
            deviceName = it
        }
        
        // 전경 서비스 시작
        startForeground()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BLE 서비스 시작됨")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BLE 서비스 종료됨")
        
        // 모든 BLE 연결 해제
        disconnectAllDevices()
        
        // 스캔 중지
        stopScan()
    }
    
    /**
     * 서비스를 전경 서비스로 시작
     */
    private fun startForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 채널 생성 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE 통신 서비스 실행 중"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 알림 클릭 시 메인 액티비티로 이동
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 알림 생성
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE 서비스 실행 중")
            .setContentText("BLE 통신을 위해 서비스가 실행 중입니다")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
        
        // 전경 서비스 시작
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * 장치 ID 초기화
     */
    private fun initDeviceId() {
        // SharedPreferences에서 UUID 가져오기 또는 새로 생성
        val prefs = getSharedPreferences("BlePrefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString()
        
        // 새로 생성된 경우 저장
        if (!prefs.contains("deviceId")) {
            prefs.edit().putString("deviceId", deviceId).apply()
        }
    }
    
    /**
     * BLE 스캔 시작
     */
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth가 활성화되지 않았습니다")
            return
        }
        
        if (_isScanningState.value) {
            Log.d(TAG, "이미 스캔 중입니다")
            return
        }
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Log.e(TAG, "BLE 스캐너를 가져올 수 없습니다")
            return
        }
        
        // 스캔 설정
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // 스캔 필터
        val filters = mutableListOf<ScanFilter>()
        
        // 스캔 시작
        try {
            bleScanner?.startScan(filters, settings, scanCallback)
            _isScanningState.value = true
            
            // 스캔 타임아웃 설정
            handler.postDelayed({
                stopScan()
            }, SCAN_PERIOD)
            
            Log.d(TAG, "BLE 스캔 시작됨")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 스캔 시작 오류: ${e.message}")
        }
    }
    
    /**
     * BLE 스캔 중지
     */
    fun stopScan() {
        if (!_isScanningState.value) {
            return
        }
        
        try {
            bleScanner?.stopScan(scanCallback)
            _isScanningState.value = false
            Log.d(TAG, "BLE 스캔 중지됨")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 스캔 중지 오류: ${e.message}")
        }
    }
    
    /**
     * 스캔 결과 콜백
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            val name = device.name ?: "알 수 없는 장치"
            val rssi = result.rssi
            
            // 기존 결과 목록 가져오기
            val currentResults = _scanResults.value.toMutableList()
            
            // 기존 결과에 있는지 확인
            val existingIndex = currentResults.indexOfFirst { it.address == address }
            
            val scanData = ScanResultData(
                address = address,
                name = name,
                rssi = rssi,
                lastSeenTimeMillis = System.currentTimeMillis()
            )
            
            if (existingIndex >= 0) {
                // 기존 항목 업데이트
                currentResults[existingIndex] = scanData
            } else {
                // 새 항목 추가
                currentResults.add(scanData)
            }
            
            // 결과 업데이트 (RSSI 순서로 정렬)
            _scanResults.value = currentResults.sortedByDescending { it.rssi }
            
            Log.d(TAG, "스캔 결과: $name ($address), RSSI: $rssi")
        }
        
        override fun onScanFailed(errorCode: Int) {
            _isScanningState.value = false
            Log.e(TAG, "BLE 스캔 실패: $errorCode")
        }
    }
    
    /**
     * BLE 장치에 연결
     */
    fun connectToDevice(address: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth가 활성화되지 않았습니다")
            return
        }
        
        // 이미 연결된 장치인지 확인
        if (gattServers.containsKey(address)) {
            Log.d(TAG, "이미 연결된 장치입니다: $address")
            return
        }
        
        try {
            // 연결 상태 업데이트 (연결 중)
            updateDeviceConnectionState(address, ConnectionStatus.CONNECTING)
            
            // 장치 가져오기
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device == null) {
                Log.e(TAG, "장치를 찾을 수 없습니다: $address")
                updateDeviceConnectionState(address, ConnectionStatus.DISCONNECTED, "장치를 찾을 수 없습니다")
                return
            }
            
            // GATT 서버에 연결
            val gatt = device.connectGatt(this, false, gattCallback)
            gattServers[address] = gatt
            
            Log.d(TAG, "장치 연결 시도: $address")
        } catch (e: Exception) {
            Log.e(TAG, "장치 연결 오류: ${e.message}")
            updateDeviceConnectionState(address, ConnectionStatus.DISCONNECTED, e.message)
        }
    }
    
    /**
     * 장치 연결 해제
     */
    fun disconnectDevice(address: String) {
        val gatt = gattServers[address]
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
                gattServers.remove(address)
                deviceCharacteristics.remove(address)
                
                updateDeviceConnectionState(address, ConnectionStatus.DISCONNECTED)
                Log.d(TAG, "장치 연결 해제: $address")
            } catch (e: Exception) {
                Log.e(TAG, "장치 연결 해제 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 모든 장치 연결 해제
     */
    private fun disconnectAllDevices() {
        for (address in gattServers.keys) {
            disconnectDevice(address)
        }
    }
    
    /**
     * GATT 콜백
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "장치 연결됨: $address")
                    
                    // 서비스 탐색 시작
                    gatt.discoverServices()
                    
                    // 연결 상태 업데이트
                    updateDeviceConnectionState(address, ConnectionStatus.CONNECTED)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "장치 연결 해제됨: $address")
                    
                    // 연결 해제 및 리소스 정리
                    gatt.close()
                    gattServers.remove(address)
                    deviceCharacteristics.remove(address)
                    
                    // 연결 상태 업데이트
                    updateDeviceConnectionState(address, ConnectionStatus.DISCONNECTED)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 탐색 완료: $address")
                
                // 필요한 서비스와 특성 찾기
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val txChar = service.getCharacteristic(TX_CHAR_UUID)
                    
                    if (txChar != null) {
                        deviceCharacteristics[address] = txChar
                        
                        // Notification 활성화
                        val rxChar = service.getCharacteristic(RX_CHAR_UUID)
                        if (rxChar != null) {
                            gatt.setCharacteristicNotification(rxChar, true)
                        }
                        
                        // 연결 상태 업데이트
                        updateDeviceConnectionState(
                            address, 
                            ConnectionStatus.READY,
                            "서비스 탐색 완료, 통신 준비됨"
                        )
                    } else {
                        Log.e(TAG, "필요한 특성을 찾을 수 없습니다: $address")
                        updateDeviceConnectionState(
                            address, 
                            ConnectionStatus.ERROR,
                            "필요한 특성을 찾을 수 없습니다"
                        )
                    }
                } else {
                    Log.e(TAG, "필요한 서비스를 찾을 수 없습니다: $address")
                    updateDeviceConnectionState(
                        address, 
                        ConnectionStatus.ERROR,
                        "필요한 서비스를 찾을 수 없습니다"
                    )
                }
            } else {
                Log.e(TAG, "서비스 탐색 실패: $address, 상태: $status")
                updateDeviceConnectionState(
                    address, 
                    ConnectionStatus.ERROR,
                    "서비스 탐색 실패: 오류 코드 $status"
                )
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val address = gatt.device.address
            Log.d(TAG, "데이터 수신: $address, ${value.contentToString()}")
            
            // 수신된 데이터 처리
            processReceivedData(address, value)
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
            value: ByteArray
        ) {
            val address = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 읽기 성공: $address, ${value.contentToString()}")
            } else {
                Log.e(TAG, "특성 읽기 실패: $address, 상태: $status")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 쓰기 성공: $address")
            } else {
                Log.e(TAG, "특성 쓰기 실패: $address, 상태: $status")
            }
        }
    }
    
    /**
     * 장치 연결 상태 업데이트
     */
    private fun updateDeviceConnectionState(
        address: String,
        status: ConnectionStatus,
        message: String? = null
    ) {
        // 현재 상태 목록 가져오기
        val currentStates = _connectionStates.value.toMutableList()
        
        // 기존 항목 검색
        val existingIndex = currentStates.indexOfFirst { it.address == address }
        
        // 장치 이름 찾기
        val deviceName = _scanResults.value.find { it.address == address }?.name ?: "알 수 없는 장치"
        
        val state = DeviceConnectionState(
            address = address,
            name = deviceName,
            status = status,
            lastUpdatedTimeMillis = System.currentTimeMillis(),
            statusMessage = message
        )
        
        if (existingIndex >= 0) {
            // 기존 항목 업데이트
            currentStates[existingIndex] = state
        } else {
            // 새 항목 추가
            currentStates.add(state)
        }
        
        // 상태 업데이트
        _connectionStates.value = currentStates
    }
    
    /**
     * 메시지 전송
     */
    fun sendMessage(request: MessageRequest): MessageResult {
        // 연결된 장치 확인
        if (gattServers.isEmpty()) {
            return MessageResult(
                messageId = UUID.randomUUID().toString(),
                isSuccess = false,
                errorCode = 1,
                errorMessage = "연결된 장치가 없습니다"
            )
        }
        
        // 특정 대상이 지정된 경우
        if (request.targetId != null) {
            // 대상 장치 찾기
            val targetDevice = _connectionStates.value.find { 
                it.nodeId == request.targetId && it.status == ConnectionStatus.READY
            }
            
            if (targetDevice == null) {
                return MessageResult(
                    messageId = UUID.randomUUID().toString(),
                    isSuccess = false,
                    errorCode = 2,
                    errorMessage = "대상 장치를 찾을 수 없거나 준비되지 않았습니다"
                )
            }
            
            // 메시지 전송
            return sendMessageToDevice(targetDevice.address, request)
        } else {
            // 브로드캐스트 (모든 준비된 장치에 전송)
            val readyDevices = _connectionStates.value.filter { it.status == ConnectionStatus.READY }
            
            if (readyDevices.isEmpty()) {
                return MessageResult(
                    messageId = UUID.randomUUID().toString(),
                    isSuccess = false,
                    errorCode = 3,
                    errorMessage = "준비된 장치가 없습니다"
                )
            }
            
            // 첫 번째 장치에 전송 (예시)
            val firstDevice = readyDevices.first()
            return sendMessageToDevice(firstDevice.address, request)
        }
    }
    
    /**
     * 특정 장치에 메시지 전송
     */
    private fun sendMessageToDevice(address: String, request: MessageRequest): MessageResult {
        val characteristic = deviceCharacteristics[address]
        if (characteristic == null) {
            return MessageResult(
                messageId = UUID.randomUUID().toString(),
                isSuccess = false,
                errorCode = 4,
                errorMessage = "장치의 특성을 찾을 수 없습니다"
            )
        }
        
        // 메시지 ID 생성
        val messageId = UUID.randomUUID().toString()
        
        try {
            // 메시지 데이터 생성 (실제 구현에서는 MessageParser 구현 필요)
            val messageData = MessageParser.prepareMessage(
                sourceId = deviceId ?: UUID.randomUUID().toString(),
                targetId = request.targetId,
                messageId = messageId,
                messageType = request.messageType,
                content = request.content,
                ttl = request.ttl
            )
            
            // 특성에 쓰기
            val gatt = gattServers[address]
            characteristic.value = messageData
            val success = gatt?.writeCharacteristic(characteristic) ?: false
            
            if (success) {
                // 전송 성공 기록
                val message = MessageData(
                    messageId = messageId,
                    sourceId = deviceId,
                    targetId = request.targetId,
                    messageType = request.messageType,
                    content = request.content,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true
                )
                
                // 메시지 목록에 추가
                val updatedMessages = _messages.value.toMutableList().apply {
                    add(0, message)
                    if (size > 100) {
                        removeAt(size - 1)
                    }
                }
                _messages.value = updatedMessages
                
                return MessageResult(
                    messageId = messageId,
                    isSuccess = true
                )
            } else {
                return MessageResult(
                    messageId = messageId,
                    isSuccess = false,
                    errorCode = 5,
                    errorMessage = "메시지 전송 실패"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 오류: ${e.message}")
            return MessageResult(
                messageId = messageId,
                isSuccess = false,
                errorCode = 6,
                errorMessage = "메시지 전송 오류: ${e.message}"
            )
        }
    }
    
    /**
     * 수신된 데이터 처리
     */
    private fun processReceivedData(address: String, data: ByteArray) {
        try {
            // 메시지 파싱 (실제 구현에서는 MessageParser 구현 필요)
            val parsedMessage = MessageParser.parseMessage(data)
            
            // 메시지 종류에 따라 처리
            when (parsedMessage.messageType) {
                MessageType.TEXT -> {
                    // 텍스트 메시지 처리
                    val message = MessageData(
                        messageId = parsedMessage.messageId ?: UUID.randomUUID().toString(),
                        sourceId = parsedMessage.sourceId,
                        targetId = parsedMessage.targetId,
                        messageType = MessageType.TEXT,
                        content = parsedMessage.content ?: "",
                        timestamp = System.currentTimeMillis(),
                        isOutgoing = false
                    )
                    
                    // 메시지 목록에 추가
                    val updatedMessages = _messages.value.toMutableList().apply {
                        add(0, message)
                        if (size > 100) {
                            removeAt(size - 1)
                        }
                    }
                    _messages.value = updatedMessages
                }
                
                MessageType.NODE_LIST -> {
                    // 노드 목록 처리
                    val nodeList = parsedMessage.content?.split(",") ?: emptyList()
                    val nodes = nodeList.map { nodeInfo ->
                        val parts = nodeInfo.split(":")
                        val nodeId = parts.getOrNull(0) ?: ""
                        val nodeName = parts.getOrNull(1) ?: "알 수 없는 노드"
                        
                        NetworkNode(
                            nodeId = nodeId,
                            nodeName = nodeName,
                            lastUpdatedTimeMillis = System.currentTimeMillis()
                        )
                    }
                    
                    // 네트워크 노드 목록 업데이트
                    _networkNodes.value = nodes
                }
                
                // 기타 메시지 타입에 대한 처리 추가 가능
                else -> {
                    Log.d(TAG, "지원되지 않는 메시지 타입: ${parsedMessage.messageType}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 처리 오류: ${e.message}")
        }
    }
    
    /**
     * 장치 이름 가져오기
     */
    fun getDeviceName(): String {
        return deviceName
    }
    
    /**
     * 장치 ID 가져오기
     */
    fun getDeviceId(): String? {
        return deviceId
    }
    
    /**
     * 스캔 중인지 확인
     */
    fun isScanning(): Boolean {
        return _isScanningState.value
    }
    
    /**
     * Local Binder 클래스
     */
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }
} 