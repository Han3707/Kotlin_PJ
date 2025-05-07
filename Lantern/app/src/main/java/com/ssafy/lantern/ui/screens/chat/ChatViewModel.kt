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

// 통신 모드 열거형
enum class ChatMode {
    CLASSIC_BLE, // 기존 BLE 채팅
    MESH_BLE     // Nordic BLE Mesh 채팅
}

// 상태 정의 개선
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(), // 메시지 타입 정의
    val scannedDevices: Map<String, String> = emptyMap(), // 스캔된 기기 목록 (Address to Name)
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
    val meshUserName: String = ""
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
    private val meshService: MeshService // Mesh 서비스 추가
) : AndroidViewModel(application), MeshService.MessageListener, MeshService.StatusListener {

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
    }
    
    override fun onCleared() {
        super.onCleared()
        // Mesh 서비스 리스너 제거
        meshService.removeMessageListener(this)
        meshService.removeStatusListener(this)
    }

    // GattClient/Server 콜백 설정
    private fun setupGattCallbacks() {
        // GattClientManager 콜백 재설정 (ViewModel 로직 연결)
        gattClientManager.setConnectionStateChangeListener { device, state, status ->
            handleClientConnectionStateChange(device, state, status)
        }
        gattClientManager.setMessageReceivedListener { device, message ->
            handleMessageReceived(device, message)
        }

        // GattServerManager 콜백 재설정 (ViewModel 로직 연결)
        gattServerManager.onConnectionStateChange = ::handleServerConnectionStateChange
        gattServerManager.onClientSubscribed = { device -> /* 클라이언트 구독 시 처리 */ }
        gattServerManager.onClientUnsubscribed = { device -> /* 클라이언트 구독 해제 시 처리 */ }
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
        _uiState.update { it.copy(chatMode = mode) }
    }

    // BLE 작업 시작
    fun startBleOperations() {
        if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
            Log.w("ChatViewModel", "Cannot start BLE operations. Permissions or Bluetooth disabled.")
            return
        }
        Log.d("ChatViewModel", "Starting BLE operations...")
        viewModelScope.launch {
            try {
                when (_uiState.value.chatMode) {
                    ChatMode.CLASSIC_BLE -> {
                        gattServerManager.openGattServer()
                        advertiserManager.startAdvertising()
                        scannerManager.startScanning(scanCallback) // ViewModel의 콜백 전달
                        _uiState.update { it.copy(isScanning = true, isAdvertising = true, errorMessage = null) }
                    }
                    ChatMode.MESH_BLE -> {
                        // Mesh 네트워크 관련 초기화는 MeshService에서 자동으로 이루어짐
                        // Mesh 작업 시작은 여기서 추가 구현 필요 없음 (MeshService에서 처리)
                        Log.d("ChatViewModel", "Mesh BLE 모드로 실행")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error starting BLE operations", e)
                _uiState.update { it.copy(errorMessage = "BLE 시작 오류: ${e.message}") }
            }
        }
    }

    // BLE 작업 중지
    fun stopBleOperations() {
        Log.d("ChatViewModel", "Stopping BLE operations...")
        viewModelScope.launch {
            try {
                when (_uiState.value.chatMode) {
                    ChatMode.CLASSIC_BLE -> {
                        scannerManager.stopScanning()
                        advertiserManager.stopAdvertising()
                        gattServerManager.closeGattServer()
                        gattClientManager.disconnectAll()
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                isAdvertising = false,
                                scannedDevices = emptyMap(),
                                connectedDevice = null,
                                connectionState = BluetoothProfile.STATE_DISCONNECTED
                            )
                        }
                    }
                    ChatMode.MESH_BLE -> {
                        // Mesh 네트워크 작업 중지는 필요한 경우 여기서 구현 (현재는 필요 없음)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error stopping BLE operations", e)
                // 오류 상태 업데이트 필요 시
            }
        }
    }

    // 메시지 전송
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        when (_uiState.value.chatMode) {
            ChatMode.CLASSIC_BLE -> {
                if (_uiState.value.connectionState != BluetoothProfile.STATE_CONNECTED) {
                    _uiState.update { it.copy(errorMessage = "기기에 연결되지 않았습니다.") }
                    return
                }
                Log.d("ChatViewModel", "Sending message via Classic BLE: $message")
                viewModelScope.launch {
                    gattServerManager.broadcastMessage(message)
                    // 내가 보낸 메시지 UI 업데이트
                    _uiState.update {
                        it.copy(messages = it.messages + ChatMessage("나", message))
                    }
                }
            }
            ChatMode.MESH_BLE -> {
                viewModelScope.launch {
                    val currentPartner = _uiState.value.meshCurrentChatPartner
                    if (currentPartner != null) {
                        // 개인 채팅
                        meshService.sendUnicastMessage(currentPartner, message, _uiState.value.meshUserName)
                    } else {
                        // 공용 채팅
                        meshService.sendBroadcastMessage(message, _uiState.value.meshUserName)
                    }
                }
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
        val deviceName = device.name ?: "Unknown Device"
        val deviceAddress = device.address

        // 스캔된 기기 목록 업데이트 (중복 방지)
        if (!_uiState.value.scannedDevices.containsKey(deviceAddress)) {
            Log.d("ChatViewModel", "Device scanned: $deviceName ($deviceAddress)")
            _uiState.update { state ->
                state.copy(scannedDevices = state.scannedDevices + (deviceAddress to deviceName))
            }
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
                // ScannerManager 중지 (선택적)
                //scannerManager.stopScanning()
                //_uiState.update { it.copy(isScanning = false) }

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
            // 스캔 결과는 ScannerManager에서 handleScanResult로 전달됨
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ChatViewModel", "BLE Scan Failed: $errorCode")
            _uiState.update {
                it.copy(
                    isScanning = false,
                    errorMessage = "BLE 스캔 실패: 오류 코드 $errorCode"
                )
            }
        }
    }

    // GattClientManager 연결 상태 변경 콜백
    private fun handleClientConnectionStateChange(device: BluetoothDevice, state: Int, status: Int) {
        Log.d("ChatViewModel", "Client connection state changed to $state (status: $status)")
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        connectedDevice = device,
                        isConnecting = false,
                        errorMessage = null
                    )
                }
                Log.d("ChatViewModel", "Connected to ${device.name ?: "Unknown"} (${device.address})")
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        isConnecting = false,
                        errorMessage = if (status != BluetoothGatt.GATT_SUCCESS) "연결 해제됨 (오류 코드: $status)" else null
                    )
                }
                Log.d("ChatViewModel", "Disconnected from ${device.name ?: "Unknown"} (${device.address})")
            }
            BluetoothProfile.STATE_CONNECTING -> {
                _uiState.update { it.copy(connectionState = state, isConnecting = true) }
            }
            BluetoothProfile.STATE_DISCONNECTING -> {
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    // GattServerManager 연결 상태 변경 콜백
    private fun handleServerConnectionStateChange(device: BluetoothDevice, state: Int) {
        Log.d("ChatViewModel", "Server connection state changed for ${device.name} to $state")
        // 서버 측 연결 상태 변경은 별도 처리가 필요한 경우 여기서 구현
    }

    // 클라이언트에서 메시지 수신 콜백
    private fun handleMessageReceived(device: BluetoothDevice, message: String) {
        // 상대방 닉네임 대신 기기 이름 또는 주소 사용
        val sender = device.name ?: device.address
        Log.d("ChatViewModel", "Message received from $sender: $message")
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(sender, message))
        }
    }
    
    // --- MeshService.MessageListener 구현 ---
    override fun onMessageReceived(message: MeshChatMessage) {
        _uiState.update { currentState ->
            if (message.isPublic) {
                // 공용 채팅 메시지
                val updatedMessages = currentState.meshPublicMessages + message
                currentState.copy(meshPublicMessages = updatedMessages)
            } else {
                // 개인 채팅 메시지
                val partnerAddress = if (message.fromAddress != currentState.meshLocalAddress) {
                    message.fromAddress
                } else {
                    message.toAddress
                }
                
                val currentMessages = currentState.meshPrivateMessages[partnerAddress] ?: emptyList()
                val updatedMessages = currentMessages + message
                val updatedMap = currentState.meshPrivateMessages.toMutableMap().apply {
                    put(partnerAddress, updatedMessages)
                }
                
                currentState.copy(meshPrivateMessages = updatedMap)
            }
        }
    }
    
    // --- MeshService.StatusListener 구현 ---
    override fun onNetworkStatusChanged(isConnected: Boolean) {
        _uiState.update { it.copy(isMeshNetworkConnected = isConnected) }
    }
    
    override fun onProvisioningComplete(unicastAddress: Int) {
        _uiState.update { it.copy(meshLocalAddress = unicastAddress) }
    }
} 