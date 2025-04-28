package com.example.bletest.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ServiceState
import com.example.bletest.data.model.ScanResultData

import com.example.bletest.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

private const val TAG = "BleViewModel"

/**
 * BLE 기능을 위한 ViewModel
 * BleService와 상호작용하여 UI 상태를 관리합니다.
 */
@HiltViewModel
class BleViewModel @Inject constructor(
    private val bleRepository: BleRepository
) : AndroidViewModel(Application()) {

    // UI 상태 관리
    private val _uiState = MutableStateFlow(BleMeshUiState())
    val uiState: StateFlow<BleMeshUiState> = _uiState.asStateFlow()
    
    // 스캔 결과
    val scanResults = bleRepository.scanResults
    
    // 연결 상태
    val connectionState = bleRepository.connectionState
    
    // 메시지
    val messages = bleRepository.messages
    
    // 스캔 중인지 여부
    val isScanning = bleRepository.isScanning
    
    // 메시 서비스 실행 상태
    private val _meshRunningState = MutableStateFlow(false)
    val meshRunningState: StateFlow<Boolean> = _meshRunningState.asStateFlow()
    
    // 로그 메시지
    private val _logMessages = MutableStateFlow<String>("")
    val logMessages: StateFlow<String> = _logMessages.asStateFlow()
    
    // 로그 이벤트 Flow
    private val _logEvents = MutableSharedFlow<String>(replay = 0)
    val logEvents = _logEvents.asSharedFlow()
    
    // 알려진 기기 목록
    private val _knownDevices = MutableStateFlow<Set<String>>(emptySet())
    val knownDevices: StateFlow<Set<String>> = _knownDevices.asStateFlow()
    
    // 수신된 메시지
    private val _receivedMessages = MutableStateFlow<MessageData?>(null)
    val receivedMessages: StateFlow<MessageData?> = _receivedMessages.asStateFlow()
    
    // 장치 ID
    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()
    
    init {
        // ViewModel이 생성되면 Flow 수집 시작
        collectFlows()
    }
    
    /**
     * StateFlow들을 수집하여 UI 상태 업데이트
     */
    private fun collectFlows() {
        // 스캔 결과 수집
        viewModelScope.launch {
            scanResults.collect { results ->
                _uiState.update { it.copy(scanResults = results) }
            }
        }
        
        // 스캔 상태 수집
        viewModelScope.launch {
            isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        
        // 연결 상태 수집
        viewModelScope.launch {
            connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        
        // 메시지 수집
        viewModelScope.launch {
            messages.collect { messagesList ->
                _uiState.update { it.copy(messages = messagesList) }
                
                // 새 메시지가 있으면 수신 이벤트 발생
                if (messagesList.isNotEmpty()) {
                    val lastMessage = messagesList.lastOrNull()
                    lastMessage?.let {
                        _receivedMessages.value = it
                        
                        // Ping이면 자동으로 Pong 응답
                        if (it.content?.equals("ping", ignoreCase = true) == true) {
                            log("Ping 수신 from ${it.sourceId}. Pong 응답 전송")
                            sendPongMessage(it.sourceId)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 장치 ID 설정
     */
    fun setDeviceId(id: String) {
        _deviceId.value = id
        _knownDevices.update { 
            val newSet = mutableSetOf<String>()
            newSet.add(id)
            newSet
        }
    }
    
    /**
     * 메시 서비스가 실행 중인지 확인
     */
    fun isMeshRunning(): Boolean {
        return _meshRunningState.value
    }
    
    /**
     * 메시 서비스 시작
     */
    fun startMesh(): Boolean {
        // 디바이스 ID가 설정되어 있는지 확인
        if (_deviceId.value.isEmpty()) {
            log("디바이스 ID가 설정되지 않았습니다.")
            return false
        }
        
        try {
            log("메시 서비스 시작 중... ID: ${_deviceId.value}")
            viewModelScope.launch {
                val result = bleRepository.startMesh(_deviceId.value)
                
                if (result) {
                    _meshRunningState.value = true
                    log("메시 서비스 시작 성공")
                } else {
                    log("메시 서비스 시작 실패")
                }
            }
            
            // 일단 시작 프로세스가 시작되었으므로 true 반환
            return true
        } catch (e: Exception) {
            log("메시 서비스 시작 중 오류: ${e.message}")
            return false
        }
    }
    
    /**
     * 메시 서비스 중지
     */
    fun stopMesh() {
        try {
            log("메시 서비스 중지 중...")
            viewModelScope.launch {
                bleRepository.stopMesh()
                _meshRunningState.value = false
                _knownDevices.value = emptySet()
                log("메시 서비스가 중지되었습니다.")
            }
        } catch (e: Exception) {
            log("메시 서비스 중지 중 오류: ${e.message}")
        }
    }
    
    /**
     * Ping 메시지 전송
     */
    fun sendPingMessage(targetId: String) {
        viewModelScope.launch {
            try {
                // 알려진 기기 확인 없이 항상 메시지 전송
                log("Ping 전송 -> $targetId")
                
                // 메시지를 보내고 결과를 받음
                val success = bleRepository.sendMessage(targetId, MessageType.TEXT, "ping")
                
                // 결과 기록
                if (success) {
                    log("Ping 메시지 전송 성공")
                    
                    // targetId를 알려진 기기 목록에 추가
                    _knownDevices.update { 
                        val newSet = it.toMutableSet()
                        newSet.add(targetId)
                        newSet
                    }
                } else {
                    log("Ping 메시지 전송 실패")
                }
                
                /* 원래 코드 (주석 처리)
                // 현재 알려진 기기 확인
                if (_knownDevices.value.contains(targetId) || targetId == deviceId) {
                    log("Ping 전송 -> $targetId")
                    bleRepository.sendMessage(targetId, MessageType.TEXT, "ping")
                } else {
                    log("알 수 없는 목표 ID: $targetId. 알려진 기기: ${_knownDevices.value}")
                }
                */
            } catch (e: Exception) {
                log("Ping 전송 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 로그 메시지 추가
     */
    fun addLog(message: String) {
        // 기존 StateFlow 값 업데이트 (이전 코드와의 호환성)
        _logMessages.value = message
        
        // 로그 이벤트 발행 (비동기)
        viewModelScope.launch {
            _logEvents.emit(message)
        }
    }
    
    // private에서 public으로 변경하고 위의 public 메서드로 리디렉션
    private fun log(message: String) {
        addLog(message)
    }
    
    /**
     * BLE 스캔 시작
     */
    fun startScanning() {
        bleRepository.startScan()
    }
    
    /**
     * BLE 스캔 중지
     */
    fun stopScanning() {
        bleRepository.stopScan()
    }
    
    /**
     * 기기 연결
     */
    fun connectDevice(device: BluetoothDevice) {
        bleRepository.connect(device)
    }
    
    /**
     * 기기 연결 해제
     */
    fun disconnectDevice() {
        bleRepository.disconnect()
    }
    
    /**
     * 메시지 전송
     */
    fun sendMessage(targetId: String?, content: String) {
        _uiState.update { it.copy(isSending = true) }
        
        viewModelScope.launch {
            try {
                // MessageType을 직접 전달하여 더 명확하게 처리
                val success = bleRepository.sendMessage(targetId, MessageType.TEXT, content)
                
                // 새로운 MessageData 구조를 활용하여 결과 처리
                val result = MessageData(
                    targetId = targetId,
                    content = content,
                    messageType = MessageType.TEXT,
                    isSent = success,
                    error = if (!success) "메시지 전송 실패" else null,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true
                )
                
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        messageSendResult = MessageSendResult(
                            success = success,
                            timestamp = System.currentTimeMillis(),
                            errorMessage = result.error
                        )
                    )
                }
            } catch (e: Exception) {
                // 오류 상황도 MessageData 구조로 처리 가능
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        messageSendResult = MessageSendResult(
                            success = false,
                            timestamp = System.currentTimeMillis(),
                            errorMessage = "메시지 전송 오류: ${e.message}"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * 메시지 목록 지우기
     */
    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList()) }
    }
    
    /**
     * ViewModel 정리
     */
    override fun onCleared() {
        viewModelScope.launch {
            if (_meshRunningState.value) {
                stopMesh()
            }
        }
        super.onCleared()
    }

    /**
     * Pong 메시지 전송
     */
    private fun sendPongMessage(targetId: String) {
        viewModelScope.launch {
            try {
                log("Pong 전송 -> $targetId")
                val success = bleRepository.sendMessage(targetId, MessageType.TEXT, "pong")
                
                if (success) {
                    log("Pong 메시지 전송 성공")
                    
                    // targetId를 알려진 기기 목록에 추가
                    _knownDevices.update { 
                        val newSet = it.toMutableSet()
                        newSet.add(targetId)
                        newSet
                    }
                } else {
                    log("Pong 메시지 전송 실패")
                }
            } catch (e: Exception) {
                log("Pong 전송 오류: ${e.message}")
            }
        }
    }
}

/**
 * BLE 메시 UI 상태
 */
data class BleMeshUiState(
    val deviceId: String = "",
    val isScanning: Boolean = false,
    val scanResults: List<ScanResultData> = emptyList(),
    val connectionState: DeviceConnectionState = DeviceConnectionState(),
    val messages: List<MessageData> = emptyList(),
    val isSending: Boolean = false,
    val messageSendResult: MessageSendResult? = null,
    val isMeshRunning: Boolean = false
)

/**
 * 메시지 전송 결과
 */
data class MessageSendResult(
    val success: Boolean,
    val timestamp: Long,
    val errorMessage: String? = null
) 