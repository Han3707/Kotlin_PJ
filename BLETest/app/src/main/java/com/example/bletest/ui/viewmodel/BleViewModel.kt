package com.example.bletest.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletest.data.model.ConnectionStatus
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageRequest
import com.example.bletest.data.model.MessageResult
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.NetworkNode
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.service.BleService
import com.example.bletest.service.ServiceState
import com.example.bletest.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    
    // 장치 ID
    private val deviceId = bleRepository.getDeviceId()
    
    init {
        // 초기 상태 설정
        _uiState.update { 
            it.copy(
                deviceId = deviceId
            )
        }
        
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
            }
        }
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
                val success = bleRepository.sendMessage(targetId, MessageType.TEXT, content)
                
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        messageSendResult = MessageSendResult(
                            success = success,
                            timestamp = System.currentTimeMillis(),
                            errorMessage = if (!success) "메시지 전송 실패" else null
                        )
                    )
                }
            } catch (e: Exception) {
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
        bleRepository.close()
        super.onCleared()
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