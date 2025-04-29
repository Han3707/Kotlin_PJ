package com.example.bletest.data.repository

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.data.source.ble.BleDataSource
import com.example.bletest.data.source.ble.BleDataSourceImpl
import com.example.bletest.service.BleServiceConnection
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val bleDataSource: BleDataSource,
    @ApplicationContext private val context: Context
) : BleRepository {

    // 고유 장치 ID (앱 인스턴스마다 다름)
    private var deviceId = UUID.randomUUID().toString()
    
    // 메시 네트워크 실행 상태
    private val _isMeshRunning = MutableStateFlow(false)
    
    // 서비스 연결 상태
    private val _isServiceConnected = MutableStateFlow(false)

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
    
    override fun setDeviceId(id: String) {
        this.deviceId = id
        // BleDataSource에도 적용
        (bleDataSource as? BleDataSourceImpl)?.setDeviceId(id)
    }
    
    /**
     * 메시 서비스 시작 (startMeshNetworking 으로 대체되었으므로 제거)
     */
    /* // 함수 제거
    override suspend fun startMesh(deviceId: String): Boolean {
        try {
            // 이전 상태 정리
            stopMesh()
            
            // 사용자 지정 장치 ID 적용
            this.deviceId = deviceId
            
            // BluetoothManager에도 ID 설정
            (bleDataSource as? BleDataSourceImpl)?.setDeviceId(deviceId)
            
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
    */
    
    /**
     * 메시 서비스 중지 (stopMeshNetworking 으로 대체되었으므로 제거)
     */
    /* // 함수 제거
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
    */
    
    override fun isMeshRunning(): Boolean {
        return _isMeshRunning.value
    }
    
    override suspend fun startMeshNetworking(deviceId: String): Boolean {
        try {
            // 이전 상태 정리
            stopMeshNetworking()
            
            // 장치 ID 설정
            this.deviceId = deviceId
            
            // BluetoothManager에도 ID 설정
            (bleDataSource as? BleDataSourceImpl)?.setDeviceId(deviceId)
            
            // BleService에 위임하여 메시 네트워킹 시작
            withContext(Dispatchers.IO) {
                // 서비스에서 공용 채팅방 기능 시작
                // 여기서는 서비스 바인딩이 되어 있다고 가정하고,
                // BleServiceConnection이 제공하는 서비스 인스턴스를 통해 호출
                val serviceInstance = BleServiceConnection.getService()
                
                // Null 체크 로그 추가
                if (serviceInstance == null) {
                    Log.e("BleRepositoryImpl", "startMeshNetworking 실패: BleService 인스턴스가 null입니다.")
                    return@withContext false // 서비스가 없으면 실패 반환
                }
                
                // 서비스 인스턴스가 null이 아닐 경우에만 호출
                val result = serviceInstance.startMeshNetworking()
                _isMeshRunning.value = result
                return@withContext result
            }
            
            return _isMeshRunning.value
        } catch (e: Exception) {
            Log.e("BleRepositoryImpl", "startMeshNetworking 중 오류 발생", e) // <<< 예외 발생 시 로그 추가
            _isMeshRunning.value = false // 실패 시 상태 업데이트 추가
            return false
        }
    }
    
    override suspend fun stopMeshNetworking(): Boolean {
        try {
            withContext(Dispatchers.IO) {
                // 서비스에서 공용 채팅방 기능 중지
                val serviceInstance = BleServiceConnection.getService()
                
                if (serviceInstance != null) {
                    serviceInstance.stopMeshNetworking()
                }
            }
            
            // 상태 업데이트
            _isMeshRunning.value = false
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun sendBroadcastMessage(content: String): Boolean {
        return withContext(Dispatchers.IO) {
            val serviceInstance = BleServiceConnection.getService()
            
            if (serviceInstance != null) {
                serviceInstance.sendBroadcastMessage(content)
            } else {
                bleDataSource.sendMessage(null, MessageType.TEXT, content)
            }
        }
    }
    
    /**
     * BLE 서비스 초기화
     */
    override fun initBleService() {
        // 서비스 바인딩
        try {
            BleServiceConnection.bindService(context)
            _isServiceConnected.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * BLE 서비스가 연결되어 있는지 확인
     */
    override fun isServiceConnected(): Boolean {
        return BleServiceConnection.getService() != null
    }
    
    override fun getDeviceName(): String {
        // 필요한 경우 구현
        return "Android 기기"
    }
} 