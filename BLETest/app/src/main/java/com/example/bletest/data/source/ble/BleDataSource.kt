package com.example.bletest.data.source.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * BLE 데이터 소스 인터페이스
 * BLE 관련 작업을 추상화하여 리포지토리 레이어에 제공합니다.
 */
interface BleDataSource {
    /**
     * BLE 스캔 결과를 나타내는 StateFlow
     */
    val scanResults: StateFlow<List<ScanResultData>>

    /**
     * 현재 BLE 연결 상태를 나타내는 StateFlow
     */
    val connectionState: StateFlow<DeviceConnectionState>

    /**
     * 수신된 메시지 목록을 나타내는 StateFlow
     */
    val messages: StateFlow<List<MessageData>>

    /**
     * 현재 스캔 중인지 여부를 나타내는 StateFlow
     */
    val isScanning: StateFlow<Boolean>

    /**
     * BLE 장치 스캔 시작
     */
    fun startScan()

    /**
     * BLE 장치 스캔 중지
     */
    fun stopScan()

    /**
     * 특정 BLE 장치에 연결
     *
     * @param device 연결할 BluetoothDevice
     */
    fun connect(device: BluetoothDevice)

    /**
     * BLE 장치 연결 해제
     */
    fun disconnect()

    /**
     * 리소스 해제
     */
    fun close()

    /**
     * 메시지 전송
     *
     * @param targetId 대상 장치 ID (null이면 브로드캐스트)
     * @param messageType 메시지 타입
     * @param content 메시지 내용
     * @return 전송 성공 여부
     */
    fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean

    // --- 광고 관련 ---
    /**
     * BLE 광고 시작
     */
    fun startAdvertising()
    
    /**
     * BLE 광고 중지
     */
    fun stopAdvertising()
    
    /**
     * 광고 관련 이벤트 Flow
     */
    val advertiseEvents: Flow<AdvertiseEvent>
    
    // --- GATT 서버 관련 (Peripheral 역할) ---
    /**
     * GATT 서버 시작
     */
    fun startServer()
    
    /**
     * GATT 서버 중지
     */
    fun stopServer()
    
    /**
     * 특정 특성에 대한 알림 전송
     * 
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid 특성 UUID
     * @param value 알림으로 전송할 값
     * @param deviceAddress 알림을 전송할 기기의 주소 (null이면 모든 연결된 기기에게 전송)
     * @return 성공 여부
     */
    fun notifyCharacteristicChanged(
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        value: ByteArray,
        deviceAddress: String? = null
    ): Boolean
    
    /**
     * GATT 서버 이벤트 Flow
     */
    val serverEvents: Flow<GattServerEvent>
    
    // --- GATT 클라이언트 관련 (Central 역할) ---
    /**
     * 특정 기기에 GATT 연결 시도
     * 
     * @param address 연결할 기기의 MAC 주소
     */
    fun connectGatt(address: String)
    
    /**
     * 특정 기기와의 GATT 연결 해제
     * 
     * @param address 연결 해제할 기기의 MAC 주소
     */
    fun disconnectGatt(address: String)
    
    /**
     * 특정 기기와의 GATT 연결 종료 및 리소스 해제
     * 
     * @param address 리소스를 해제할 기기의 MAC 주소
     */
    fun closeGatt(address: String)
    
    /**
     * GATT 서비스 검색
     * 
     * @param address 서비스를 검색할 기기의 MAC 주소
     */
    fun discoverServices(address: String)
    
    /**
     * 특성 값 쓰기
     * 
     * @param address 특성을 쓸 기기의 MAC 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid 특성 UUID
     * @param value 쓸 값
     * @return 작업 요청 성공 여부
     */
    fun writeCharacteristic(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        value: ByteArray
    ): Boolean
    
    /**
     * 디스크립터 값 쓰기
     * 
     * @param address 디스크립터를 쓸 기기의 MAC 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid 특성 UUID
     * @param descriptorUuid 디스크립터 UUID
     * @param value 쓸 값
     * @return 작업 요청 성공 여부
     */
    fun writeDescriptor(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        descriptorUuid: UUID, 
        value: ByteArray
    ): Boolean
    
    /**
     * 특성 알림 활성화/비활성화
     * 
     * @param address 알림을 설정할 기기의 MAC 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid 특성 UUID
     * @param enable 활성화 여부
     * @return 작업 요청 성공 여부
     */
    fun setCharacteristicNotification(
        address: String, 
        serviceUuid: UUID, 
        characteristicUuid: UUID, 
        enable: Boolean
    ): Boolean
    
    /**
     * GATT 클라이언트 이벤트 Flow
     */
    val clientEvents: Flow<GattClientEvent>
}

/**
 * 광고 관련 이벤트를 나타내는 sealed class
 */
sealed class AdvertiseEvent {
    data object Started : AdvertiseEvent()
    data class Failed(val errorCode: Int) : AdvertiseEvent()
}

/**
 * GATT 서버 이벤트를 나타내는 sealed class
 */
sealed class GattServerEvent {
    data class DeviceConnected(val device: BluetoothDevice) : GattServerEvent()
    data class DeviceDisconnected(val device: BluetoothDevice) : GattServerEvent()
    data class CharacteristicReadRequest(
        val device: BluetoothDevice,
        val requestId: Int,
        val offset: Int,
        val characteristic: BluetoothGattCharacteristic
    ) : GattServerEvent()
    data class CharacteristicWriteRequest(
        val device: BluetoothDevice,
        val requestId: Int,
        val characteristic: BluetoothGattCharacteristic,
        val preparedWrite: Boolean,
        val responseNeeded: Boolean,
        val offset: Int,
        val value: ByteArray
    ) : GattServerEvent()
    data class DescriptorReadRequest(
        val device: BluetoothDevice,
        val requestId: Int,
        val offset: Int,
        val descriptor: BluetoothGattDescriptor
    ) : GattServerEvent()
    data class DescriptorWriteRequest(
        val device: BluetoothDevice,
        val requestId: Int,
        val descriptor: BluetoothGattDescriptor,
        val preparedWrite: Boolean,
        val responseNeeded: Boolean,
        val offset: Int,
        val value: ByteArray
    ) : GattServerEvent()
    data class NotificationSent(
        val device: BluetoothDevice,
        val status: Int
    ) : GattServerEvent()
}

/**
 * GATT 클라이언트 이벤트를 나타내는 sealed class
 */
sealed class GattClientEvent {
    data class ConnectionStateChange(
        val address: String,
        val status: Int,
        val newState: Int
    ) : GattClientEvent()
    data class ServicesDiscovered(
        val address: String,
        val status: Int,
        val services: List<BluetoothGattService>
    ) : GattClientEvent()
    data class CharacteristicRead(
        val address: String,
        val characteristic: BluetoothGattCharacteristic,
        val status: Int,
        val value: ByteArray
    ) : GattClientEvent()
    data class CharacteristicWrite(
        val address: String,
        val characteristic: BluetoothGattCharacteristic,
        val status: Int
    ) : GattClientEvent()
    data class CharacteristicChanged(
        val address: String,
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray
    ) : GattClientEvent()
    data class DescriptorRead(
        val address: String,
        val descriptor: BluetoothGattDescriptor,
        val status: Int,
        val value: ByteArray
    ) : GattClientEvent()
    data class DescriptorWrite(
        val address: String,
        val descriptor: BluetoothGattDescriptor,
        val status: Int
    ) : GattClientEvent()
} 