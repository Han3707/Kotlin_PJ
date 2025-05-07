package com.ssafy.lantern.data.source.ble.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import java.util.UUID

class GattClientManager(
    private val context: Context,
    var onConnectionStateChange: (device: BluetoothDevice, status: Int, newState: Int) -> Unit = { _, _, _ -> },
    var onMessageReceived: (device: BluetoothDevice, message: String) -> Unit = { _, _ -> }
) {

    // GattServer에 요청하기 위한 객체 (연결별로 관리)
    private val activeGatts = mutableMapOf<String, BluetoothGatt>()
    
    /**
     * 연결 상태 변경 리스너 설정
     */
    fun setConnectionStateChangeListener(listener: (device: BluetoothDevice, status: Int, newState: Int) -> Unit) {
        onConnectionStateChange = listener
    }
    
    /**
     * 메시지 수신 리스너 설정
     */
    fun setMessageReceivedListener(listener: (device: BluetoothDevice, message: String) -> Unit) {
        onMessageReceived = listener
    }

    @SuppressLint("MissingPermission") // 권한 확인은 ViewModel/Screen에서 수행
    fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {

            if (activeGatts.containsKey(device.address)) {
                Log.d("GattClientManager", "Already connected or connecting to: ${device.address}")
                // 이미 연결 시도 중이거나 연결된 경우 추가 연결 시도 방지 (필요시 재연결 로직 추가)
                return
            }
            Log.d("GattClientManager", "Connecting to GATT server on device: ${device.address}")
            // autoConnect=false 로 설정하여 즉시 연결 시도
            val gatt = device.connectGatt(context, false, gattCallback)
            activeGatts[device.address] = gatt // 연결 시도 중인 GATT 객체 저장
        } else {
            Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT")
            // 권한 없음 처리 (ViewModel에서 처리)
        }
    }

    // 오버로드된 connect 메서드 - ViewModel에서 필요
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, autoConnect: Boolean): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                
                if (activeGatts.containsKey(device.address)) {
                    // 이미 연결된 경우 성공으로 간주
                    return true
                }
                
                Log.d("GattClientManager", "Connecting to device: ${device.address}, autoConnect: $autoConnect")
                val gatt = device.connectGatt(context, autoConnect, gattCallback)
                activeGatts[device.address] = gatt
                true
            } else {
                Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT")
                false
            }
        } catch (e: Exception) {
            Log.e("GattClientManager", "Error connecting to device", e)
            false
        }
    }

    // 특정 기기와의 연결 해제
    @SuppressLint("MissingPermission")
    fun disconnectDevice(deviceAddress: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {
            activeGatts[deviceAddress]?.let {
                Log.d("GattClientManager", "Disconnecting from device: $deviceAddress")
                it.disconnect()
                // close()는 onConnectionStateChange 콜백에서 STATE_DISCONNECTED 확인 후 호출
            }
        } else {
            Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT")
        }
    }

    // 모든 기기와의 연결 해제
    fun disconnectAll() {
        activeGatts.keys.toList().forEach { address ->
            disconnectDevice(address)
        }
    }

    // GATT 콜백 구현
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            Log.d("GattClientManager", "onConnectionStateChange: Device ${device.address}, Status $status, NewState $newState")
            // 외부 콜백 호출
            onConnectionStateChange(device, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattClientManager", "Connected to GATT server on ${device.address}.")
                // 서비스 탐색 시작
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices()
                } else {
                     Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT for discoverServices")
                     disconnectDevice(device.address) // 권한 없으면 연결 해제
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattClientManager", "Disconnected from GATT server on ${device.address}.")
                // GATT 객체 정리
                gatt.close() // GATT 리소스 해제
                activeGatts.remove(device.address)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("GattClientManager", "Services discovered for ${gatt.device.address}")
                // 채팅 서비스 및 특성 찾기 (UUID는 GattServerManager와 동일하게)
                val service = gatt.getService(GattServerManager.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(GattServerManager.CHARACTERISTIC_UUID)

                if (characteristic != null) {
                     if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        // Notification 활성화 요청
                        gatt.setCharacteristicNotification(characteristic, true)
                        // CCCD (Client Characteristic Configuration Descriptor) 설정
                        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                            Log.i("GattClientManager", "Notification enabled for characteristic on ${gatt.device.address}")
                        } else {
                            Log.e("GattClientManager", "CCCD descriptor not found for characteristic on ${gatt.device.address}")
                        }
                     } else {
                         Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT for setCharacteristicNotification")
                         disconnectDevice(gatt.device.address)
                     }
                } else {
                    Log.e("GattClientManager", "Chat characteristic not found on ${gatt.device.address}")
                    disconnectDevice(gatt.device.address) // 필요한 특성 없으면 연결 해제
                }
            } else {
                Log.w("GattClientManager", "onServicesDiscovered received error status: $status for ${gatt.device.address}")
            }
        }

        // 특성 읽기 결과 (필요시 사용)
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { /* ... */ }

        // 특성 쓰기 결과 (필요시 사용)
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { /* ... */ }

        // 특성 변경 알림 수신 (메시지 수신)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val device = gatt.device
            val messageBytes = characteristic.value
            val messageString = String(messageBytes, Charsets.UTF_8)
            Log.d("GattClientManager", "Message received from ${device.address}: $messageString")
            // 외부 콜백 호출
            onMessageReceived(device, messageString)
        }

        // 디스크립터 쓰기 결과 (Notification 설정 완료 확인 등)
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
             if (status == BluetoothGatt.GATT_SUCCESS) {
                 Log.d("GattClientManager", "Descriptor write successful for ${gatt.device.address}")
                 // Notification 설정 완료 후 작업 수행 가능
             } else {
                 Log.e("GattClientManager", "Descriptor write failed with status $status for ${gatt.device.address}")
             }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}