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
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @ApplicationScope private val coroutineScope: CoroutineScope
) : BleDataSource {

    private val bluetoothManager = BluetoothManager(context)

    override val scanResults: StateFlow<List<ScanResultData>> = bluetoothManager.scanResults
    
    override val connectionState: StateFlow<DeviceConnectionState> = bluetoothManager.connectionState
    
    override val messages: StateFlow<List<MessageData>> = bluetoothManager.messages
    
    override val isScanning: StateFlow<Boolean> = bluetoothManager.isScanning

    // --- Bluetooth 관련 객체 ---
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    
    // --- 연결된 GATT 클라이언트 관리 ---
    private val gattClients = ConcurrentHashMap<String, BluetoothGatt>()
    
    // --- 상태 관리를 위한 Flow ---
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()
    
    private val _scanResults = MutableSharedFlow<ScanResult>(replay = 0)
    override val scanResults: Flow<ScanResult> = _scanResults.asSharedFlow()
    
    private val _advertiseEvents = MutableSharedFlow<AdvertiseEvent>(replay = 0)
    override val advertiseEvents: Flow<AdvertiseEvent> = _advertiseEvents.asSharedFlow()
    
    private val _serverEvents = MutableSharedFlow<GattServerEvent>(replay = 0)
    override val serverEvents: Flow<GattServerEvent> = _serverEvents.asSharedFlow()
    
    private val _clientEvents = MutableSharedFlow<GattClientEvent>(replay = 0)
    override val clientEvents: Flow<GattClientEvent> = _clientEvents.asSharedFlow()
    
    // --- 연결 시도 중인 주소 추적 ---
    private val connectingAddresses = ConcurrentHashMap<String, Boolean>()
    
    init {
        // Bluetooth 객체 초기화
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }
    
    // 권한 체크 헬퍼 함수
    private fun hasRequiredPermissions(): Boolean {
        return PermissionHelper.hasRequiredPermissions(context)
    }
    
    // Flow 이벤트 발행 헬퍼 함수 (코루틴 내에서 비동기로 발행)
    private fun <T> emitEvent(flow: MutableSharedFlow<T>, event: T) {
        coroutineScope.launch {
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
    
    // --- BLE 스캔 관련 ---
    // 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            logd("스캔 결과: ${result.device.address}, RSSI: ${result.rssi}")
            // Flow를 통해 스캔 결과 발행
            emitEvent(_scanResults, result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            logd("배치 스캔 결과: ${results.size}개")
            for (result in results) {
                emitEvent(_scanResults, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            loge("스캔 실패: 에러 코드 $errorCode")
            _isScanning.value = false
        }
    }
    
    // 스캔 시작
    override fun startScan() {
        bluetoothManager.startScan()
    }
    
    // 스캔 중지
    override fun stopScan() {
        bluetoothManager.stopScan()
    }
    
    // --- BLE 광고 관련 ---
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
    
    // --- GATT 서버 관련 (Peripheral 역할) ---
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

    // --- GATT 클라이언트 관련 (Central 역할) ---
    // GATT 클라이언트 콜백
    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            val stateName = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "연결됨"
                BluetoothProfile.STATE_DISCONNECTED -> "연결 해제됨"
                BluetoothProfile.STATE_CONNECTING -> "연결 중"
                BluetoothProfile.STATE_DISCONNECTING -> "연결 해제 중"
                else -> "알 수 없음 ($newState)"
            }
            
            logd("GATT 클라이언트 연결 상태 변경: $address, 상태: $status, 새 상태: $stateName")
            
            // 연결 시도 중 상태 해제
            connectingAddresses.remove(address)
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.ConnectionStateChange(address, status, newState))
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 연결 성공 시 서비스 검색 자동 시작
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logd("서비스 검색 시작: $address")
                    gatt.discoverServices()
                }
                
                // GATT 클라이언트 맵에 추가
                gattClients[address] = gatt
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 자원 해제 및 맵에서 제거
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    logd("연결 실패 또는 연결 해제: $address, 상태: $status")
                    closeGatt(address)
                } else {
                    logd("정상적으로 연결 해제됨: $address")
                    gattClients.remove(address)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                logd("서비스 검색 완료: $address, ${services.size}개 서비스 발견")
                
                // 메시 서비스 찾기
                val meshService = services.find { it.uuid == Constants.MESH_SERVICE_UUID.uuid }
                
                if (meshService != null) {
                    logd("메시 서비스 발견: $address")
                    
                    // 메시 특성 찾기
                    val meshCharacteristic = meshService.getCharacteristic(Constants.MESH_CHARACTERISTIC_UUID)
                    
                    if (meshCharacteristic != null) {
                        logd("메시 특성 발견: $address")
                        
                        // 알림 활성화
                        setCharacteristicNotification(address, Constants.MESH_SERVICE_UUID.uuid, Constants.MESH_CHARACTERISTIC_UUID, true)
                    } else {
                        loge("메시 특성을 찾을 수 없음: $address")
                    }
                } else {
                    loge("메시 서비스를 찾을 수 없음: $address")
                }
                
                // 이벤트 발행
                emitEvent(_clientEvents, GattClientEvent.ServicesDiscovered(address, status, services))
            } else {
                loge("서비스 검색 실패: $address, 상태: $status")
                // 이벤트 발행
                emitEvent(_clientEvents, GattClientEvent.ServicesDiscovered(address, status, emptyList()))
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
            value: ByteArray
        ) {
            val address = gatt.device.address
            logd("특성 읽기 완료: $address, 상태: $status, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.CharacteristicRead(address, characteristic, status, value))
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val address = gatt.device.address
            logd("특성 읽기 완료 (레거시): $address, 상태: $status, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.CharacteristicRead(address, characteristic, status, value))
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            val statusText = if (status == BluetoothGatt.GATT_SUCCESS) "성공" else "실패 ($status)"
            logd("특성 쓰기 완료: $address, 상태: $statusText")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.CharacteristicWrite(address, characteristic, status))
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val address = gatt.device.address
            logd("특성 변경됨: $address, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.CharacteristicChanged(address, characteristic, value))
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val address = gatt.device.address
            val value = characteristic.value ?: ByteArray(0)
            logd("특성 변경됨 (레거시): $address, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.CharacteristicChanged(address, characteristic, value))
        }
        
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            val address = gatt.device.address
            logd("디스크립터 읽기 완료: $address, 상태: $status, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.DescriptorRead(address, descriptor, status, value))
        }
        
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val address = gatt.device.address
            val value = descriptor.value ?: ByteArray(0)
            logd("디스크립터 읽기 완료 (레거시): $address, 상태: $status, 값 크기: ${value.size}바이트")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.DescriptorRead(address, descriptor, status, value))
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val address = gatt.device.address
            val statusText = if (status == BluetoothGatt.GATT_SUCCESS) "성공" else "실패 ($status)"
            logd("디스크립터 쓰기 완료: $address, 상태: $statusText")
            
            // 이벤트 발행
            emitEvent(_clientEvents, GattClientEvent.DescriptorWrite(address, descriptor, status))
            
            // CCCD 쓰기 완료 시 로그 추가
            if (descriptor.uuid == Constants.CCCD_UUID) {
                logd("CCCD 설정 완료: $address")
            }
        }
    }

    // --- GATT 클라이언트 함수 구현 ---
    
    // GATT 연결
    override fun connect(device: BluetoothDevice) {
        if (!hasRequiredPermissions()) {
            loge("GATT 연결 권한 없음")
            return
        }
        
        // 이미 연결된 기기거나 연결 시도 중인 경우
        if (gattClients.containsKey(device.address)) {
            logd("이미 연결된 기기: ${device.address}")
            return
        }
        
        if (connectingAddresses.getOrDefault(device.address, false)) {
            logd("이미 연결 시도 중인 기기: ${device.address}")
            return
        }
        
        // 연결 시도 중 상태로 표시
        connectingAddresses[device.address] = true
        
        // GATT 연결 시작
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 이상: LE 전용 모드
            device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            // Android 6.0 미만
            device.connectGatt(context, false, gattClientCallback)
        }
        
        logd("GATT 연결 시도: ${device.address}")
    }
    
    // GATT 연결 해제
    override fun disconnect() {
        if (!hasRequiredPermissions()) {
            loge("GATT 연결 해제 권한 없음")
            return
        }
        
        gattClients.forEach { (address, gatt) ->
            try {
                gatt.disconnect()
                logd("GATT 연결 해제 요청: $address")
            } catch (e: Exception) {
                loge("GATT 연결 해제 실패", e)
            }
        }
    }
    
    // GATT 클라이언트 종료 및 자원 해제
    override fun close() {
        if (!hasRequiredPermissions()) {
            loge("GATT 종료 권한 없음")
            return
        }
        
        gattClients.forEach { (address, gatt) ->
            try {
                gatt.close()
                logd("GATT 클라이언트 종료: $address")
            } catch (e: Exception) {
                loge("GATT 클라이언트 종료 실패", e)
            }
        }
        
        connectingAddresses.clear()
    }
    
    // 서비스 검색
    override fun discoverServices(address: String) {
        if (!hasRequiredPermissions()) {
            loge("서비스 검색 권한 없음")
            return
        }
        
        gattClients[address]?.let { gatt ->
            try {
                val result = gatt.discoverServices()
                logd("서비스 검색 요청: $address, 요청 성공: $result")
            } catch (e: Exception) {
                loge("서비스 검색 실패", e)
            }
        } ?: run {
            logd("연결된 GATT 클라이언트 없음: $address")
        }
    }
    
    // 특성 값 쓰기
    override fun writeCharacteristic(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        value: ByteArray
    ): Boolean {
        if (!hasRequiredPermissions()) {
            loge("특성 쓰기 권한 없음")
            return false
        }
        
        gattClients[address]?.let { gatt ->
            try {
                // 서비스와 특성 찾기
                val service = gatt.getService(serviceUuid)
                
                if (service == null) {
                    loge("특성 쓰기 실패: 서비스를 찾을 수 없음 ($serviceUuid)")
                    return false
                }
                
                val characteristic = service.getCharacteristic(characteristicUuid)
                
                if (characteristic == null) {
                    loge("특성 쓰기 실패: 특성을 찾을 수 없음 ($characteristicUuid)")
                    return false
                }
                
                // 특성 값 쓰기
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13 이상: 신규 API 사용
                    gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    // Android 13 미만: 레거시 API 사용
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                    BluetoothStatusCodes.SUCCESS
                }
                
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result == BluetoothStatusCodes.SUCCESS
                } else {
                    result
                }
                
                if (success) {
                    logd("특성 쓰기 요청 성공: $address (${value.size}바이트)")
                } else {
                    loge("특성 쓰기 요청 실패: $address, 결과: $result")
                }
                
                return success
            } catch (e: Exception) {
                loge("특성 쓰기 중 예외 발생", e)
                return false
            }
        } ?: run {
            logd("연결된 GATT 클라이언트 없음: $address")
            return false
        }
    }
    
    // 디스크립터 값 쓰기
    override fun writeDescriptor(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        descriptorUuid: UUID, 
        value: ByteArray
    ): Boolean {
        if (!hasRequiredPermissions()) {
            loge("디스크립터 쓰기 권한 없음")
            return false
        }
        
        gattClients[address]?.let { gatt ->
            try {
                // 서비스, 특성, 디스크립터 찾기
                val service = gatt.getService(serviceUuid)
                
                if (service == null) {
                    loge("디스크립터 쓰기 실패: 서비스를 찾을 수 없음 ($serviceUuid)")
                    return false
                }
                
                val characteristic = service.getCharacteristic(characteristicUuid)
                
                if (characteristic == null) {
                    loge("디스크립터 쓰기 실패: 특성을 찾을 수 없음 ($characteristicUuid)")
                    return false
                }
                
                val descriptor = characteristic.getDescriptor(descriptorUuid)
                
                if (descriptor == null) {
                    loge("디스크립터 쓰기 실패: 디스크립터를 찾을 수 없음 ($descriptorUuid)")
                    return false
                }
                
                // 디스크립터 값 쓰기
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13 이상: 신규 API 사용
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    // Android 13 미만: 레거시 API 사용
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                    BluetoothStatusCodes.SUCCESS
                }
                
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result == BluetoothStatusCodes.SUCCESS
                } else {
                    result
                }
                
                if (success) {
                    logd("디스크립터 쓰기 요청 성공: $address (${value.size}바이트)")
                } else {
                    loge("디스크립터 쓰기 요청 실패: $address, 결과: $result")
                }
                
                return success
            } catch (e: Exception) {
                loge("디스크립터 쓰기 중 예외 발생", e)
                return false
            }
        } ?: run {
            logd("연결된 GATT 클라이언트 없음: $address")
            return false
        }
    }
    
    // 특성 알림 설정/해제
    override fun setCharacteristicNotification(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        enable: Boolean
    ): Boolean {
        if (!hasRequiredPermissions()) {
            loge("알림 설정 권한 없음")
            return false
        }
        
        gattClients[address]?.let { gatt ->
            try {
                // 서비스와 특성 찾기
                val service = gatt.getService(serviceUuid)
                
                if (service == null) {
                    loge("알림 설정 실패: 서비스를 찾을 수 없음 ($serviceUuid)")
                    return false
                }
                
                val characteristic = service.getCharacteristic(characteristicUuid)
                
                if (characteristic == null) {
                    loge("알림 설정 실패: 특성을 찾을 수 없음 ($characteristicUuid)")
                    return false
                }
                
                // 1. 로컬 알림 활성화/비활성화
                val notificationResult = gatt.setCharacteristicNotification(characteristic, enable)
                
                if (!notificationResult) {
                    loge("알림 설정 실패 (setCharacteristicNotification): $address")
                    return false
                }
                
                // 2. CCCD (Client Characteristic Configuration Descriptor) 쓰기
                val descriptor = characteristic.getDescriptor(Constants.CCCD_UUID)
                
                if (descriptor == null) {
                    loge("알림 설정 실패: CCCD를 찾을 수 없음")
                    return false
                }
                
                // 알림 활성화/비활성화 값 설정
                val value = if (enable) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                
                // 디스크립터 쓰기
                val writeResult = writeDescriptor(address, serviceUuid, characteristicUuid, Constants.CCCD_UUID, value)
                
                if (!writeResult) {
                    loge("알림 설정 실패 (CCCD 쓰기): $address")
                    return false
                }
                
                val action = if (enable) "활성화" else "비활성화" 
                logd("특성 알림 $action 요청 성공: $address")
                
                return true
            } catch (e: Exception) {
                loge("알림 설정 중 예외 발생", e)
                return false
            }
        } ?: run {
            logd("연결된 GATT 클라이언트 없음: $address")
            return false
        }
    }
} 