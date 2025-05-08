package com.ssafy.lantern.service.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BLE 통신 인터페이스
 */
interface BleComm {
    /**
     * 광고 시작 - 데이터 전파용
     */
    fun startAdvertising(data: ByteArray)
    
    /**
     * 광고 중지
     */
    fun stopAdvertising()
    
    /**
     * 스캔 시작 - 데이터 수신용
     */
    fun startScanning(callback: (ScanResult) -> Unit)
    
    /**
     * 스캔 중지
     */
    fun stopScanning()
}

/**
 * BLE 통신 구현 클래스
 */
@Singleton
class BleCommImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BleComm {
    companion object {
        private const val TAG = "BleComm"
        
        // 메쉬 네트워크 서비스 UUID - 고유한 값으로 변경 필요
        val MESH_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        
        // 스캔 주기 (밀리초)
        private const val SCAN_PERIOD_HIGH = 10_000L  // 고성능 스캔 기간 (10초)
        private const val SCAN_PERIOD_LOW = 60_000L   // 저전력 스캔 기간 (1분)
        
        // 최대 광고 데이터 크기 (바이트)
        // 안드로이드 BLE 광고 패킷은 총 31바이트, 헤더와 UUID 등 제외하면 약 20바이트 사용 가능
        private const val MAX_ADVERTISE_DATA_SIZE = 8  // 안전하게 8바이트로 축소
        
        // 광고 간 최소 지연 시간 (밀리초)
        private const val ADVERTISE_DELAY = 200L
    }
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private val bleAdvertiser by lazy {
        bluetoothAdapter?.bluetoothLeAdvertiser
    }
    
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    
    private var userScanCallback: ((ScanResult) -> Unit)? = null
    private var isScanning = false
    
