package com.example.bletest.data.repository

import android.bluetooth.BluetoothDevice
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.data.source.ble.BleDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BleRepository 인터페이스 구현체
 */
@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleDataSource: BleDataSource
) : BleRepository {

    // 고유 장치 ID (앱 인스턴스마다 다름)
    private val deviceId = UUID.randomUUID().toString()
    
    // 메시 네트워크 실행 상태
    private val _isMeshRunning = MutableStateFlow(false)

    override val scanResults: StateFlow<List<ScanResultData>> = bleDataSource.scanResults
    
    override val connectionState: StateFlow<DeviceConnectionState> = bleDataSource.connectionState
    
    override val messages: StateFlow<List<MessageData>> = bleDataSource.messages
    
    override val isScanning: StateFlow<Boolean> = bleDataSource.isScanning
    
    // 연결 상태 맵 (주소 -> 연결 상태)
    override val connectionStates: Flow<Map<String, ConnectionState>> = connectionState.map { state ->
        mapOf(state.address to state.state)
    }
    
    override fun startScan() {
        bleDataSource.startScan()
    }

    override fun stopScan() {
        bleDataSource.stopScan()
    }

    override fun connect(device: BluetoothDevice) {
        bleDataSource.connect(device)
    }

    override fun disconnect() {
        bleDataSource.disconnect()
    }

    override fun close() {
        bleDataSource.close()
    }

    override suspend fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        return withContext(Dispatchers.IO) {
            bleDataSource.sendMessage(targetId, messageType, content)
        }
    }
    
    override fun getDeviceId(): String {
        return deviceId
    }
    
    override suspend fun startMesh(deviceId: String): Boolean {
        try {
            // 이전 상태 정리
            stopMesh()
            
            // 스캔 시작
            bleDataSource.startScan()
            
            // 광고 시작
            bleDataSource.startAdvertising()
            
            // GATT 서버 시작
            bleDataSource.startServer()
            
            // 상태 업데이트
            _isMeshRunning.value = true
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun stopMesh(): Boolean {
        try {
            // 스캔 중지
            bleDataSource.stopScan()
            
            // 광고 중지
            bleDataSource.stopAdvertising()
            
            // GATT 서버 중지
            bleDataSource.stopServer()
            
            // 상태 업데이트
            _isMeshRunning.value = false
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override fun isMeshRunning(): Boolean {
        return _isMeshRunning.value
    }
} 