package com.ssafy.lanterns.service.ble.scanner

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.R
import com.ssafy.lanterns.service.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.service.ble.BleMessageManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 스캔 관리 클래스 (개선 버전)
 * - 배터리 효율성 강화
 * - 메시지 처리 로직 개선
 * - 스캔 주기 최적화
 */
object ScannerManager {
    private const val TAG = "ScannerManager"
    
    // 블루투스 관련 객체
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    
    // 처리된 채팅 메시지 저장 (중복 처리 방지)
    private val chatSet = mutableSetOf<String>() // <uuid, chat>
    
    // UUID와 타임스탬프를 저장하는 맵 추가
    private val chatTimeMap = ConcurrentHashMap<String, Long>() // <uuid, timestamp>
    
    // UUID 만료 시간 (5분)
    private const val UUID_EXPIRATION_TIME = 5 * 60 * 1000L
    
    // 주변 기기 정보 맵
    private val nearbyDevices = ConcurrentHashMap<String, DeviceInfo>() // <address, DeviceInfo>
    
    // 핸들러
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 스캔 관련 상수
    private const val SCAN_PERIOD = 5000L // 스캔 주기 (밀리초)
    private const val SCAN_INTERVAL = 1000L // 스캔 간격 (밀리초)
    private const val CLEANUP_INTERVAL = 60000L // 정리 주기 (밀리초)
    
    // 기본 저장소 상수
    private const val PREF_NAME = "ble_prefs"
    private const val KEY_CHAT_SET = "chat_uuids"
    
    // 제조사 ID 정의 - 광고자와 일치
    private const val MANUFACTURER_ID_MESSAGE = 0xFFFF
    private const val MANUFACTURER_ID_EMAIL = 0xFFFE
    
    // 스캔 상태
    private var isScanning = false
    private var isPaused = false
    private var scanRestartRunnable: Runnable? = null
    private var cleanupRunnable: Runnable? = null
    
    /**
     * 주변 기기 정보 클래스
     */
    data class DeviceInfo(
        val address: String,
        val name: String?,
        var lastSeen: Long,
        var rssi: Int,
        var data: Map<String, ByteArray> = mapOf()
    )
    
    /**
     * 초기화 함수
     * @param activity 액티비티 컨텍스트
     */
    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        loadChatSet(activity)
        
        // 기존 스캔 중지
        stopScanning()
        
        // 만료된 UUID 정리 Runnable
        cleanupRunnable = object : Runnable {
            override fun run() {
                cleanExpiredUuids()
                mainHandler.postDelayed(this, CLEANUP_INTERVAL)
            }
        }
        
        // 정리 작업 시작
        mainHandler.postDelayed(cleanupRunnable!!, CLEANUP_INTERVAL)
        
