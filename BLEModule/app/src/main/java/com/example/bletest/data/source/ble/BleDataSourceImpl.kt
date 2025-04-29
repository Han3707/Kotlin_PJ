package com.example.bletest.data.source.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        try {
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        } catch (e: Exception) {
            loge("Bluetooth 초기화 중 오류", e)
        }
    }
    
    // 권한 체크 헬퍼 함수
    private fun hasRequiredPermissions(): Boolean {
        return try {
            PermissionHelper.hasRequiredPermissions(context)
        } catch (e: Exception) {
            loge("권한 확인 중 오류", e)
            false
        }
    }
    
    // Flow 이벤트 발행 헬퍼 함수 (코루틴 내에서 비동기로 발행)
    private fun <T> emitEvent(flow: MutableSharedFlow<T>, event: T) {
        scope.launch {
            try {
                flow.emit(event)
            } catch (e: Exception) {
                loge("이벤트 발행 중 오류", e)
            }
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
        if (!hasRequiredPermissions()) {
            loge("스캔 권한 없음 - 권한이 없어도 앱 실행은 유지")
            emitEvent(_clientEvents, GattClientEvent.PermissionDenied("BLUETOOTH_SCAN"))
            return
        }
        
        try {
            customBleManager.startScan()
        } catch (e: Exception) {
            loge("스캔 시작 중 오류", e)
        }
    }
    
    override fun stopScan() {
        try {
            customBleManager.stopScan()
        } catch (e: Exception) {
            loge("스캔 중지 중 오류", e)
        }
    }

    override fun connect(device: BluetoothDevice) {
        if (!hasRequiredPermissions()) {
            loge("연결 권한 없음 - 권한이 없어도 앱 실행은 유지")
            emitEvent(_clientEvents, GattClientEvent.PermissionDenied("BLUETOOTH_CONNECT"))
            return
        }
        
        try {
            customBleManager.connect(device)
        } catch (e: Exception) {
            loge("기기 연결 중 오류", e)
        }
    }
    
    override fun disconnect() {
        try {
            customBleManager.disconnect()
        } catch (e: Exception) {
            loge("기기 연결 해제 중 오류", e)
        }
    }

    override fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        if (!hasRequiredPermissions()) {
            loge("메시지 전송 권한 없음 - 권한이 없어도 앱 실행은 유지")
            return false
        }
        
        return try {
            customBleManager.sendMessage(targetId, messageType, content)
        } catch (e: Exception) {
            loge("메시지 전송 중 오류", e)
            false
        }
    }
    
    override fun close() {
        try {
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
        } catch (e: Exception) {
            loge("BleDataSource 종료 중 오류", e)
        }
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
            loge("광고 권한 없음 - 필요한 권한을 확인해주세요")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                loge("Android 12+ 필수 권한: BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT")
                loge("BLUETOOTH_ADVERTISE 권한 상태: ${PermissionHelper.hasPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE)}")
                loge("BLUETOOTH_CONNECT 권한 상태: ${PermissionHelper.hasPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)}")
            } else {
                loge("Android 11- 필수 권한: ACCESS_FINE_LOCATION")
                loge("ACCESS_FINE_LOCATION 권한 상태: ${PermissionHelper.hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)}")
            }
            emitEvent(_advertiseEvents, AdvertiseEvent.Failed(-1))
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
                logd("BLE 광고 시작 요청됨 - 서비스 UUID: ${Constants.MESH_SERVICE_UUID}")
            } catch (e: Exception) {
                loge("광고 시작 실패", e)
                emitEvent(_advertiseEvents, AdvertiseEvent.Failed(-1))
            }
        } ?: run {
            loge("BluetoothLeAdvertiser 없음")
            emitEvent(_advertiseEvents, AdvertiseEvent.Failed(-1))
        }
    }
    
    // 광고 중지
    override fun stopAdvertising() {
        try {
            val advertiser = bluetoothLeAdvertiser ?: run {
                logd("광고 중지: BluetoothLeAdvertiser 없음")
                return
            }
            
            advertiser.stopAdvertising(advertiseCallback)
            logd("BLE 광고 중지됨")
        } catch (e: Exception) {
            loge("광고 중지 중 오류", e)
        }
    }
    
    // ---------- GATT 서버 관련 기능 ----------
    
    // GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val deviceAddress = device?.address ?: "unknown"
            logd("GATT 서버 연결 상태 변경: $deviceAddress, 상태=$status, 새상태=$newState")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emitEvent(_serverEvents, GattServerEvent.DeviceConnected(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    emitEvent(_serverEvents, GattServerEvent.DeviceDisconnected(device))
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logd("서비스 추가됨: ${service?.uuid}")
                emitEvent(_serverEvents, GattServerEvent.ServiceAdded(service))
            } else {
                loge("서비스 추가 실패: 상태=$status")
                emitEvent(_serverEvents, GattServerEvent.ServiceAddFailed(status))
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            logd("특성 읽기 요청: ${device?.address}, ${characteristic?.uuid}")
            
            if (device == null || characteristic == null) {
                loge("특성 읽기 요청 처리 실패: 디바이스 또는 특성이 null")
                return
            }
            
            val value = customBleManager.handleCharacteristicReadRequest(characteristic)
            
            if (value != null) {
                if (bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value) == true) {
                    logd("읽기 요청에 응답 성공")
                    emitEvent(_serverEvents, GattServerEvent.CharacteristicRead(device, characteristic))
                } else {
                    loge("읽기 요청에 응답 실패")
                }
            } else {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                loge("읽기 요청에 데이터 없음으로 응답")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            logd("특성 쓰기 요청: ${device?.address}, ${characteristic?.uuid}, 데이터=${value?.size ?: 0}바이트")
            
            if (device == null || characteristic == null || value == null) {
                loge("특성 쓰기 요청 처리 실패: 디바이스, 특성 또는 값이 null")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }
            
            val success = customBleManager.handleCharacteristicWriteRequest(characteristic, value, device)
            
            if (responseNeeded) {
                val status = if (success) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                if (bluetoothGattServer?.sendResponse(device, requestId, status, offset, null) == true) {
                    logd("쓰기 요청에 응답 성공")
                } else {
                    loge("쓰기 요청에 응답 실패")
                }
            }
            
            if (success) {
                emitEvent(_serverEvents, GattServerEvent.CharacteristicWrite(device, characteristic, value))
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            logd("디스크립터 읽기 요청: ${device?.address}, ${descriptor?.uuid}")
            
            if (device == null || descriptor == null) {
                loge("디스크립터 읽기 요청 처리 실패: 디바이스 또는 디스크립터가 null")
                return
            }
            
            val value = customBleManager.handleDescriptorReadRequest(descriptor)
            
            if (value != null) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                logd("디스크립터 읽기 요청에 응답")
            } else {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                loge("디스크립터 읽기 요청에 데이터 없음으로 응답")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            logd("디스크립터 쓰기 요청: ${device?.address}, ${descriptor?.uuid}")
            
            if (device == null || descriptor == null || value == null) {
                loge("디스크립터 쓰기 요청 처리 실패: 디바이스, 디스크립터 또는 값이 null")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }
            
            val success = customBleManager.handleDescriptorWriteRequest(descriptor, value, device)
            
            if (responseNeeded) {
                val status = if (success) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                if (bluetoothGattServer?.sendResponse(device, requestId, status, offset, null) == true) {
                    logd("디스크립터 쓰기 요청에 응답 성공")
                } else {
                    loge("디스크립터 쓰기 요청에 응답 실패")
                }
            }
            
            if (success) {
                emitEvent(_serverEvents, GattServerEvent.DescriptorWrite(device, descriptor, value))
                
                // 알림 활성화 여부 체크
                if (descriptor.uuid == Constants.CCCD_UUID) {
                    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        val characteristic = descriptor.characteristic
                        logd("알림 활성화: ${characteristic.uuid}")
                        emitEvent(_serverEvents, GattServerEvent.NotificationsEnabled(device, characteristic))
                    } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        val characteristic = descriptor.characteristic
                        logd("알림 비활성화: ${characteristic.uuid}")
                        emitEvent(_serverEvents, GattServerEvent.NotificationsDisabled(device, characteristic))
                    }
                }
            }
        }
    }
    
    // GATT 서버 시작
    override fun startServer() {
        if (!hasRequiredPermissions()) {
            loge("GATT 서버 시작 권한 없음 - 권한이 없어도 앱 실행은 유지")
            emitEvent(_serverEvents, GattServerEvent.ServerStartFailed("권한 없음"))
            return
        }
        
        try {
            // 이미 실행 중인 서버가 있으면 중지
            if (bluetoothGattServer != null) {
                stopServer()
            }
            
            // GATT 서버 시작
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            if (bluetoothGattServer == null) {
                loge("GATT 서버를 시작할 수 없음")
                emitEvent(_serverEvents, GattServerEvent.ServerStartFailed("서버 생성 실패"))
                return
            }
            
            // 서비스 추가
            val meshService = customBleManager.createMeshService()
            val addServiceResult = bluetoothGattServer?.addService(meshService) ?: false
            
            if (addServiceResult) {
                logd("GATT 서버 시작됨 및 메시 서비스 추가됨")
                emitEvent(_serverEvents, GattServerEvent.ServerStarted)
            } else {
                loge("GATT 서버에 메시 서비스 추가 실패")
                emitEvent(_serverEvents, GattServerEvent.ServerStartFailed("서비스 추가 실패"))
                stopServer()
            }
        } catch (e: Exception) {
            loge("GATT 서버 시작 중 오류", e)
            emitEvent(_serverEvents, GattServerEvent.ServerStartFailed(e.message ?: "알 수 없는 오류"))
        }
    }
    
    // GATT 서버 중지
    override fun stopServer() {
        try {
            bluetoothGattServer?.let { server ->
                server.clearServices()
                server.close()
                bluetoothGattServer = null
                logd("GATT 서버 중지됨")
                emitEvent(_serverEvents, GattServerEvent.ServerStopped)
            }
        } catch (e: Exception) {
            loge("GATT 서버 중지 중 오류", e)
        }
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
                    val result = characteristic.setValue(value)
                    if (!result) {
                        loge("특성 값 설정 실패")
                        return false
                    }
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
                            @Suppress("DEPRECATION")
                            val result = server.notifyCharacteristicChanged(device, characteristic, false)
                            logd("특정 기기에 알림 전송: $deviceAddress (${value.size}바이트)")
                            result
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
                        @Suppress("DEPRECATION")
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

    /**
     * 장치 ID 설정
     * 
     * @param id 새로운 장치 ID
     */
    override fun setDeviceId(id: String) {
        customBleManager.setDeviceId(id)
        logd("BleDataSourceImpl: 장치 ID가 설정됨: $id")
    }
} 