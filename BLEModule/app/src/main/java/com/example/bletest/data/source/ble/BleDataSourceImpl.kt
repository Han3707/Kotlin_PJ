package com.example.bletest.data.source.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.bletest.utils.Constants
import com.example.bletest.utils.PermissionHelper
import com.example.bletest.bluetooth.BluetoothManager
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleDataSourceImpl"

/**
 * BleDataSource 인터페이스의 구현체
 * BluetoothManager를 사용하여 실제 BLE 작업을 수행합니다.
 */
@SuppressLint("MissingPermission") // 코드 내에서 권한 체크를 수행하므로 경고 무시
@Singleton
class BleDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val bluetoothManager: android.bluetooth.BluetoothManager
) : BleDataSource {

    // 커스텀 BluetoothManager를 사용하여 BLE 기본 기능 위임
    private val customBleManager = BluetoothManager(context)
    
    // StateFlow 프록시 - BluetoothManager의 StateFlow를 직접 사용
    override val scanResults: StateFlow<List<ScanResultData>> get() = customBleManager.scanResults
    override val isScanning: StateFlow<Boolean> get() = customBleManager.isScanning
    override val connectionState: StateFlow<DeviceConnectionState> get() = customBleManager.connectionState
    override val messages: StateFlow<List<MessageData>> get() = customBleManager.messages

    // --- Bluetooth 관련 객체 ---
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    
    // --- GATT 클라이언트 관리 ---
    private val gattClients = ConcurrentHashMap<String, BluetoothGatt>()
    
    // --- 상태 관리를 위한 Flow ---
    private val _advertiseEvents = MutableSharedFlow<AdvertiseEvent>(replay = 0)
    override val advertiseEvents: Flow<AdvertiseEvent> = _advertiseEvents.asSharedFlow()
    
    private val _serverEvents = MutableSharedFlow<GattServerEvent>(replay = 0)
    override val serverEvents: Flow<GattServerEvent> = _serverEvents.asSharedFlow()
    
    private val _clientEvents = MutableSharedFlow<GattClientEvent>(replay = 0)
    override val clientEvents: Flow<GattClientEvent> = _clientEvents.asSharedFlow()
    
    init {
        // Bluetooth 객체 초기화
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }
    
    // 권한 체크 헬퍼 함수
    private fun hasRequiredPermissions(): Boolean {
        return PermissionHelper.hasRequiredPermissions(context)
    }
    
    // Flow 이벤트 발행 헬퍼 함수 (코루틴 내에서 비동기로 발행)
    private fun <T> emitEvent(flow: MutableSharedFlow<T>, event: T) {
        scope.launch {
            flow.emit(event)
        }
    }
    
    // 로그 헬퍼 함수
    private fun logd(message: String) {
        Log.d(TAG, message)
    }
    
    private fun loge(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }
    
    // ---------- BluetoothManager에 위임되는 기본 BLE 기능 ----------
    
    override fun startScan() {
        customBleManager.startScan()
    }
    
    override fun stopScan() {
        customBleManager.stopScan()
    }

    override fun connect(device: BluetoothDevice) {
        customBleManager.connect(device)
    }
    
    override fun disconnect() {
        customBleManager.disconnect()
    }

    override fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        return customBleManager.sendMessage(targetId, messageType, content)
    }
    
    override fun close() {
        customBleManager.close()
        stopServer()
        stopAdvertising()
        
        gattClients.forEach { (address, gatt) ->
            try {
                gatt.close()
                logd("GATT 클라이언트 종료: $address")
            } catch (e: Exception) {
                loge("GATT 클라이언트 종료 실패", e)
            }
        }
        gattClients.clear()
    }

    // ---------- 광고 관련 기능 ----------
    
    // 광고 콜백
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            logd("광고 시작 성공")
            emitEvent(_advertiseEvents, AdvertiseEvent.Started)
        }

        override fun onStartFailure(errorCode: Int) {
            loge("광고 시작 실패: 에러 코드 $errorCode")
            emitEvent(_advertiseEvents, AdvertiseEvent.Failed(errorCode))
        }
    }
    
    // 광고 시작
    override fun startAdvertising() {
        if (!hasRequiredPermissions()) {
            loge("광고 권한 없음")
            return
        }
        
        bluetoothLeAdvertiser?.let { advertiser ->
            try {
                // 광고 설정
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // 저지연 모드
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // 높은 전송 출력
                    .setConnectable(true) // 연결 가능
                    .build()
                
                // 광고 데이터
                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true) // 기기 이름 포함
                    .addServiceUuid(Constants.MESH_SERVICE_UUID) // 메시 서비스 UUID
                    .build()
                
                // 광고 시작
                advertiser.startAdvertising(settings, data, advertiseCallback)
                logd("BLE 광고 시작 요청됨")
            } catch (e: Exception) {
                loge("광고 시작 실패", e)
            }
        } ?: run {
            loge("BluetoothLeAdvertiser 없음")
        }
    }
    
    // 광고 중지
    override fun stopAdvertising() {
        if (!hasRequiredPermissions()) {
            loge("광고 권한 없음")
            return
        }
        
        bluetoothLeAdvertiser?.let { advertiser ->
            try {
                advertiser.stopAdvertising(advertiseCallback)
                logd("BLE 광고 중지 요청됨")
            } catch (e: Exception) {
                loge("광고 중지 실패", e)
            }
        }
    }
    
    // ---------- GATT 서버 관련 기능 ----------
    
    // GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            val stateName = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "연결됨"
                BluetoothProfile.STATE_DISCONNECTED -> "연결 해제됨"
                BluetoothProfile.STATE_CONNECTING -> "연결 중"
                BluetoothProfile.STATE_DISCONNECTING -> "연결 해제 중"
                else -> "알 수 없음 ($newState)"
            }
            
            logd("GATT 서버 연결 상태 변경: $address, 상태: $status, 새 상태: $stateName")
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 클라이언트 연결됨
                emitEvent(_serverEvents, GattServerEvent.DeviceConnected(device))
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 클라이언트 연결 해제됨
                emitEvent(_serverEvents, GattServerEvent.DeviceDisconnected(device))
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            logd("특성 읽기 요청: ${device.address}, charUUID: ${characteristic.uuid}")
            
            // 이벤트 발행
            emitEvent(_serverEvents, GattServerEvent.CharacteristicReadRequest(
                device, requestId, offset, characteristic
            ))
            
            // 기본 응답 (빈 데이터)
            bluetoothGattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0)
            )
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            logd("특성 쓰기 요청: ${device.address}, charUUID: ${characteristic.uuid}, " +
                "값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_serverEvents, GattServerEvent.CharacteristicWriteRequest(
                device, requestId, characteristic, preparedWrite, responseNeeded, offset, value
            ))
            
            // 응답이 필요하면 성공 응답 전송
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
                )
            }
        }
        
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            logd("디스크립터 읽기 요청: ${device.address}, descUUID: ${descriptor.uuid}")
            
            // 이벤트 발행
            emitEvent(_serverEvents, GattServerEvent.DescriptorReadRequest(
                device, requestId, offset, descriptor
            ))
            
            // CCCD 읽기 요청인 경우 기본값 반환
            if (descriptor.uuid == Constants.CCCD_UUID) {
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                )
            } else {
                // 다른 디스크립터는 빈 데이터로 응답
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0)
                )
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            logd("디스크립터 쓰기 요청: ${device.address}, descUUID: ${descriptor.uuid}, " +
                "값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_serverEvents, GattServerEvent.DescriptorWriteRequest(
                device, requestId, descriptor, preparedWrite, responseNeeded, offset, value
            ))
            
            // 응답이 필요하면 성공 응답 전송
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
                )
            }
        }
        
        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            val statusText = if (status == BluetoothGatt.GATT_SUCCESS) "성공" else "실패 ($status)"
            logd("알림 전송 ${statusText}: ${device.address}")
            
            // 이벤트 발행
            emitEvent(_serverEvents, GattServerEvent.NotificationSent(device, status))
        }
    }
    
    // GATT 서버 시작 (서비스 생성 포함)
    override fun startServer() {
        if (!hasRequiredPermissions()) {
            loge("GATT 서버 권한 없음")
            return
        }
        
        // 이미 초기화된 서버가 있으면 반환
        if (bluetoothGattServer != null) {
            logd("GATT 서버가 이미 초기화됨")
            return
        }
        
        // GATT 서버 초기화
        bluetoothManager.let { manager ->
            try {
                // GATT 서버 인스턴스 생성
                bluetoothGattServer = manager.openGattServer(context, gattServerCallback)
                
                // GATT 서비스 생성
                val service = createMeshService()
                
                // 서비스 추가
                val serviceAdded = bluetoothGattServer?.addService(service)
                
                logd("GATT 서버 시작. 서비스 추가: $serviceAdded")
            } catch (e: Exception) {
                loge("GATT 서버 시작 실패", e)
                stopServer()
            }
        } ?: run {
            loge("BluetoothManager 없음")
        }
    }
    
    // GATT 서버 중지
    override fun stopServer() {
        if (!hasRequiredPermissions()) {
            loge("GATT 서버 권한 없음")
            return
        }
        
        bluetoothGattServer?.let { server ->
            try {
                server.close()
                bluetoothGattServer = null
                logd("GATT 서버 종료됨")
            } catch (e: Exception) {
                loge("GATT 서버 종료 실패", e)
            }
        }
    }
    
    // 메시 서비스 생성 헬퍼 함수
    private fun createMeshService(): BluetoothGattService {
        // 메시 서비스 인스턴스 생성 (GATT 서비스 유형: PRIMARY)
        val service = BluetoothGattService(
            Constants.MESH_SERVICE_UUID.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // 메시 특성 생성 (읽기, 쓰기, 알림 권한)
        val meshCharacteristic = BluetoothGattCharacteristic(
            Constants.MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // CCCD (Client Characteristic Configuration Descriptor) 추가
        val cccd = BluetoothGattDescriptor(
            Constants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE
        )
        meshCharacteristic.addDescriptor(cccd)
        
        // 서비스에 특성 추가
        service.addCharacteristic(meshCharacteristic)
        
        return service
    }
    
    // 특성 변경 알림 전송
    override fun notifyCharacteristicChanged(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray,
        deviceAddress: String?
    ): Boolean {
        if (!hasRequiredPermissions()) {
            loge("알림 권한 없음")
            return false
        }
        
        bluetoothGattServer?.let { server ->
            try {
                // 서비스와 특성 찾기
                val service = server.getService(serviceUuid)
                
                if (service == null) {
                    loge("알림 전송 실패: 서비스를 찾을 수 없음 ($serviceUuid)")
                    return false
                }
                
                val characteristic = service.getCharacteristic(characteristicUuid)
                
                if (characteristic == null) {
                    loge("알림 전송 실패: 특성을 찾을 수 없음 ($characteristicUuid)")
                    return false
                }
                
                // 특성 값 설정
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    characteristic.setValue(value)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                }
                
                // 특정 기기에만 알림 전송 또는 연결된 모든 기기에 알림 전송
                return if (deviceAddress != null) {
                    // 특정 기기에 알림 전송
                    bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)?.let { devices ->
                        val device = devices.find { it.address == deviceAddress }
                        
                        if (device != null) {
                            server.notifyCharacteristicChanged(device, characteristic, false)
                            logd("특정 기기에 알림 전송: $deviceAddress (${value.size}바이트)")
                            true
                        } else {
                            loge("알림 전송 실패: 연결된 기기 중 주소가 일치하는 기기 없음 ($deviceAddress)")
                            false
                        }
                    } ?: run {
                        loge("알림 전송 실패: 연결된 기기 목록을 가져올 수 없음")
                        false
                    }
                } else {
                    // 모든 연결된 기기에 알림 전송
                    var success = true
                    bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)?.forEach { device ->
                        val result = server.notifyCharacteristicChanged(device, characteristic, false)
                        if (result) {
                            logd("알림 전송: ${device.address} (${value.size}바이트)")
                        } else {
                            loge("알림 전송 실패: ${device.address}")
                            success = false
                        }
                    }
                    
                    if (success) {
                        logd("모든 연결된 기기에 알림 전송 성공")
                    } else {
                        logd("일부 기기에 알림 전송 실패")
                    }
                    
                    success
                }
            } catch (e: Exception) {
                loge("알림 전송 중 예외 발생", e)
                return false
            }
        } ?: run {
            loge("알림 전송 실패: GATT 서버가 초기화되지 않음")
            return false
        }
    }

    // ---------- GATT 클라이언트 관련 기능 (선택적 구현 - 필요 시 사용) ----------
    
    // 다음 함수들은 GATT 클라이언트 기능을 제공하지만 대부분 필요 없을 수 있음
    // BluetoothManager에서 이미 기본 기능 제공
    
    override fun connectGatt(address: String) {
        if (!hasRequiredPermissions()) {
            loge("GATT 연결 권한 없음")
            return
        }
        
        // 이미 BluetoothManager에서 connect() 지원하므로 이 메서드 불필요
        logd("참고: connectGatt 대신 connect(device) 메서드 사용 권장")
        
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                connect(device)
            } else {
                loge("장치를 찾을 수 없음: $address")
            }
        } catch (e: Exception) {
            loge("GATT 연결 오류", e)
        }
    }

    override fun disconnectGatt(address: String) {
        // 기본 구현은 단일 장치만 관리하므로 특정 주소 무시
        disconnect()
    }
    
    override fun closeGatt(address: String) {
        // 해당 주소의 클라이언트 종료 (구현 생략)
        // 필요 시 구현
    }
    
    override fun discoverServices(address: String) {
        // 서비스 검색 (구현 생략)
        // 필요 시 구현
    }
    
    override fun writeCharacteristic(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        value: ByteArray
    ): Boolean {
        // 특성 쓰기 (구현 생략)
        // 필요 시 구현
        return false
    }
    
    override fun writeDescriptor(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        descriptorUuid: UUID, 
        value: ByteArray
    ): Boolean {
        // 디스크립터 쓰기 (구현 생략)
        // 필요 시 구현
        return false
    }
    
    override fun setCharacteristicNotification(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        enable: Boolean
    ): Boolean {
        // 알림 설정 (구현 생략)
        // 필요 시 구현
        return false
    }
} 