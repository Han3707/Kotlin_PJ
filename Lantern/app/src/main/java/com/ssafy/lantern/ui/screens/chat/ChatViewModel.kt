package com.ssafy.lantern.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile // STATE_CONNECTED 등
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lantern.MyApp
import com.ssafy.lantern.data.model.ChatMessage as MeshChatMessage
import com.ssafy.lantern.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lantern.data.source.ble.gatt.GattClientManager
import com.ssafy.lantern.data.source.ble.gatt.GattServerManager
import com.ssafy.lantern.data.source.ble.scanner.ScannerManager
import com.ssafy.lantern.service.MeshService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import javax.inject.Inject
import android.widget.Toast
import kotlinx.coroutines.delay
import com.ssafy.lantern.service.network.MeshNetworkLayer

// 통신 모드 열거형
enum class ChatMode {
    CLASSIC_BLE, // 기존 BLE 채팅
    MESH_BLE     // Nordic BLE Mesh 채팅
}

// 상태 정의 개선
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(), // 메시지 타입 정의
    val scannedDevices: Map<String, String> = emptyMap(), // Map<MAC 주소, 기기 이름>
    val deviceRssiMap: Map<String, Int> = emptyMap(), // Map<MAC 주소, RSSI 값>
    val connectedDevice: BluetoothDevice? = null,
    val connectionState: Int = BluetoothProfile.STATE_DISCONNECTED,
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val requiredPermissionsGranted: Boolean = false,
    val errorMessage: String? = null,
    
    // Mesh 관련 상태 추가
    val chatMode: ChatMode = ChatMode.CLASSIC_BLE,
    val isMeshNetworkConnected: Boolean = false,
    val meshNodes: List<ProvisionedMeshNode> = emptyList(),
    val meshPublicMessages: List<MeshChatMessage> = emptyList(),
    val meshPrivateMessages: Map<Int, List<MeshChatMessage>> = emptyMap(),
    val meshCurrentChatPartner: Int? = null,
    val meshLocalAddress: Int? = null,
    val meshUserName: String = "",
    val inputMessage: String = "",
    val classicMessages: List<com.ssafy.lantern.data.model.ChatMessage> = emptyList(),
    val connectedDeviceName: String? = null,
    val scanResults: List<BluetoothDevice> = emptyList(),
    val localMeshAddress: Int = 0,
    val provisionedNodes: List<Int> = emptyList()
)

