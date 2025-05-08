package com.ssafy.lantern.service.provisioning

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.ssafy.lantern.service.security.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프로비저닝 결과 데이터 클래스
 */
data class ProvisionResult(
    val isSuccessful: Boolean,
    val deviceAddress: String,
    val unicastAddress: Int? = null,
    val errorMessage: String? = null
)

/**
 * 노드 정보 데이터 클래스
 */
data class MeshNode(
    val device: BluetoothDevice,
    val rssi: Int,
    val isProvisioned: Boolean = false,
    val unicastAddress: Int? = null
)

/**
 * 프로비저닝 관리자 클래스
 * 메쉬 노드 발견 및 프로비저닝 기능 제공
 */
@Singleton
class ProvisioningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "ProvisioningManager"
        
        // 메쉬 프로비저닝 서비스 UUID
        val MESH_PROVISIONING_UUID = UUID.fromString("00001827-0000-1000-8000-00805F9B34FB")
        
        // 프로비저닝 데이터 입력 특성 UUID
        val PROVISIONING_DATA_IN_UUID = UUID.fromString("00002ADB-0000-1000-8000-00805F9B34FB")
        
        // 프로비저닝 데이터 출력 특성 UUID
        val PROVISIONING_DATA_OUT_UUID = UUID.fromString("00002ADC-0000-1000-8000-00805F9B34FB")
        
        // 클라이언트 설정 디스크립터 UUID
        val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        
        // 스캔 타임아웃 (밀리초)
        private const val SCAN_TIMEOUT = 10_000L
        
        // 연결 타임아웃 (밀리초)
        private const val CONNECTION_TIMEOUT = 30_000L
        
        // 프로비저닝 타임아웃 (밀리초)
        private const val PROVISIONING_TIMEOUT = 60_000L
        
        // 재시도 횟수
        private const val MAX_RETRY_COUNT = 3
        
        // 주소 할당 시작값
        private const val UNICAST_ADDRESS_START = 0x0001
    }
    
    // 블루투스 관련 객체
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    
    // 스캔 중 플래그
    private var isScanning = false
    
    // GATT 연결 객체
    private var bluetoothGatt: BluetoothGatt? = null
    
    // 미프로비저닝 노드 캐시
    private val unProvisionedNodes = ConcurrentHashMap<String, MeshNode>()
    
    // 프로비저닝된 노드 관리 (주소 할당 추적용)
    private val provisionedNodes = ConcurrentHashMap<String, MeshNode>()
    
    // 다음 할당할 유니캐스트 주소
    private var nextUnicastAddress = UNICAST_ADDRESS_START
    
    // 난수 생성기
    private val secureRandom = SecureRandom()
    
    // 데이터 수신 완료 신호
    private var dataReceived = CompletableDeferred<ByteArray?>()
    
    // 스캔 결과 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val rssi = result.rssi
            
            // 이미 발견된 장치인지 확인
            if (unProvisionedNodes.containsKey(device.address)) {
                return
            }
            
            // 새 노드 등록
            val newNode = MeshNode(device, rssi)
            unProvisionedNodes[device.address] = newNode
            
            Log.d(TAG, "미프로비저닝 노드 발견: ${device.address}, RSSI: $rssi")
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "스캔 실패: 오류 코드 $errorCode")
            isScanning = false
        }
    }
    
    // GATT 콜백
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT 연결 성공: ${gatt.device.address}")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT 연결 해제: ${gatt.device.address}")
                    gatt.close()
                    bluetoothGatt = null
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 검색 완료")
                // 필요한 서비스와 특성 검색
                val provisioningService = gatt.getService(MESH_PROVISIONING_UUID)
                if (provisioningService != null) {
                    Log.d(TAG, "프로비저닝 서비스 발견")
                }
            } else {
                Log.e(TAG, "서비스 검색 실패: $status")
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 쓰기 성공: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "특성 쓰기 실패: $status")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "특성 변경 감지: ${characteristic.uuid}")
            
            if (characteristic.uuid == PROVISIONING_DATA_OUT_UUID) {
                val value = characteristic.value
                if (value != null) {
                    Log.d(TAG, "프로비저닝 데이터 수신: ${value.size} 바이트")
                    // 비동기 처리를 위해 CompletableDeferred 완료
                    dataReceived.complete(value)
                }
            }
        }
    }
    
    /**
     * 미프로비저닝 노드 검색
     */
    @SuppressLint("MissingPermission")
    suspend fun discoverNodes(): List<MeshNode> = withContext(Dispatchers.IO) {
        if (isScanning) {
            Log.d(TAG, "이미 스캔 중입니다")
            return@withContext unProvisionedNodes.values.toList()
        }
        
        // 캐시 초기화
        unProvisionedNodes.clear()
        isScanning = true
        
        try {
            // 프로비저닝 서비스 UUID로 필터링
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_PROVISIONING_UUID))
                .build()
            
            // 스캔 설정
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            // 스캔 시작
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            
            // 타임아웃 설정
            withTimeout(SCAN_TIMEOUT) {
                // 일정 시간 스캔 유지
                delay(SCAN_TIMEOUT)
            }
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "스캔 타임아웃 - 정상 종료")
        } catch (e: Exception) {
            Log.e(TAG, "스캔 중 오류 발생", e)
        } finally {
            // 스캔 중지
            bleScanner?.stopScan(scanCallback)
            isScanning = false
        }
        
        return@withContext unProvisionedNodes.values.toList()
    }
    
    /**
     * 노드 프로비저닝
     */
    @SuppressLint("MissingPermission")
    suspend fun provision(device: BluetoothDevice): ProvisionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "${device.address} 프로비저닝 시작")
        
        for (attempt in 1..MAX_RETRY_COUNT) {
            try {
                // 기존 연결 정리
                bluetoothGatt?.close()
                
                // 장치에 연결
                val connectDeferred = CompletableDeferred<Boolean>()
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                
                // 연결 타임아웃
                withTimeout(CONNECTION_TIMEOUT) {
                    while (bluetoothGatt?.services?.isEmpty() != false) {
                        delay(500)
                    }
                    connectDeferred.complete(true)
                }
                
                // 연결 성공 확인
                if (!connectDeferred.await()) {
                    Log.e(TAG, "GATT 연결 실패 (시도 $attempt/$MAX_RETRY_COUNT)")
                    continue
                }
                
                // 프로비저닝 서비스 찾기
                val gatt = bluetoothGatt ?: throw IllegalStateException("GATT 연결이 없습니다")
                val provisioningService = gatt.getService(MESH_PROVISIONING_UUID)
                    ?: throw IllegalStateException("프로비저닝 서비스를 찾을 수 없습니다")
                
                // 입출력 특성 찾기
                val dataInChar = provisioningService.getCharacteristic(PROVISIONING_DATA_IN_UUID)
                    ?: throw IllegalStateException("프로비저닝 데이터 입력 특성을 찾을 수 없습니다")
                
                val dataOutChar = provisioningService.getCharacteristic(PROVISIONING_DATA_OUT_UUID)
                    ?: throw IllegalStateException("프로비저닝 데이터 출력 특성을 찾을 수 없습니다")
                
                // 알림 활성화
                val descriptor = dataOutChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
                    ?: throw IllegalStateException("클라이언트 설정 디스크립터를 찾을 수 없습니다")
                
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                gatt.setCharacteristicNotification(dataOutChar, true)
                
                // 프로비저닝 단계 진행
                // 1. 초대(Invite) 단계
                val inviteData = ByteArray(1) { 0x00 } // Invite PDU
                dataInChar.value = inviteData
                gatt.writeCharacteristic(dataInChar)
                
                // 2. Capabilities 수신 대기
                dataReceived = CompletableDeferred()
                val capabilitiesData = withTimeout(PROVISIONING_TIMEOUT) {
                    dataReceived.await() ?: throw IllegalStateException("Capabilities 데이터 수신 실패")
                }
                Log.d(TAG, "Capabilities 수신 완료: ${capabilitiesData.size} 바이트")
                
                // 3. Start 메시지 전송
                val startData = ByteArray(5) { 0x02 } // Start PDU
                dataInChar.value = startData
                gatt.writeCharacteristic(dataInChar)
                
                // 4. 공개 키 교환
                val localPublicKey = ByteArray(64) // 실제로는 키 생성 필요
                secureRandom.nextBytes(localPublicKey)
                
                val publicKeyData = ByteArray(65) { 0x03 } // Public Key PDU
                System.arraycopy(localPublicKey, 0, publicKeyData, 1, 64)
                dataInChar.value = publicKeyData
                gatt.writeCharacteristic(dataInChar)
                
                // 5. 원격 공개 키 수신
                dataReceived = CompletableDeferred()
                val remotePublicKey = withTimeout(PROVISIONING_TIMEOUT) {
                    dataReceived.await() ?: throw IllegalStateException("원격 공개 키 수신 실패")
                }
                Log.d(TAG, "원격 공개 키 수신 완료: ${remotePublicKey.size} 바이트")
                
                // 6. 확인(Confirmation) 단계
                val confirmationData = ByteArray(17) { 0x05 } // Confirmation PDU
                secureRandom.nextBytes(confirmationData)
                dataInChar.value = confirmationData
                gatt.writeCharacteristic(dataInChar)
                
                // 7. 원격 확인 값 수신
                dataReceived = CompletableDeferred()
                val remoteConfirmation = withTimeout(PROVISIONING_TIMEOUT) {
                    dataReceived.await() ?: throw IllegalStateException("원격 확인 값 수신 실패")
                }
                
                // 8. 난수(Random) 전송
                val randomData = ByteArray(17) { 0x06 } // Random PDU
                secureRandom.nextBytes(randomData)
                dataInChar.value = randomData
                gatt.writeCharacteristic(dataInChar)
                
                // 9. 원격 난수 수신
                dataReceived = CompletableDeferred()
                val remoteRandom = withTimeout(PROVISIONING_TIMEOUT) {
                    dataReceived.await() ?: throw IllegalStateException("원격 난수 수신 실패")
                }
                
                // 10. 장치 키 파생
                val deviceKey = securityManager.deriveDeviceKey(remoteRandom, randomData)
                
                // 11. 유니캐스트 주소 할당
                val unicastAddress = nextUnicastAddress
                nextUnicastAddress += 1  // 다음 주소로 증가
                
                // 12. 프로비저닝 데이터 전송
                val provisioningData = ByteArray(25) { 0x07 } // Data PDU
                // 네트워크 키, 유니캐스트 주소 등 설정 (실제로는 더 복잡한 구성 필요)
                System.arraycopy(
                    deviceKey, 0, 
                    provisioningData, 1, 
                    16.coerceAtMost(deviceKey.size)
                )
                // 주소 설정 (2바이트)
                provisioningData[17] = (unicastAddress and 0xFF).toByte()
                provisioningData[18] = ((unicastAddress shr 8) and 0xFF).toByte()
                
                dataInChar.value = provisioningData
                gatt.writeCharacteristic(dataInChar)
                
                // 13. 완료 확인
                dataReceived = CompletableDeferred()
                val completionStatus = withTimeout(PROVISIONING_TIMEOUT) {
                    try {
                        dataReceived.await()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                
                // 프로비저닝 완료 처리
                if (completionStatus) {
                    // 프로비저닝된 노드 목록에 추가
                    provisionedNodes[device.address] = MeshNode(
                        device = device,
                        rssi = 0,
                        isProvisioned = true,
                        unicastAddress = unicastAddress
                    )
                    
                    // 미프로비저닝 목록에서 제거
                    unProvisionedNodes.remove(device.address)
                    
                    Log.d(TAG, "프로비저닝 성공: ${device.address}, 주소: 0x${unicastAddress.toString(16)}")
                    return@withContext ProvisionResult(
                        isSuccessful = true,
                        deviceAddress = device.address,
                        unicastAddress = unicastAddress
                    )
                } else {
                    Log.e(TAG, "프로비저닝 완료 확인 실패")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "프로비저닝 타임아웃 (시도 $attempt/$MAX_RETRY_COUNT)")
            } catch (e: Exception) {
                Log.e(TAG, "프로비저닝 오류 (시도 $attempt/$MAX_RETRY_COUNT)", e)
            } finally {
                // 연결 해제
                delay(1000)
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }
        
        Log.e(TAG, "최대 재시도 횟수 초과: 프로비저닝 실패")
        return@withContext ProvisionResult(
            isSuccessful = false,
            deviceAddress = device.address,
            errorMessage = "최대 재시도 횟수를 초과했습니다"
        )
    }
    
    /**
     * 프로비저닝된 노드 목록 반환
     */
    fun getProvisionedNodes(): List<MeshNode> {
        return provisionedNodes.values.toList()
    }
    
    /**
     * 미프로비저닝 노드 목록 반환
     */
    fun getUnprovisionedNodes(): List<MeshNode> {
        return unProvisionedNodes.values.toList()
    }
    
    /**
     * 특정 유니캐스트 주소의 노드 찾기
     */
    fun findNodeByAddress(unicastAddress: Int): MeshNode? {
        return provisionedNodes.values.find { it.unicastAddress == unicastAddress }
    }
    
    /**
     * 리소스 해제
     */
    @SuppressLint("MissingPermission")
    fun release() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        
        if (isScanning) {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }
} 