    // 광고 상태 관리를 위한 변수
    private val isAdvertising = AtomicBoolean(false)
    private val isAdvertisingQueued = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // 광고 콜백
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "광고 시작 성공: $settingsInEffect")
            // 광고가 시작되면 지연 후 중지하여 다음 광고를 위한 준비
            coroutineScope.launch {
                delay(ADVERTISE_DELAY)
                stopAdvertisingInternal()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "광고 시작 실패, 에러 코드: $errorCode 의미: ${getAdvertiseErrorMessage(errorCode)}")
            stopAdvertisingInternal()
        }
    }
    
    // 광고 오류 메시지 반환
    private fun getAdvertiseErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "데이터가 너무 큽니다."
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "너무 많은 광고자가 있습니다."
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "이미 광고가 시작되었습니다."
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "내부 오류가 발생했습니다."
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "기능이 지원되지 않습니다."
            else -> "알 수 없는 오류입니다."
        }
    }
    
    // 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // 서비스 UUID 확인
            val scanRecord = result.scanRecord ?: return
            val serviceData = scanRecord.serviceData
            
            val meshServiceUuid = ParcelUuid(MESH_SERVICE_UUID)
            
            // 스캔 레코드에서 메시 서비스 데이터 확인
            if (serviceData?.containsKey(meshServiceUuid) == true) {
                val data = serviceData[meshServiceUuid]
                if (data != null && data.isNotEmpty()) {
                    Log.d(TAG, "메시 데이터 발견: ${data.size} 바이트")
                    // 사용자 콜백 호출
                    userScanCallback?.invoke(result)
                    return
                }
            }
            
            // 서비스 UUID로 등록된 경우도 추가 확인
            val serviceUuids = scanRecord.serviceUuids
            if (serviceUuids?.contains(meshServiceUuid) == true) {
                // 서비스 UUID만 있고 데이터가 없는 경우 - 이 경우 근접 디바이스 발견으로만 처리
                Log.d(TAG, "메시 서비스 UUID 발견")
                userScanCallback?.invoke(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "스캔 실패, 에러 코드: $errorCode")
            isScanning = false
        }
    }
    
    /**
     * 스캔 설정 생성 - 저전력 모드
     */
    private fun createLowPowerScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)  // 즉시 결과 보고
            .build()
    }
    
    /**
     * 스캔 설정 생성 - 고성능 모드
     */
    private fun createHighPowerScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)  // 즉시 결과 보고
            .build()
    }
    
    /**
     * 메쉬 서비스 필터 생성
     */
    private fun createMeshServiceFilter(): List<ScanFilter> {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()
        return listOf(filter)
    }
    
    /**
     * 광고 설정 생성
     */
    private fun createAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  // 최소 지연 모드로 변경
            .setConnectable(false)  // 연결 불가능 (브로드캐스트 전용)
            .setTimeout(0)  // 제한 없음
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)  // 전력 수준을 낮게 조정
            .build()
    }
    
    /**
     * 광고 데이터 분할 처리
     */
    private fun splitAdvertiseData(data: ByteArray): List<ByteArray> {
        // 데이터가 최대 크기보다 작으면 분할하지 않음
        if (data.size <= MAX_ADVERTISE_DATA_SIZE) {
            return listOf(data)
        }
        
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        
        while (offset < data.size) {
            val chunkSize = minOf(MAX_ADVERTISE_DATA_SIZE, data.size - offset)
            val chunk = ByteArray(chunkSize)
            System.arraycopy(data, offset, chunk, 0, chunkSize)
            chunks.add(chunk)
            offset += chunkSize
        }
        
        Log.d(TAG, "데이터 분할: 총 ${data.size}바이트를 ${chunks.size}개 청크로 분할")
        return chunks
    }
    
    @SuppressLint("MissingPermission")  // 호출 전에 권한 확인 필요
    override fun startAdvertising(data: ByteArray) {
        if (bluetoothAdapter == null || bleAdvertiser == null) {
            Log.e(TAG, "Bluetooth 어댑터 혹은 BLE 광고자가 사용 불가능합니다")
            return
        }
        
        // 데이터 분할
        val chunks = splitAdvertiseData(data)
        
        // 큐에 광고 작업 추가
        coroutineScope.launch {
            // 이미 큐에 있다면 새로 추가하지 않음
            if (isAdvertisingQueued.getAndSet(true)) {
                Log.d(TAG, "이미 광고 작업이 큐에 있습니다. 무시됩니다.")
                return@launch
            }
            
            for (chunk in chunks) {
                // 이전 광고가 끝날 때까지 대기
                while (isAdvertising.get()) {
                    delay(50) // 50ms 간격으로 확인
                }
                
                // 광고 시작
                if (isAdvertising.compareAndSet(false, true)) {
                    try {
                        Log.d(TAG, "광고 시작 요청 (${chunk.size} 바이트)")
                        
                        // 더 작은 크기로 제한 - UUID 공간도 고려
                        val actualChunk = if (chunk.size > MAX_ADVERTISE_DATA_SIZE - 4) {
                            // 추가 공간 확보를 위해 크기 축소
                            val reducedSize = maxOf(1, MAX_ADVERTISE_DATA_SIZE - 4)
                            chunk.copyOf(reducedSize)
                        } else {
                            chunk
                        }

                        Log.d(TAG, "실제 광고 데이터 크기: ${actualChunk.size} 바이트")
                        
                        // 광고 데이터 - 최소한의 정보만 포함
                        val advertiseData = AdvertiseData.Builder()
                            .setIncludeDeviceName(false)  // 디바이스 이름 제외하여 공간 확보
                            .setIncludeTxPowerLevel(false) // 전송 전력 제외
                            // 서비스 UUID와 데이터를 따로 설정 - 데이터를 서비스로 포함하지 않음 (최대한 공간 확보)
                            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                            .build()
                            
                        // 응답 데이터에 실제 페이로드 담기
                        val scanResponse = AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .setIncludeTxPowerLevel(false)
                            .addServiceData(ParcelUuid(MESH_SERVICE_UUID), actualChunk)
                            .build()
                            
                        // 광고 시작
                        bleAdvertiser?.startAdvertising(
                            createAdvertiseSettings(),
                            advertiseData, 
                            scanResponse,
                            advertiseCallback
                        )
                        
                        // 각 청크 사이에 지연 추가
                        delay(ADVERTISE_DELAY)
                    } catch (e: Exception) {
                        Log.e(TAG, "광고 시작 중 오류 발생", e)
                        stopAdvertisingInternal()
                    }
                }
            }
            
            // 모든 청크 광고를 마치면 큐 플래그 초기화
            isAdvertisingQueued.set(false)
        }
    }

    @SuppressLint("MissingPermission")  // 호출 전에 권한 확인 필요
    override fun stopAdvertising() {
        stopAdvertisingInternal()
    }
    
    /**
     * 내부적으로 광고를 중지하는 메서드
     */
    private fun stopAdvertisingInternal() {
        if (!isAdvertising.get()) return
        
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "광고 중지")
        } catch (e: Exception) {
            Log.e(TAG, "광고 중지 중 오류 발생", e)
        } finally {
            isAdvertising.set(false)
        }
    }

    @SuppressLint("MissingPermission")  // 호출 전에 권한 확인 필요
    override fun startScanning(callback: (ScanResult) -> Unit) {
        if (bluetoothAdapter == null || bleScanner == null) {
            Log.e(TAG, "Bluetooth 어댑터 혹은 BLE 스캐너가 사용 불가능합니다")
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "이미 스캔 중입니다")
            return
        }
        
        // 권한 확인 추가
        val hasScanPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasScanPermission) {
            Log.e(TAG, "BLE 스캔 권한이 없습니다. BLUETOOTH_SCAN 권한을 확인하세요.")
            return
        }
        
        userScanCallback = callback
        isScanning = true
        
        try {
            // 고성능 스캔 시작 (10초)
            Log.d(TAG, "고성능 스캔 시작 (${SCAN_PERIOD_HIGH}ms)")
            bleScanner?.startScan(
                createMeshServiceFilter(),
                createHighPowerScanSettings(),
                scanCallback
            )
            
            // 10초 후 저전력 모드로 전환
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isScanning) {
                    // 고성능 스캔 중지
                    bleScanner?.stopScan(scanCallback)
                    
                    // 저전력 스캔 시작
                    Log.d(TAG, "저전력 스캔으로 전환 (${SCAN_PERIOD_LOW}ms)")
                    bleScanner?.startScan(
                        createMeshServiceFilter(),
                        createLowPowerScanSettings(),
                        scanCallback
                    )
                }
            }, SCAN_PERIOD_HIGH)
        } catch (e: Exception) {
            isScanning = false
            Log.e(TAG, "스캔 시작 중 오류 발생", e)
        }
    }

    @SuppressLint("MissingPermission")  // 호출 전에 권한 확인 필요
    override fun stopScanning() {
        if (!isScanning) return
        
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            userScanCallback = null
            Log.d(TAG, "스캔 중지")
        } catch (e: Exception) {
            Log.e(TAG, "스캔 중지 중 오류 발생", e)
        }
    }
} 