        Log.d(TAG, "스캐너 초기화 완료: 블루투스=${bluetoothAdapter != null}, 스캐너=${bluetoothLeScanner != null}")
    }
    
    /**
     * 채팅 세트 로드
     */
    private fun loadChatSet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_CHAT_SET, null)
        if (savedSet != null) {
            chatSet.clear()
            chatSet.addAll(savedSet)
            // 로드된 UUID 각각에 현재 시간 할당
            val currentTime = System.currentTimeMillis()
            savedSet.forEach { uuid ->
                chatTimeMap[uuid] = currentTime
            }
        }
    }
    
    /**
     * 채팅 세트 저장
     */
    private fun saveChatSet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_CHAT_SET, chatSet).apply()
    }
    
    /**
     * 채팅 UUID 추가
     */
    fun updateChatSet(uuid: String, chat: String, activity: Activity){
        this.chatSet.add(uuid)
        // UUID에 현재 시간 기록
        chatTimeMap[uuid] = System.currentTimeMillis()
        saveChatSet(activity)
    }
    
    /**
     * 만료된 UUID 정리
     */
    private fun cleanExpiredUuids() {
        val currentTime = System.currentTimeMillis()
        val expiredUuids = mutableListOf<String>()
        
        // 만료된 UUID 찾기
        chatTimeMap.forEach { (uuid, timestamp) ->
            if (currentTime - timestamp > UUID_EXPIRATION_TIME) {
                expiredUuids.add(uuid)
            }
        }
        
        // 만료된 UUID 제거
        expiredUuids.forEach { uuid ->
            chatSet.remove(uuid)
            chatTimeMap.remove(uuid)
        }
        
        // 오래된 기기 정보 제거
        val expiredDevices = nearbyDevices.entries
            .filter { currentTime - it.value.lastSeen > UUID_EXPIRATION_TIME }
            .map { it.key }
        
        expiredDevices.forEach { nearbyDevices.remove(it) }
        
        if (expiredUuids.isNotEmpty() || expiredDevices.isNotEmpty()) {
            Log.d(TAG, "만료된 데이터 정리: UUID ${expiredUuids.size}개, 기기 ${expiredDevices.size}개")
        }
    }
    
    /**
     * BLE 스캔 시작
     * @param activity 액티비티 컨텍스트
     * @param onMessageReceived 메시지 수신 콜백
     */
    fun startScanning(activity: Activity, onMessageReceived: (String, String, String) -> Unit) {
        if (isPaused) {
            Log.d(TAG, "스캔이 일시 중지된 상태에서 시작 요청됨")
            isPaused = false
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "이미 스캔 중, 중복 시작 요청 무시")
            return
        }
        
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner가 null입니다. 스캔을 시작할 수 없습니다.")
            return
        }
        
        // 블루투스 권한 확인
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN 권한이 없습니다. 스캔을 시작할 수 없습니다.")
            return
        }
        
        // 이전 스캔 정리
        stopScanning()
        
        // 스캔 설정 - 배터리 효율성과 성능 사이의 균형
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 초기에는 고속 스캔
            .setReportDelay(0) // 결과 즉시 보고
            .build()
        
        // 필터 설정 - 우리 앱에서 사용하는 제조사 ID만 필터링
        val scanFilter1 = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID_MESSAGE, null)
            .build()
        
        val scanFilter2 = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID_EMAIL, null)
            .build()
        
        val scanFilters = listOf(scanFilter1, scanFilter2)
        
        // 스캔 콜백
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    processBleScanResult(scanResult, activity, onMessageReceived)
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { result ->
                    processBleScanResult(result, activity, onMessageReceived)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "스캔 실패: 오류 코드=$errorCode")
                isScanning = false
                
                // 오류 복구 로직
                when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> {
                        Log.d(TAG, "이미 스캔 중")
                        isScanning = true
                    }
                    else -> {
                        // 다른 오류는 잠시 후 재시도
                        mainHandler.postDelayed({
                            if (!isPaused) {
                                startScanning(activity, onMessageReceived)
                            }
                        }, 5000) // 5초 후 재시도
                    }
                }
            }
        }
        
        // 스캔 시작
        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE 스캔 시작")
            
            // 스캔 간격 조정을 위한 Runnable
            scheduleIntervalScanning(activity, onMessageReceived)
            
        } catch (e: Exception) {
            Log.e(TAG, "스캔 시작 중 오류: ${e.message}")
            isScanning = false
        }
    }
    
    /**
     * 간헐적 스캔 일정 잡기
     * - 배터리 소모 최소화를 위해 스캔-휴지 주기 반복
     */
    private fun scheduleIntervalScanning(activity: Activity, onMessageReceived: (String, String, String) -> Unit) {
        // 기존 스캔 일정 취소
        scanRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // 새 스캔 일정 정의
        scanRestartRunnable = object : Runnable {
            override fun run() {
                if (isPaused) return
                
                // 스캔 중지
                stopActiveScanning()
                
                // 잠시 후 스캔 재개
                mainHandler.postDelayed({
                    if (!isPaused) {
                        // 스캔 설정을 배터리 효율적으로 변경
                        val lowPowerSettings = ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                            .setReportDelay(0)
                            .build()
                        
                        // 필터 사용
                        val scanFilter1 = ScanFilter.Builder()
                            .setManufacturerData(MANUFACTURER_ID_MESSAGE, null)
                            .build()
                        
                        val scanFilter2 = ScanFilter.Builder()
                            .setManufacturerData(MANUFACTURER_ID_EMAIL, null)
                            .build()
                        
                        val scanFilters = listOf(scanFilter1, scanFilter2)
                        
                        // 저전력 모드로 스캔 재개
                        try {
                            bluetoothLeScanner?.startScan(scanFilters, lowPowerSettings, scanCallback)
                            isScanning = true
                            Log.d(TAG, "저전력 모드로 BLE 스캔 재개")
                            
                            // 다음 스캔 주기 일정
                            mainHandler.postDelayed(this, SCAN_PERIOD)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "스캔 재개 중 오류: ${e.message}")
                            isScanning = false
                            
                            // 오류 발생 시 재시도
                            mainHandler.postDelayed({
                                if (!isPaused) startScanning(activity, onMessageReceived)
                            }, SCAN_INTERVAL * 3)
                        }
                    }
                }, SCAN_INTERVAL)
            }
        }
        
        // 첫 번째 스캔 주기 종료 일정
        mainHandler.postDelayed(scanRestartRunnable!!, SCAN_PERIOD)
    }
    
    /**
     * 활성 스캔만 중지
     * - 내부 상태는 유지
     */
    private fun stopActiveScanning() {
        if (!isScanning) return
        
        scanCallback?.let {
            try {
                bluetoothLeScanner?.stopScan(it)
                Log.d(TAG, "활성 스캔 중지")
            } catch (e: Exception) {
                Log.e(TAG, "스캔 중지 중 오류: ${e.message}")
            }
        }
        
        isScanning = false
    }
    
    /**
     * 스캔 완전 중지
     * - 모든 리소스 정리
     */
    fun stopScanning() {
        // 활성 스캔 중지
        stopActiveScanning()
        
        // 모든 스캔 관련 리소스 정리
        scanCallback = null
        mainHandler.removeCallbacksAndMessages(null)
        isScanning = false
        Log.d(TAG, "스캔 완전 중지 (모든 리소스 정리)")
    }
    
    /**
     * 스캔 일시 중지
     */
    fun pauseScanning() {
        isPaused = true
        stopActiveScanning()
        Log.d(TAG, "스캔 일시 중지")
    }
    
    /**
     * 스캔 재개
     */
    fun resumeScanning(activity: Activity, onMessageReceived: (String, String, String) -> Unit) {
        isPaused = false
        if (!isScanning) {
            startScanning(activity, onMessageReceived)
        }
    }
    
    /**
     * BLE 스캔 결과 처리
     */
    private fun processBleScanResult(scanResult: ScanResult, activity: Activity, onMessageReceived: (String, String, String) -> Unit) {
        val device = scanResult.device
        val rssi = scanResult.rssi
        val scanRecord = scanResult.scanRecord ?: return
        
        // 주변 기기 정보 업데이트
        val deviceInfo = nearbyDevices.getOrPut(device.address) {
            DeviceInfo(
                address = device.address,
                name = device.name,
                lastSeen = System.currentTimeMillis(),
                rssi = rssi
            )
        }.apply {
            lastSeen = System.currentTimeMillis()
            this.rssi = rssi
        }
        
        // 제조사 데이터 추출
        val manufacturerData = scanRecord.getManufacturerSpecificData(MANUFACTURER_ID_MESSAGE)
        val emailData = scanRecord.getManufacturerSpecificData(MANUFACTURER_ID_EMAIL)
        
        if (manufacturerData == null) return
        
        // 데이터 문자열 변환
        val messageData = String(manufacturerData)
        val emailOrNickname = emailData?.let { String(it) } ?: "Unknown"
        
        // 메시지 형식 파싱 (UUID|내용)
        val messageParts = messageData.split("|", limit=2)
        val emailParts = emailOrNickname.split("|", limit=2)
        
        if (messageParts.size == 2) {
            val uuid = messageParts[0]
            val message = messageParts[1]
            val sender = emailParts.getOrNull(0) ?: "Unknown"
            val additionalData = emailParts.getOrNull(1) ?: ""
            
            // UUID 유효성 검증
            if (!isValidUUID(uuid)) {
                Log.d(TAG, "유효하지 않은 UUID: $uuid")
                return
            }
            
            // 이미 처리한 메시지인지 확인
            if (chatSet.contains(uuid)) {
                return
            }
            
            // 메시지 처리
            val combinedMessage = message + additionalData
            
            // 처리 완료된 메시지 기록
            chatSet.add(uuid)
            chatTimeMap[uuid] = System.currentTimeMillis()
            saveChatSet(activity)
            
            // 메시지 수신 처리
            val rawDataMap = mapOf(
                "id" to uuid,
                "s" to sender,
                "c" to combinedMessage,
                "t" to System.currentTimeMillis().toString()
            )
            
            // BleMessageManager를 통한 메시지 처리
            val processedMessage = BleMessageManager.processIncomingMessagePart(rawDataMap)
            
            // 완전히 조립된 메시지가 있으면 콜백 호출
            processedMessage?.let {
                Log.d(TAG, "메시지 수신: 발신자=$sender, ID=$uuid")
                onMessageReceived(sender, it.content, rssi.toString())
                
                // 릴레이 처리 - 필요한 경우 메시지 전달
                relayMessage(uuid, message, additionalData, sender, activity)
            }
        }
    }
    
    /**
     * 메시지 릴레이
     * - 다른 사용자에게 메시지 전달
     */
    private fun relayMessage(uuid: String, message: String, additionalData: String, sender: String, activity: Activity) {
        // 릴레이 횟수 제한 등 필요한 로직 추가 가능
        AdvertiserManager.startAdvertising(
            messageList = listOf("$uuid|$message", "$sender|$additionalData"),
            email = sender,
            activity = activity,
            state = 1 // 릴레이 모드
        )
    }
    
    /**
     * UUID 유효성 검사
     */
    private fun isValidUUID(uuid: String): Boolean {
        // UUID 형식 검사 (8자리 16진수)
        return uuid.length == 8 && uuid.all { 
            it.isDigit() || it in 'a'..'f' || it in 'A'..'F' 
        }
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        stopScanning()
        mainHandler.removeCallbacksAndMessages(null)
        cleanupRunnable = null
        scanRestartRunnable = null
    }
}