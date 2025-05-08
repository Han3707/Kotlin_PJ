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
import android.os.Handler
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
    
    // 재연결 시도 횟수를 추적하는 맵
    private val connectionRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_COUNT = 3
    private val connectionHandler = Handler(android.os.Looper.getMainLooper())
    private val connectionTimeouts = mutableMapOf<String, Runnable>()
    
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

    /**
     * 지정된 기기와의 GATT 연결 생성
     */
    @SuppressLint("MissingPermission") // 권한 확인은 ViewModel/Screen에서 수행
    fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {

            // 이미 연결되어 있거나 재연결 시도 중인 경우
            if (activeGatts.containsKey(device.address)) {
                Log.d("GattClientManager", "Already connected or connecting to: ${device.address}")
                return
            }
            
            // 연결 시도 횟수 초기화
            connectionRetryCount[device.address] = 0
            
            // 연결 시도
            initiateConnection(device)
        } else {
            Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT")
        }
    }

    /**
     * 실제 연결 시도를 수행하는 메소드
     */
    @SuppressLint("MissingPermission")
    private fun initiateConnection(device: BluetoothDevice) {
        try {
            Log.d("GattClientManager", "Connecting to GATT server on device: ${device.address}")
            // autoConnect=false로 설정하여 즉시 연결 시도
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            activeGatts[device.address] = gatt
            
            // 연결 타임아웃 설정 (10초)
            setConnectionTimeout(device)
        } catch (e: Exception) {
            Log.e("GattClientManager", "Exception during GATT connection attempt", e)
            // 연결 실패 처리 및 재시도
            handleConnectionFailure(device, BluetoothGatt.GATT_FAILURE)
        }
    }

    /**
     * 연결 타임아웃 설정
     */
    private fun setConnectionTimeout(device: BluetoothDevice) {
        val timeout = Runnable {
            Log.w("GattClientManager", "Connection timeout for device: ${device.address}")
            // 타임아웃으로 인한 연결 실패 처리
            handleConnectionFailure(device, 133) // GATT_ERROR 코드 사용
        }
        
        connectionTimeouts[device.address] = timeout
        connectionHandler.postDelayed(timeout, 10000) // 10초 타임아웃
    }

    /**
     * 연결 실패 처리 및 재시도
     */
    private fun handleConnectionFailure(device: BluetoothDevice, status: Int) {
        val currentRetryCount = connectionRetryCount[device.address] ?: 0
        val newRetryCount = currentRetryCount + 1
        
        // 재시도 카운트 기록
        connectionRetryCount[device.address] = newRetryCount
        
        if (newRetryCount <= MAX_RETRY_COUNT) {
            // 재시도 간격 증가 (지수 백오프 방식: 점점 더 길게 기다림)
            val delayMs = when (newRetryCount) {
                1 -> 2000L // 2초
                2 -> 4000L // 4초
                else -> 6000L // 6초
            }
            
            Log.i("GattClientManager", "Retrying connection to ${device.address} (Attempt $newRetryCount/$MAX_RETRY_COUNT) in ${delayMs/1000} seconds")
            
            // 지연 후 재연결 시도
            connectionHandler.postDelayed({
                // 이미 연결되어 있지 않은 경우에만 재연결 시도
                if (!activeGatts.containsKey(device.address)) {
                    initiateConnection(device)
                }
            }, delayMs)
        } else {
            Log.e("GattClientManager", "Failed to connect to ${device.address} after $MAX_RETRY_COUNT attempts")
            // 최대 재시도 횟수 초과 - 콜백 통해 실패 알림
            onConnectionStateChange(device, status, BluetoothProfile.STATE_DISCONNECTED)
            connectionRetryCount.remove(device.address)
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
                
                // 연결 시도 횟수 초기화
                connectionRetryCount[device.address] = 0
                
                Log.d("GattClientManager", "Connecting to device: ${device.address}, autoConnect: $autoConnect")
                val gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
                activeGatts[device.address] = gatt
                
                // 연결 타임아웃 설정
                setConnectionTimeout(device)
                
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
            
            // 연결 타임아웃 제거
            connectionTimeouts[deviceAddress]?.let {
                connectionHandler.removeCallbacks(it)
                connectionTimeouts.remove(deviceAddress)
            }
            
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
            
            // 연결 타임아웃 제거
            connectionTimeouts[device.address]?.let {
                connectionHandler.removeCallbacks(it)
                connectionTimeouts.remove(device.address)
            }
            
            // 성공적으로 연결된 경우
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("GattClientManager", "Connected to GATT server on ${device.address}.")
                    // 재시도 카운트 초기화
                    connectionRetryCount.remove(device.address)
                    
                    // 서비스 탐색 시작 전에 약간의 지연 추가 (안정성 향상)
                    connectionHandler.postDelayed({
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            if (activeGatts.containsKey(device.address)) {
                                gatt.discoverServices()
                            }
                        } else {
                            Log.e("GattClientManager", "Permission denied: BLUETOOTH_CONNECT for discoverServices")
                            disconnectDevice(device.address) // 권한 없으면 연결 해제
                        }
                    }, 1000) // 1초 지연
                    
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("GattClientManager", "Disconnected from GATT server on ${device.address}.")
                    // GATT 객체 정리
                    gatt.close() // GATT 리소스 해제
                    activeGatts.remove(device.address)
                }
                
                // 외부 콜백 호출
                onConnectionStateChange(device, status, newState)
                
            } else { // 연결 실패/오류
                // 특별한 오류 코드 처리 추가
                val errorDetails = when (status) {
                    133 -> "기기가 범위를 벗어났거나 GATT 서비스를 지원하지 않습니다"
                    8 -> "기기에 연결할 권한이 없습니다"
                    22 -> "블루투스 스택 내부 오류"
                    62 -> "원격 기기에서 연결을 종료함"
                    else -> "알 수 없는 오류 ($status)"
                }
                
                Log.e("GattClientManager", "Connection error for ${device.address}: status=$status ($errorDetails)")
                
                // GATT 객체 정리
                gatt.close()
                activeGatts.remove(device.address)
                
                // 133 오류는 BLE 서비스 미지원 또는 잘못된 UUID로 인한 경우가 많음
                // 재시도 메커니즘 조정: 133 오류는 재시도 횟수 감소
                if (status == 133) {
                    val currentRetry = connectionRetryCount[device.address] ?: 0
                    if (currentRetry < MAX_RETRY_COUNT - 1) { // 최대 1회만 더 시도
                        handleConnectionFailure(device, status)
                    } else {
                        // 연결 실패로 처리
                        onConnectionStateChange(device, status, BluetoothProfile.STATE_DISCONNECTED)
                        connectionRetryCount.remove(device.address)
                    }
                } else {
                    // 다른 오류는 기존 로직대로 재시도
                    handleConnectionFailure(device, status)
                }
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