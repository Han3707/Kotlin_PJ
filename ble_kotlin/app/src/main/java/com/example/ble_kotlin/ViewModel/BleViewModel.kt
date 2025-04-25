package com.example.ble_kotlin.ViewModel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble_kotlin.BleManager.BleAdvertiser
import com.example.ble_kotlin.BleManager.BleGattClient
import com.example.ble_kotlin.BleManager.BleGattServer
import com.example.ble_kotlin.BleManager.BlePermissionsManager
import com.example.ble_kotlin.BleManager.BleScanner
import com.example.ble_kotlin.BleManager.BleServiceManager
import com.example.ble_kotlin.BleManager.ConnectionState
import com.example.ble_kotlin.BleManager.GattState
import com.example.ble_kotlin.BleManager.PermissionState
import com.example.ble_kotlin.Utils.ChatMessage
import com.example.ble_kotlin.Utils.Constants
import com.example.ble_kotlin.Utils.ScannedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE 테스트 앱의 ViewModel 클래스.
 * 앱의 상태를 관리하고 UI와 BLE 관련 로직 간의 상호작용을 처리합니다.
 *
 * @property application 애플리케이션 컨텍스트
 */
class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "BleViewModel"
    
    // BLE 관련 매니저 클래스들
    private val serviceManager = BleServiceManager(application)
    private val permissionsManager = BlePermissionsManager(application)
    private val advertiser = BleAdvertiser(application)
    private val scanner = BleScanner(application)
    private val gattServer = BleGattServer(
        application,
        application.getSystemService(Application.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    )
    
    // GATT 클라이언트는 연결 시 초기화
    private var gattClient: BleGattClient? = null
    
    // UI 상태
    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()
    
    // 스캔된 기기 목록
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
    
    // 채팅 메시지
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    
    // 이벤트 (토스트 메시지, 스낵바 등)
    private val _events = MutableSharedFlow<BleEvent>()
    val events: SharedFlow<BleEvent> = _events.asSharedFlow()
    
    init {
        // 초기 BLE 상태 체크 및 Flow 수집
        checkBleStatus()
        collectServerMessages()
    }
    
    /**
     * BLE 지원 여부 및 권한 체크
     *
     * @param activity 현재 액티비티 (권한 요청에 필요)
     */
    fun checkBleSupportAndPermissions(activity: Activity) {
        viewModelScope.launch {
            // BLE 지원 여부 확인
            if (!serviceManager.initAdapter()) {
                _uiState.update { it.copy(bleStatus = BleStatus.NOT_AVAILABLE) }
                _events.emit(BleEvent.ShowMessage("이 기기는 Bluetooth Low Energy를 지원하지 않습니다."))
                return@launch
            }
            
            // BLE 활성화 체크
            if (!serviceManager.isBluetoothEnabled()) {
                val enableBtIntent = serviceManager.getEnableBluetoothIntent()
                _events.emit(BleEvent.RequestBluetoothEnable(enableBtIntent))
                return@launch
            }
            
            // 권한 체크
            permissionsManager.checkAndRequestPermissions(activity)
                .collectLatest { permissionState ->
                    when (permissionState) {
                        PermissionState.GRANTED -> {
                            _uiState.update { it.copy(
                                bleStatus = BleStatus.READY,
                                permissionGranted = true
                            )}
                        }
                        PermissionState.BLUETOOTH_PERMISSIONS_REQUIRED,
                        PermissionState.LOCATION_PERMISSION_REQUIRED -> {
                            _uiState.update { it.copy(permissionGranted = false) }
                            _events.emit(BleEvent.RequestPermissions(permissionsManager.getRequiredPermissions()))
                        }
                        else -> {
                            // 다른 상태 처리
                        }
                    }
                }
        }
    }
    
    /**
     * BLE 활성화 요청 결과 처리
     */
    fun handleBluetoothEnableResult(enabled: Boolean) {
        _uiState.update { it.copy(bleStatus = if (enabled) BleStatus.READY else BleStatus.DISABLED) }
    }
    
    /**
     * 권한 요청 결과 처리
     */
    fun handlePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        
        if (granted) {
            viewModelScope.launch {
                _events.emit(BleEvent.ShowMessage("모든 권한이 허용되었습니다"))
                _uiState.update { it.copy(bleStatus = BleStatus.READY) }
            }
        } else {
            viewModelScope.launch {
                _events.emit(BleEvent.ShowMessage("일부 권한이 거부되었습니다. 앱 기능이 제한될 수 있습니다"))
            }
        }
    }
    
    /**
     * BLE 상태 확인
     */
    private fun checkBleStatus() {
        viewModelScope.launch {
            // Bluetooth 어댑터 초기화
            val adapterAvailable = serviceManager.initAdapter()
            
            if (!adapterAvailable) {
                _uiState.update { it.copy(bleStatus = BleStatus.NOT_AVAILABLE) }
                return@launch
            }
            
            // Bluetooth 활성화 상태 확인
            val isEnabled = serviceManager.isBluetoothEnabled()
            _uiState.update { 
                it.copy(bleStatus = if (isEnabled) BleStatus.READY else BleStatus.DISABLED) 
            }
        }
    }
    
    /**
     * GATT 서버 메시지 수집
     */
    private fun collectServerMessages() {
        viewModelScope.launch {
            gattServer.receiveMessages().collectLatest { message ->
                addChatMessage(message)
            }
        }
    }
    
    /**
     * BLE 스캔 시작
     */
    fun startScanning() {
        if (!checkReadyState()) return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isScanning = true) }
                
                // 이전 스캔 결과 초기화
                _scannedDevices.value = emptyList()
                
                // 스캔 시작 및 결과 수집
                scanner.startScan(Constants.APP_SERVICE_UUID).collectLatest { device ->
                    // 중복 기기 체크
                    val existingDevices = _scannedDevices.value.toMutableList()
                    val existingIndex = existingDevices.indexOfFirst { it.deviceAddress == device.deviceAddress }
                    
                    if (existingIndex >= 0) {
                        // 기존 기기 업데이트
                        existingDevices[existingIndex] = device
                    } else {
                        // 새 기기 추가
                        existingDevices.add(device)
                    }
                    
                    // RSSI 기준 내림차순 정렬
                    existingDevices.sortByDescending { it.rssi }
                    
                    _scannedDevices.value = existingDevices
                }
            } catch (e: Exception) {
                Log.e(TAG, "스캔 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("스캔 중 오류: ${e.message}"))
                stopScanning()
            }
        }
    }
    
    /**
     * BLE 스캔 중지
     */
    fun stopScanning() {
        viewModelScope.launch {
            try {
                scanner.stopScan()
                _uiState.update { it.copy(isScanning = false) }
            } catch (e: Exception) {
                Log.e(TAG, "스캔 중지 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("스캔 중지 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * BLE 광고 시작
     */
    fun startAdvertising() {
        if (!checkReadyState()) return
        
        viewModelScope.launch {
            try {
                advertiser.startAdvertising(Constants.APP_SERVICE_UUID).onSuccess {
                    _uiState.update { it.copy(isAdvertising = true) }
                    startGattServer() // 광고와 함께 GATT 서버 시작
                }.onFailure { error ->
                    _events.emit(BleEvent.ShowMessage("광고 시작 실패: ${error.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "광고 시작 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("광고 시작 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * BLE 광고 중지
     */
    fun stopAdvertising() {
        viewModelScope.launch {
            try {
                advertiser.stopAdvertising().onSuccess {
                    _uiState.update { it.copy(isAdvertising = false) }
                    stopGattServer() // 광고 중지와 함께 GATT 서버 중지
                }
            } catch (e: Exception) {
                Log.e(TAG, "광고 중지 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("광고 중지 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * GATT 서버 시작
     */
    private fun startGattServer() {
        viewModelScope.launch {
            try {
                gattServer.startServer().onSuccess {
                    _uiState.update { it.copy(isServerRunning = true) }
                }.onFailure { error ->
                    _events.emit(BleEvent.ShowMessage("GATT 서버 시작 실패: ${error.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "GATT 서버 시작 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("GATT 서버 시작 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * GATT 서버 중지
     */
    private fun stopGattServer() {
        viewModelScope.launch {
            try {
                gattServer.stopServer().onSuccess {
                    _uiState.update { it.copy(isServerRunning = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "GATT 서버 중지 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("GATT 서버 중지 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * 장치에 연결
     *
     * @param device 연결할 스캔된 장치
     */
    fun connectToDevice(device: ScannedDevice) {
        if (!checkReadyState()) return
        
        viewModelScope.launch {
            try {
                // 스캔 중지
                if (_uiState.value.isScanning) {
                    stopScanning()
                }
                
                // GATT 클라이언트 초기화
                initGattClient()
                
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                
                // 장치 연결
                gattClient?.connectToDevice(device)?.collectLatest { state ->
                    when (state) {
                        is GattState.Connecting -> {
                            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                        }
                        is GattState.Connected -> {
                            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED) }
                        }
                        is GattState.ServicesDiscovered -> {
                            // 서비스 발견 상태 (필요 시 처리)
                        }
                        is GattState.Ready -> {
                            _uiState.update { 
                                it.copy(
                                    connectionState = ConnectionState.READY,
                                    connectedDevice = device
                                )
                            }
                            _events.emit(BleEvent.ShowMessage("${device.deviceName ?: device.deviceAddress}에 연결되었습니다"))
                            
                            // 메시지 수신 구독
                            collectClientMessages()
                        }
                        is GattState.Disconnected -> {
                            _uiState.update { 
                                it.copy(
                                    connectionState = ConnectionState.DISCONNECTED,
                                    connectedDevice = null
                                )
                            }
                        }
                        is GattState.Error -> {
                            _uiState.update { it.copy(connectionState = ConnectionState.ERROR) }
                            _events.emit(BleEvent.ShowMessage("연결 오류: ${state.message}"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "장치 연결 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("장치 연결 오류: ${e.message}"))
                _uiState.update { it.copy(connectionState = ConnectionState.ERROR) }
            }
        }
    }
    
    /**
     * 장치 연결 해제
     */
    fun disconnectDevice() {
        viewModelScope.launch {
            try {
                gattClient?.close()
                gattClient = null
                
                _uiState.update { 
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        connectedDevice = null
                    )
                }
                
                _events.emit(BleEvent.ShowMessage("장치 연결이 해제되었습니다"))
            } catch (e: Exception) {
                Log.e(TAG, "장치 연결 해제 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("장치 연결 해제 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * 채팅 메시지 전송
     *
     * @param message 전송할 메시지
     */
    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                
                // 서버 모드로 전송
                if (currentState.isServerRunning && gattServer.getConnectedDevicesCount() > 0) {
                    gattServer.sendMessage(message).onFailure { error ->
                        _events.emit(BleEvent.ShowMessage("메시지 전송 실패: ${error.message}"))
                    }
                }
                // 클라이언트 모드로 전송
                else if (currentState.connectionState == ConnectionState.READY && gattClient != null) {
                    gattClient?.sendMessage(message)?.onFailure { error ->
                        _events.emit(BleEvent.ShowMessage("메시지 전송 실패: ${error.message}"))
                    }
                }
                else {
                    _events.emit(BleEvent.ShowMessage("연결된 장치가 없습니다"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "메시지 전송 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("메시지 전송 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * GATT 클라이언트 초기화
     */
    private fun initGattClient() {
        if (gattClient == null) {
            val bluetoothManager = getApplication<Application>().getSystemService(Application.BLUETOOTH_SERVICE) 
                as android.bluetooth.BluetoothManager
            
            gattClient = BleGattClient(
                getApplication(),
                bluetoothManager.adapter
            )
        }
    }
    
    /**
     * GATT 클라이언트 메시지 수집
     */
    private fun collectClientMessages() {
        gattClient?.let { client ->
            viewModelScope.launch {
                client.receiveMessages().collectLatest { message ->
                    addChatMessage(message)
                }
            }
        }
    }
    
    /**
     * 채팅 메시지 추가
     */
    private fun addChatMessage(message: ChatMessage) {
        val updatedList = _chatMessages.value.toMutableList()
        updatedList.add(message)
        _chatMessages.value = updatedList
    }
    
    /**
     * BLE가 준비되었는지 확인
     */
    private fun checkReadyState(): Boolean {
        val state = _uiState.value
        
        if (state.bleStatus != BleStatus.READY) {
            viewModelScope.launch {
                _events.emit(BleEvent.ShowMessage("Bluetooth가 활성화되지 않았습니다"))
            }
            return false
        }
        
        if (!state.permissionGranted) {
            viewModelScope.launch {
                _events.emit(BleEvent.ShowMessage("필요한 권한이 없습니다"))
            }
            return false
        }
        
        return true
    }
    
    /**
     * 스캔 토글 (시작/중지)
     */
    fun toggleScan() {
        if (uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    /**
     * 광고 토글 (시작/중지)
     */
    fun toggleAdvertising() {
        if (uiState.value.isAdvertising) {
            stopAdvertising()
        } else {
            startAdvertising()
        }
    }
    
    /**
     * GATT 서버 토글 (시작/중지)
     */
    fun toggleGattServer() {
        if (uiState.value.isServerRunning) {
            stopGattServer()
        } else {
            startGattServer()
        }
    }
    
    /**
     * 메시지 전송
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        sendChatMessage(message)
    }
    
    /**
     * GATT 연결 해제
     */
    fun disconnectGatt() {
        disconnectDevice()
    }
    
    /**
     * 앱이 포그라운드로 돌아왔을 때 BLE 작업 재개
     * (화면이 보이게 되었을 때)
     */
    fun resumeOperations() {
        viewModelScope.launch {
            try {
                // 앱이 백그라운드에 있다가 다시 돌아왔을 때 필요한 작업
                
                // GATT 연결 복구 시도 (필요하다면)
                val currentState = uiState.value
                if (currentState.connectionState == ConnectionState.READY || 
                    currentState.connectionState == ConnectionState.CONNECTED) {
                    // 연결이 끊어졌는지 확인 후 재연결 시도
                    gattClient?.let { client ->
                        if (!client.isConnected() && currentState.connectedDevice != null) {
                            _events.emit(BleEvent.ShowMessage("연결 복구 시도 중..."))
                            connectToDevice(currentState.connectedDevice)
                        }
                    }
                }
                
                // 서버 모드였다면 다시 시작
                if (currentState.isServerRunning) {
                    startGattServer()
                }
                
                // 광고 중이었다면 다시 시작
                if (currentState.isAdvertising) {
                    startAdvertising()
                }
                
                // 스캔 중이었다면 다시 시작
                if (currentState.isScanning) {
                    startScanning()
                }
            } catch (e: Exception) {
                Log.e(TAG, "작업 재개 중 오류 발생", e)
                _events.emit(BleEvent.ShowMessage("BLE 작업 재개 중 오류: ${e.message}"))
            }
        }
    }
    
    /**
     * 앱이 백그라운드로 갈 때 BLE 작업 일시 중지
     * (화면이 더 이상 보이지 않을 때)
     */
    fun pauseOperations() {
        viewModelScope.launch {
            try {
                // 필요한 상태 저장
                val wasScanning = uiState.value.isScanning
                val wasAdvertising = uiState.value.isAdvertising
                val wasServerRunning = uiState.value.isServerRunning
                
                // 스캔 중지 (배터리 절약)
                if (wasScanning) {
                    stopScanning()
                    // 사용자가 명시적으로 시작한 스캔은 상태를 유지하여 복귀 시 재개할 수 있도록 함
                    _uiState.update { it.copy(isScanning = wasScanning) }
                }
                
                // 광고는 상황에 따라 중지 여부 결정 (배터리 영향이 있지만 연결성 유지를 위해 필요할 수 있음)
                // 여기서는 예시로 중지하고 상태를 유지하여 복귀 시 재개
                if (wasAdvertising) {
                    stopAdvertising()
                    _uiState.update { it.copy(isAdvertising = wasAdvertising) }
                }
                
                // GATT 서버는 연결된 장치가 있는 경우 유지 (없으면 중지)
                if (wasServerRunning && gattServer.getConnectedDevicesCount() == 0) {
                    stopGattServer()
                    _uiState.update { it.copy(isServerRunning = wasServerRunning) }
                }
                
                // GATT 클라이언트 연결은 유지 (필요시 여기서 해제 가능)
                // 대부분의 경우 백그라운드에서도 연결을 유지하는 것이 권장됨
            } catch (e: Exception) {
                Log.e(TAG, "작업 일시 중지 중 오류 발생", e)
            }
        }
    }
    
    /**
     * ViewModel이 소멸될 때 모든 리소스 해제
     */
    override fun onCleared() {
        super.onCleared()
        
        // 리소스 해제 - runBlocking을 사용하여 확실히 리소스가 정리되도록 함
        kotlinx.coroutines.runBlocking {
            try {
                scanner.stopScan()
                advertiser.stopAdvertising()
                gattServer.stopServer()
                gattClient?.close()
                
                Log.d(TAG, "모든 BLE 리소스 해제 완료")
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel 정리 중 오류 발생", e)
            }
        }
    }
}

/**
 * UI 상태를 나타내는 불변 데이터 클래스
 */
data class BleUiState(
    val bleStatus: BleStatus = BleStatus.UNKNOWN,
    val permissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val isServerRunning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedDevice: ScannedDevice? = null
)

/**
 * Bluetooth 상태 열거형
 */
enum class BleStatus {
    UNKNOWN,       // 초기 상태
    NOT_AVAILABLE, // 기기에서 BLE 지원하지 않음
    DISABLED,      // 비활성화됨
    READY          // 사용 준비됨
}

/**
 * UI 이벤트 sealed 클래스
 */
sealed class BleEvent {
    data class ShowMessage(val message: String) : BleEvent()
    data class RequestBluetoothEnable(val intent: Intent) : BleEvent()
    data class RequestPermissions(val permissions: Array<String>) : BleEvent()
} 