// 메시지 데이터 클래스
data class ChatMessage(
    val sender: String, // "나" 또는 상대방 이름/주소
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@SuppressLint("MissingPermission") // ViewModel 내부에서는 권한 확인되었다고 가정
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    // Hilt를 통해 주입 (DataModule에서 Singleton으로 제공)
    private val advertiserManager: AdvertiserManager,
    private val gattServerManager: GattServerManager,
    private val gattClientManager: GattClientManager,
    private val meshService: MeshService, // Mesh 서비스 추가
    private val networkLayer: MeshNetworkLayer // 네트워크 레이어 추가
) : AndroidViewModel(application), MeshService.MessageListener, MeshService.StatusListener, MeshService.ProvisioningListener {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Bluetooth Adapter
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothManager?.adapter
    }

    // ScannerManager는 ViewModel에서 직접 생성 및 관리
    private val scannerManager: ScannerManager by lazy {
        ScannerManager(application, ::handleScanResult)
    }

    init {
        Log.d("ChatViewModel", "Initializing...")
        checkInitialBluetoothState()
        // GattClient/Server 콜백 설정 (DataModule에서 임시 콜백으로 생성됨)
        setupGattCallbacks()
        
        // Mesh 서비스 리스너 등록
        meshService.addMessageListener(this)
        meshService.addStatusListener(this)
        meshService.addProvisionListener(this)

        _uiState.update {
            it.copy(localMeshAddress = meshService.getLocalUnicastAddress())
        }
        initializeMeshNetwork() // ViewModel 초기화 시 메시 네트워크 상태 확인
    }
    
    override fun onCleared() {
        super.onCleared()
        // Mesh 서비스 리스너 제거
        meshService.removeMessageListener(this)
        meshService.removeStatusListener(this)
        meshService.removeProvisionListener(this)
        stopBleOperations() // BLE 작업 중지
    }

    // GattClient/Server 콜백 설정
    private fun setupGattCallbacks() {
        // GattClientManager 콜백 재설정 (ViewModel 로직 연결)
        gattClientManager.setConnectionStateChangeListener { device, state, status ->
            handleClientConnectionStateChange(device, state, status)
        }
        gattClientManager.setMessageReceivedListener { device, message ->
            // Classic BLE 메시지 수신 처리 (ChatMessage으로 가정)
            val classicMessage = com.ssafy.lantern.data.model.ChatMessage(
                fromAddress = 0, // Classic 모드에서는 주소 개념이 다름, 임의값 또는 고정값
                toAddress = 0,   // 마찬가지
                content = message, 
                senderName = _uiState.value.connectedDeviceName ?: "Unknown"
            )
            _uiState.update { it.copy(classicMessages = it.classicMessages + classicMessage) }
        }

        // GattServerManager 콜백 재설정 (ViewModel 로직 연결)
        gattServerManager.onConnectionStateChange = { device, status, newState ->
            handleServerConnectionStateChange(device, status, newState)
        }
        gattServerManager.onClientSubscribed = { device -> Log.d("ChatViewModel", "Client subscribed: ${device.address}") }
        gattServerManager.onClientUnsubscribed = { device -> Log.d("ChatViewModel", "Client unsubscribed: ${device.address}") }
    }

    // 초기 블루투스 상태 확인
    private fun checkInitialBluetoothState() {
        _uiState.update { it.copy(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true) }
    }

    // 권한 상태 업데이트
    fun updatePermissionStatus(granted: Boolean) {
        val wasGranted = _uiState.value.requiredPermissionsGranted
        _uiState.update { it.copy(requiredPermissionsGranted = granted) }
        if (granted && !wasGranted && _uiState.value.isBluetoothEnabled) {
            startBleOperations()
        } else if (!granted) {
            stopBleOperations()
            _uiState.update { it.copy(errorMessage = "BLE 기능을 사용하려면 권한이 필요합니다.") }
        }
    }

    // 블루투스 활성화 상태 업데이트
    fun updateBluetoothState(enabled: Boolean) {
        val wasEnabled = _uiState.value.isBluetoothEnabled
        _uiState.update { it.copy(isBluetoothEnabled = enabled) }
        if (enabled && !wasEnabled && _uiState.value.requiredPermissionsGranted) {
            startBleOperations()
        } else if (!enabled) {
            stopBleOperations()
            _uiState.update { it.copy(errorMessage = "블루투스를 활성화해주세요.") }
        }
    }

    // 채팅 모드 변경
    fun setChatMode(mode: ChatMode) {
        _uiState.update { it.copy(chatMode = mode, classicMessages = emptyList(), meshPublicMessages = emptyList(), meshPrivateMessages = emptyMap(), meshCurrentChatPartner = null) } // 모드 변경 시 메시지 목록 초기화
        stopBleOperations() // 현재 BLE 작업 중지
        if (_uiState.value.isBluetoothEnabled && _uiState.value.requiredPermissionsGranted) {
            startBleOperations() // 새 모드에 맞춰 BLE 작업 시작
            if (mode == ChatMode.MESH_BLE) {
                initializeMeshNetwork()
            }
        }
    }

    // 메시 네트워크 초기화
    private fun initializeMeshNetwork() {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "메시 네트워크 초기화/상태 확인")
                val localAddress = meshService.getLocalUnicastAddress()
                val provisionedNodes = meshService.getProvisionedNodes()
                _uiState.update { it.copy(localMeshAddress = localAddress, provisionedNodes = provisionedNodes) }

                if (_uiState.value.meshUserName.isBlank() || _uiState.value.meshUserName == "User") {
                    val defaultName = "사용자_${(1000..9999).random()}"
                    _uiState.update { it.copy(meshUserName = defaultName) }
                    Log.d("ChatViewModel", "기본 사용자 이름 설정: $defaultName")
                }
                
                val network = meshService.getMeshNetwork()
                if (network != null) {
                    val hasNetKeys = network.netKeys.isNotEmpty()
                    val hasAppKeys = network.appKeys.isNotEmpty()
                    Log.d("ChatViewModel", "메시 네트워크 상태: NetKeys: $hasNetKeys, AppKeys: $hasAppKeys, LocalAddr: $localAddress")
                    _uiState.update { it.copy(isMeshNetworkConnected = hasNetKeys && hasAppKeys) }
                    val message = when {
                        !hasNetKeys || !hasAppKeys -> "네트워크 키 또는 앱 키가 없습니다. 프로비저닝이 필요할 수 있습니다."
                        else -> """메시 네트워크에 연결되었습니다. (${_uiState.value.meshUserName} @ ${localAddress.toString(16)})
다른 노드: ${provisionedNodes.joinToString { it.toString(16) }}"""
                    }
                    addSystemMessage(message)
                } else {
                    Log.e("ChatViewModel", "메시 네트워크 정보를 가져올 수 없습니다.")
                    addSystemMessage("메시 네트워크 정보를 가져올 수 없습니다.")
                    _uiState.update { it.copy(isMeshNetworkConnected = false) }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "메시 네트워크 초기화 오류", e)
                _uiState.update { it.copy(errorMessage = "메시 네트워크 초기화 실패: ${e.message}", isMeshNetworkConnected = false) }
                addSystemMessage("메시 네트워크 초기화 실패: ${e.message}")
            }
        }
    }
    
    // 시스템 메시지 추가 도우미 함수
    private fun addSystemMessage(content: String) {
        val systemMessage = MeshChatMessage(
            fromAddress = 0,
            toAddress = 0xFFFF,
            timestamp = System.currentTimeMillis(),
            content = content,
            senderName = "시스템"
        )
        
        _uiState.update { state ->
            val updatedMessages = state.meshPublicMessages + systemMessage
            state.copy(meshPublicMessages = updatedMessages)
        }
    }

    // BLE 작업 시작
    fun startBleOperations() {
        if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
            Log.w("ChatViewModel", "Cannot start BLE operations. Permissions or Bluetooth disabled.")
            return
        }
        Log.d("ChatViewModel", "Starting BLE operations for mode: ${_uiState.value.chatMode}")
        viewModelScope.launch {
            try {
                when (_uiState.value.chatMode) {
                    ChatMode.CLASSIC_BLE -> {
                        gattServerManager.openGattServer()
                        advertiserManager.startAdvertising()
                        scannerManager.startScanning(scanCallback, useFilter = true) // 필터 사용
                        _uiState.update { it.copy(isScanning = true, isAdvertising = true) }
                    }
                    ChatMode.MESH_BLE -> {
                        // MeshService가 내부적으로 BLE 스캔/광고를 처리하므로 ViewModel에서 직접 제어하지 않음
                        // 필요한 경우 MeshService의 특정 기능을 호출 (예: 프로비저닝 시작)
                        Log.d("ChatViewModel", "MESH_BLE mode: Operations managed by MeshService.")
                        
                        // 네트워크 레이어의 스캔 시작
                        try {
                            networkLayer.startScan()
                            Log.d("ChatViewModel", "메시 네트워크 스캔 시작")
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "메시 네트워크 스캔 시작 중 오류 발생", e)
                        }
                        
                        // UI 상태 업데이트 (MeshService의 상태를 반영해야 함)
                        initializeMeshNetwork() // 네트워크 상태 재확인
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error starting BLE operations", e)
                _uiState.update { it.copy(errorMessage = "BLE 작업 시작 오류: ${e.message}") }
            }
        }
    }

    // BLE 작업 중지
    fun stopBleOperations() {
        Log.d("ChatViewModel", "Stopping BLE operations for mode: ${_uiState.value.chatMode}")
        try {
            when (_uiState.value.chatMode) {
                ChatMode.CLASSIC_BLE -> {
                    scannerManager.stopScanning()
                    advertiserManager.stopAdvertising()
                    gattServerManager.closeGattServer()
                    gattClientManager.disconnectAll() // 모든 클라이언트 연결 해제
                    _uiState.update { it.copy(isScanning = false, isAdvertising = false, connectedDeviceName = null, classicMessages = emptyList(), scanResults = emptyList()) }
                }
                ChatMode.MESH_BLE -> {
                    // MeshService의 해제 로직은 앱 종료 시점에 호출되거나 별도 관리
                    // 여기서는 ViewModel 레벨에서 UI 관련 상태만 초기화
                     _uiState.update { it.copy(isScanning = false, isAdvertising = false) } // MeshService 상태에 따라 업데이트 필요
                    Log.d("ChatViewModel", "MESH_BLE mode: Stop operations managed by MeshService if any.")
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error stopping BLE operations", e)
            _uiState.update { it.copy(errorMessage = "BLE 작업 중지 오류: ${e.message}") }
        }
    }

    /**
     * 메시지 전송 처리
     */
    fun sendMessage(message: String) {
        Log.d("ChatViewModel", "메시지 전송 시도: $message")
        
        if (message.isBlank()) {
            Log.d("ChatViewModel", "빈 메시지는 전송하지 않습니다.")
            return
        }
        
        viewModelScope.launch {
            try {
                when (_uiState.value.chatMode) {
                    // 일반 블루투스 모드
                    ChatMode.CLASSIC_BLE -> {
                        val connectedDevice = _uiState.value.connectedDevice
                        if (connectedDevice != null) {
                            Log.d("ChatViewModel", "클래식 모드: 메시지 전송 - ${message.take(20)}...")
                            gattServerManager.broadcastMessage(message)
                            
                            // 화면에 메시지 추가
                            val newMessage = ChatMessage(
                                sender = "나", 
                                text = message,
                                timestamp = System.currentTimeMillis()
                            )
                            _uiState.update { state ->
                                val updatedMessages = state.messages + newMessage
                                state.copy(messages = updatedMessages)
                            }
                        } else {
                            Log.e("ChatViewModel", "클래식 모드: 연결된 기기가 없어 메시지를 전송할 수 없습니다.")
                            _uiState.update { it.copy(errorMessage = "연결된 기기가 없어 메시지를 전송할 수 없습니다.") }
                        }
                    }
                    
                    // 메시 네트워크 모드
                    ChatMode.MESH_BLE -> {
                        val selectedNode = _uiState.value.meshCurrentChatPartner
                        val senderName = _uiState.value.meshUserName
                        
                        if (!_uiState.value.isMeshNetworkConnected) {
                            Log.e("ChatViewModel", "메시 모드: 네트워크가 연결되지 않아 메시지를 전송할 수 없습니다.")
                            _uiState.update { it.copy(errorMessage = "메시 네트워크가 연결되지 않았습니다. 연결 후 다시 시도해주세요.") }
                            return@launch
                        }
                        
                        // 개인 채팅 또는 공개 채팅에 따라 처리
                        if (selectedNode != null && selectedNode != 0xFFFF) {
                            Log.d("ChatViewModel", "메시 모드: 개인 메시지 전송 - 대상: ${selectedNode}, 메시지: ${message.take(20)}...")
                            // 유니캐스트 메시지 전송
                            val success = meshService.sendUnicast(selectedNode, message, senderName)
                            
                            if (success) {
                                Log.d("ChatViewModel", "유니캐스트 메시지 전송 성공")
                                // 메시지는 onMessageReceived 콜백을 통해 추가됩니다
                                // 여기서 UI에 직접 추가하지 않음
                            } else {
                                Log.e("ChatViewModel", "유니캐스트 메시지 전송 실패")
                                _uiState.update { it.copy(errorMessage = "메시지 전송에 실패했습니다. 연결 상태를 확인해주세요.") }
                            }
                        } else {
                            Log.d("ChatViewModel", "메시 모드: 공개 메시지 전송 - 메시지: ${message.take(20)}...")
                            // 브로드캐스트 메시지 전송
                            val success = meshService.sendBroadcast(message, senderName)
                            
                            if (success) {
                                Log.d("ChatViewModel", "브로드캐스트 메시지 전송 성공")
                                // 메시지는 onMessageReceived 콜백을 통해 추가됩니다
                                // 여기서 UI에 직접 추가하지 않음
                            } else {
                                Log.e("ChatViewModel", "브로드캐스트 메시지 전송 실패")
                                _uiState.update { it.copy(errorMessage = "메시지 전송에 실패했습니다. 연결 상태를 확인해주세요.") }
                            }
                        }
                    }
                }
                
                // 입력 필드 초기화
                _uiState.update { it.copy(inputMessage = "") }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "메시지 전송 중 오류 발생", e)
                _uiState.update { it.copy(errorMessage = "메시지 전송 중 오류가 발생했습니다: ${e.message}") }
            }
        }
    }
    
    // 사용자 이름 설정
    fun setUserName(name: String) {
        _uiState.update { it.copy(meshUserName = name) }
    }
    
    // 현재 채팅 상대 변경
    fun selectChatPartner(address: Int?) {
        _uiState.update { it.copy(meshCurrentChatPartner = address) }
    }
    
    // 공용 채팅방 선택
    fun selectPublicChat() {
        _uiState.update { it.copy(meshCurrentChatPartner = null) }
    }

    // 스캔 결과 처리 (ScannerManager 콜백)
    private fun handleScanResult(result: ScanResult) {
        if (!_uiState.value.requiredPermissionsGranted) return // 권한 없으면 무시
        
        val device = result.device
        
        // 장치 정보 추출
        val deviceAddress = device.address
        val rssi = result.rssi
        
        // 블루투스 기기 클래스 정보 추출
        val deviceClass = device.bluetoothClass?.deviceClass ?: 0
        val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
        val deviceTypeStr = when(device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }
        
        // 제조사 추정 (MAC 주소의 앞 3바이트로 추정)
        val manufacturerPrefix = deviceAddress.split(":").take(3).joinToString(":")
        val manufacturer = getManufacturerFromOUI(manufacturerPrefix)
        
        // 스캔 데이터 추출 (LE 기기)
        val scanRecord = result.scanRecord
        val deviceName = if (!device.name.isNullOrBlank()) {
            device.name ?: ""  // null이 아닌 문자열 반환 보장
        } else if (scanRecord != null && !scanRecord.deviceName.isNullOrBlank()) {
            scanRecord.deviceName ?: ""  // null이 아닌 문자열 반환 보장
        } else {
            "기기 ${device.address.takeLast(5)}" // 이름이 없으면 MAC 주소 마지막 부분 사용
        }
        
        // 추가 정보를 포함하는 장치 이름 생성
        val enhancedName = if (manufacturer.isNotBlank()) {
            "$deviceName [$manufacturer]"
        } else {
            deviceName
        }
        
        // 로깅
        Log.d("ChatViewModel", "Device found: $enhancedName ($deviceAddress) with RSSI: $rssi, Type: $deviceTypeStr, Class: $majorClass")
        
        // 기존 스캔된 기기 목록에 추가
        _uiState.update { currentState ->
            val updatedDevices = currentState.scannedDevices.toMutableMap().apply {
                put(deviceAddress, enhancedName)
            }
            val updatedRssiMap = currentState.deviceRssiMap.toMutableMap().apply {
                put(deviceAddress, rssi)
            }
            currentState.copy(
                scannedDevices = updatedDevices,
                deviceRssiMap = updatedRssiMap
            )
        }
    }
    
    // MAC 주소의 OUI(제조사 ID)로 제조사 추정
    private fun getManufacturerFromOUI(ouiPrefix: String): String {
        // 주요 OUI 목록 (제한된 목록)
        return when (ouiPrefix.uppercase()) {
            "00:1A:11", "AC:9B:0A", "58:8E:BF" -> "Google"
            "00:0A:27", "34:36:3B", "00:12:FE", "00:18:F5", "00:D0:59" -> "Apple"
            "00:13:E0", "00:1E:E1", "00:26:37", "58:C3:8B", "B0:34:95" -> "Samsung"
            "00:12:5A", "00:17:EB", "00:21:86", "4C:4E:34" -> "Xerox"
            "90:E6:BA", "48:46:C1", "2C:FD:AB", "EC:7F:15" -> "Espressif"
            "00:1A:7D", "00:1E:37", "00:90:4C", "00:22:A9" -> "Nordic"
            "00:E0:4C", "00:23:EB", "5C:51:88", "18:89:5B" -> "Realtek"
            "00:03:7A", "00:06:FD", "00:15:A3", "00:19:5D" -> "Cisco"
            "00:10:EB", "00:1B:44", "00:22:B7", "2C:59:8A" -> "Intel"
            "00:18:C3", "00:1C:FB", "00:25:64", "00:21:D2" -> "Qualcomm"
            "00:26:83", "00:0A:E6", "00:06:C7", "00:0D:9D" -> "Broadcom"
            "00:1A:89", "00:21:70", "00:24:7C", "9C:4F:CF" -> "Nokia"
            "00:0F:B3", "00:1A:80", "00:37:6D", "9C:D3:6D" -> "Sony"
            "00:03:93", "00:12:75", "00:21:1B", "78:DD:12" -> "Microsoft"
            else -> ""
        }
    }

    // 기기 연결 시도 (스캔된 장치 항목 클릭 시)
    fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            _uiState.update { it.copy(errorMessage = "디바이스를 찾을 수 없습니다.") }
            return
        }

        _uiState.update { it.copy(isConnecting = true) }
        Log.d("ChatViewModel", "Connecting to device: $address")
        viewModelScope.launch {
            try {
                // 현재 신호 강도를 확인
                val rssi = _uiState.value.deviceRssiMap[address] ?: -100
                
                if (rssi < -85) {
                    // 신호가 너무 약한 경우 경고 메시지 표시
                    _uiState.update { 
                        it.copy(
                            errorMessage = "신호가 약합니다. 기기를 가까이 두고 다시 시도하세요. (${rssi}dBm)"
                        )
                    }
                }
                
                // GattClientManager를 통해 연결 시작
                val success = gattClientManager.connect(device, false)
                if (!success) {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            errorMessage = "연결 시도 중 오류가 발생했습니다."
                        )
                    }
                }
                // 성공/실패 여부는 handleClientConnectionStateChange에서 처리
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error connecting to device", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "연결 오류: ${e.message}"
                    )
                }
            }
        }
    }

    // --- Callback Handlers ---

    // ScanCallback 구현 - ScannerManager에 전달
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 스캔 결과를 직접 처리
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            // 배치 스캔 결과 처리
            for (result in results) {
                handleScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ChatViewModel", "BLE Scan Failed: $errorCode")
            _uiState.update {
                it.copy(
                    isScanning = false,
                    errorMessage = when(errorCode) {
                        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "스캔이 이미 실행 중입니다"
                        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "애플리케이션 등록 실패"
                        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE 스캔 기능이 지원되지 않습니다"
                        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "내부 오류로 인한 스캔 실패"
                        else -> "BLE 스캔 실패: 오류 코드 $errorCode"
                    }
                )
            }
        }
    }

    // GattClientManager 연결 상태 변경 콜백
    private fun handleClientConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.d("ChatViewModel", "Client connection state changed to $status (status: $newState)")
        
        // 상태와 오류 코드에 따른 적절한 처리
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                // 연결 성공
                // 기기 이름과 주소 추출
                val deviceName = device.name ?: "알 수 없는 기기"
                val deviceAddress = device.address
                val deviceClass = device.bluetoothClass?.majorDeviceClass ?: 0
                val deviceType = when(device.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
                    BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
                    else -> "UNKNOWN"
                }
                
                // 기기 정보 로깅
                Log.i("ChatViewModel", "연결된 기기 정보:\n" +
                    "이름: $deviceName\n" +
                    "주소: $deviceAddress\n" +
                    "타입: $deviceType\n" +
                    "클래스: $deviceClass")
                
                // 세부 기기 정보를 포함하여 UI 상태 업데이트
                _uiState.update {
                    it.copy(
                        connectedDevice = device,
                        connectionState = BluetoothProfile.STATE_CONNECTED,
                        isConnecting = false,
                        errorMessage = "연결 성공: $deviceName ($deviceAddress), 타입: $deviceType"
                    )
                }
                
                // 연결 첫 성공 시 정보 메시지 추가
                val deviceInfo = "연결된 기기 정보:\n이름: $deviceName\n주소: $deviceAddress\n타입: $deviceType"
                _uiState.update {
                    it.copy(messages = it.messages + ChatMessage("시스템", deviceInfo))
                }
            }
            BluetoothProfile.STATE_CONNECTING -> {
                // 연결 중
                _uiState.update {
                    it.copy(
                        connectionState = BluetoothProfile.STATE_CONNECTING,
                        isConnecting = true
                    )
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                // 연결 실패 또는 연결 해제
                val errorMessageToShow = when (status) {
                    BluetoothGatt.GATT_SUCCESS -> null // 정상 연결 해제
                    133 -> "이 기기는 Lantern 앱과 호환되지 않거나 범위를 벗어났습니다. (오류: 133)"
                    8 -> "블루투스 연결 권한이 없습니다. (오류: 8)"
                    22 -> "블루투스 통신 오류가 발생했습니다. (오류: 22)"
                    62 -> "기기가 연결을 거부했습니다. (오류: 62)"
                    else -> "블루투스 연결 오류가 발생했습니다. (오류: $status)"
                }
                
                _uiState.update {
                    it.copy(
                        // 정상적인 연결 해제가 아닌 경우에만 connectedDevice를 null로 설정
                        connectedDevice = if (status == BluetoothGatt.GATT_SUCCESS && it.connectedDevice?.address != device.address) it.connectedDevice else null,
                        connectionState = BluetoothProfile.STATE_DISCONNECTED,
                        isConnecting = false,
                        errorMessage = errorMessageToShow
                    )
                }
            }
            BluetoothProfile.STATE_DISCONNECTING -> {
                // 연결 해제 중
                _uiState.update {
                    it.copy(
                        connectionState = BluetoothProfile.STATE_DISCONNECTING
                    )
                }
            }
        }
    }

    // GattServerManager 연결 상태 변경 콜백
    private fun handleServerConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.d("ChatViewModel", "Server connection state changed for ${device.name.orEmpty()} to $newState (status: $status)")
        // 서버 측 연결 상태 변경은 별도 처리가 필요한 경우 여기서 구현
    }

    // 클라이언트에서 메시지 수신 콜백
    private fun handleMessageReceived(device: BluetoothDevice, message: String) {
        // 상대방 닉네임 대신 기기 이름 또는 주소 사용
        val sender = device.name.orEmpty().ifEmpty { device.address }
        Log.d("ChatViewModel", "Message received from $sender: $message")
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(sender, message))
        }
    }
    
    /**
     * 메시지 수신 처리
     */
    override fun onMessageReceived(message: MeshChatMessage) {
        Log.d("ChatViewModel", "Mesh Message Received: $message")
        
        viewModelScope.launch {
            // 중복 메시지 필터링 (같은 내용과 시간이 0.5초 이내인 메시지)
            val isDuplicate = _uiState.value.meshPublicMessages.any { existingMsg ->
                existingMsg.content == message.content &&
                existingMsg.fromAddress == message.fromAddress &&
                existingMsg.timestamp > 0 &&
                Math.abs(existingMsg.timestamp - message.timestamp) < 500 // 0.5초 이내 동일 메시지 필터링
            }
            
            if (isDuplicate) {
                Log.d("ChatViewModel", "중복 메시지 무시: ${message.content}")
                return@launch
            }
            
            // UI 상태 업데이트
            _uiState.update { state ->
                if (message.isPublic) {
                    // 공개 메시지 추가
                    state.copy(meshPublicMessages = state.meshPublicMessages + message)
                } else {
                    // 개인 메시지 추가
                    val existingMessages = state.meshPrivateMessages[message.fromAddress] ?: emptyList()
                    val updatedMessages = existingMessages + message
                    val updatedMap = state.meshPrivateMessages.toMutableMap().apply {
                        put(message.fromAddress, updatedMessages)
                    }
                    state.copy(meshPrivateMessages = updatedMap)
                }
            }
        }
    }
    
    /**
     * 네트워크 상태 변경 처리
     */
    override fun onNetworkStatusChanged(isConnected: Boolean) {
        Log.d("ChatViewModel", "Mesh Network Status Changed: $isConnected")
        _uiState.update { it.copy(isMeshNetworkConnected = isConnected) }
        
        // 상태 변경 메시지 추가
        val statusMessage = if (isConnected) "메시 네트워크에 연결되었습니다." else "메시 네트워크 연결이 끊어졌습니다."
        addSystemMessage(statusMessage)
    }
    
    override fun onProvisioningComplete(unicastAddress: Int) {
        Log.d("ChatViewModel", "Node Provisioning Complete: $unicastAddress")
        _uiState.update { 
            it.copy(provisionedNodes = it.provisionedNodes + unicastAddress)
        }
        addSystemMessage("노드 프로비저닝 성공: 0x${unicastAddress.toString(16)}")
        // 프로비저닝 완료 후 네트워크 정보 갱신
        initializeMeshNetwork()
    }

    override fun onProvisioningFailed(device: BluetoothDevice, error: String) {
        Log.e("ChatViewModel", "Node Provisioning Failed: ${device.address}, Error: $error")
        addSystemMessage("노드 프로비저닝 실패: ${device.name ?: device.address} - $error")
    }

    // BLE 데이터 수신 처리 - 더 이상 사용되지 않음
    fun handleBleNotification(data: ByteArray) {
        // 이 메서드는 더 이상 사용되지 않습니다. onMessageReceived로 대체됩니다.
        Log.d("ChatViewModel", "handleBleNotification은 더 이상 사용되지 않습니다. onMessageReceived를 사용하세요.")
    }

    // BLE 프로비저닝 완료 처리 - 더 이상 사용되지 않음
    fun handleProvisioningComplete(unicastAddress: Int) {
        // 이 메서드는 더 이상 사용되지 않습니다. onProvisioningComplete로 대체됩니다.
        Log.d("ChatViewModel", "handleProvisioningComplete은 더 이상 사용되지 않습니다. onProvisioningComplete을 사용하세요.")
    }

    // BLE 데이터 알림 처리 - 더 이상 사용되지 않음
    fun handleBleNotifications(mtu: Int, data: ByteArray) {
        // 이 메서드는 더 이상 사용되지 않습니다.
        Log.d("ChatViewModel", "handleBleNotifications은 더 이상 사용되지 않습니다.")
    }

    // BLE 쓰기 콜백 처리 - 더 이상 사용되지 않음
    fun handleBleWriteCallbacks(mtu: Int, data: ByteArray) {
        // 이 메서드는 더 이상 사용되지 않습니다.
        Log.d("ChatViewModel", "handleBleWriteCallbacks은 더 이상 사용되지 않습니다.")
    }

    // 에러 메시지 표시
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // 임시로 MeshChatMessage에 isPublic 추가 (ChatMessage의 것을 활용하거나 중복 제거 필요)
    val MeshChatMessage.isPublic: Boolean
        get() = this.toAddress == MeshService.BROADCAST_ADDR
} 