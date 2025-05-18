package com.ssafy.lanterns.service.ble.advertiser

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import com.ssafy.lanterns.service.ble.scanner.ScannerManager
import com.ssafy.lanterns.service.ble.BleMessageManager

/**
 * BLE 광고 관리 클래스 (개선 버전)
 * - 메시지 전송 관리
 * - 전송 실패 복구 메커니즘
 * - 광고 간격 최적화
 */
object AdvertiserManager {
    private const val TAG = "AdvertiserManager"
    
    // 블루투스 관련 객체
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    
    // 메시지 전송 관련 상태 관리
    private var isAdvertising = false
    private var currentMessageId: String? = null
    private var currentMessageParts = ConcurrentLinkedQueue<Map<String, String>>()
    private var currentPartIndex = 0
    
    // 핸들러
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 광고 및 재시도 관련 상수
    private const val ADVERTISE_DURATION = 500L // 광고 지속 시간 (ms)
    private const val RETRY_DELAY = 300L // 광고 실패시 재시도 간격 (ms)
    private const val NEXT_MESSAGE_DELAY = 100L // 다음 메시지 처리 간격 (ms)
    private const val MAX_RETRY_COUNT = 3 // 최대 재시도 횟수
    
    // 랜턴 앱 전용 상수
    private const val MANUFACTURER_ID_MESSAGE = 0xFFFF
    private const val MANUFACTURER_ID_EMAIL = 0xFFFE
    private val LANTERN_APP_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    
    // 현재 전송중인 메시지 분할 큐
    private var messageQueue = ConcurrentLinkedQueue<BleMessageManager.BleMessage>()
    
    // 현재 광고중인 메시지 파트 카운터
    private var advertisingPartCounter = 0
    private var retryCount = 0
    
    /**
     * 초기화 함수
     * @param activity 액티비티 컨텍스트
     */
    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        
        Log.d(TAG, "광고 관리자 초기화: 블루투스=${bluetoothAdapter != null}, 광고자=${bluetoothLeAdvertiser != null}")
    }
    
    /**
     * 메시지 큐 처리 시작
     * - 메시지가 있으면 하나씩 처리
     */
    private fun processMessageQueue(activity: Activity) {
        if (isAdvertising) {
            Log.d(TAG, "이미 광고 중, 큐 처리 지연")
            return
        }
        
        val nextMessage = BleMessageManager.getNextMessageToSend() ?: return
        
        Log.d(TAG, "메시지 처리 시작: ID=${nextMessage.id}, 타입=${nextMessage.type}")
        
        // 메시지 분할
        val messageParts = BleMessageManager.splitMessage(nextMessage)
        currentMessageId = nextMessage.id
        currentMessageParts.clear()
        currentMessageParts.addAll(messageParts)
        currentPartIndex = 0
        advertisingPartCounter = 0
        retryCount = 0
        
        // 광고 시작
        advertiseNextPart(activity)
    }
    
    /**
     * 다음 메시지 파트 광고
     */
    private fun advertiseNextPart(activity: Activity) {
        if (currentMessageParts.isEmpty()) {
            finishCurrentMessage(true)
            scheduleNextMessage(activity)
            return
        }
        
        val nextPart = currentMessageParts.poll()
        if (nextPart == null) {
            finishCurrentMessage(true)
            scheduleNextMessage(activity)
            return
        }
        
        advertisingPartCounter++
        startAdvertising(listOf(nextPart["id"] ?: "", nextPart["c"] ?: ""), 
                         nextPart["s"] ?: "Unknown", 
                         activity, 
                         nextPart["type"]?.toIntOrNull() ?: 0)
    }
    
    /**
     * 현재 메시지 처리 완료
     * @param success 성공 여부
     */
    private fun finishCurrentMessage(success: Boolean) {
        val messageId = currentMessageId ?: return
        
        if (success) {
            Log.d(TAG, "메시지 전송 완료: $messageId (파트: $advertisingPartCounter)")
            BleMessageManager.markMessageAsSent(messageId)
        } else {
            Log.e(TAG, "메시지 전송 실패: $messageId")
            BleMessageManager.handleMessageFailure(messageId)
        }
        
        currentMessageId = null
        currentMessageParts.clear()
        advertisingPartCounter = 0
    }
    
    /**
     * 다음 메시지 처리 예약
     */
    private fun scheduleNextMessage(activity: Activity) {
        mainHandler.postDelayed({
            processMessageQueue(activity)
        }, NEXT_MESSAGE_DELAY)
    }
    
    /**
     * 메시지 전송 요청
     * @param message 전송할 메시지 객체
     * @param activity 액티비티 컨텍스트
     */
    fun sendMessage(message: BleMessageManager.BleMessage, activity: Activity) {
        // 메시지 큐에 추가
        BleMessageManager.enqueueMessage(message)
        
        // 큐 처리 시작
        processMessageQueue(activity)
    }
    
    /**
     * 채팅 메시지 전송 (이전 호환성 유지)
     * @param messageList 메시지 리스트
     * @param email 발신자 이메일/닉네임
     * @param activity 액티비티 컨텍스트
     * @param state 메시지 상태
     */
    fun startAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int) {
        stopAdvertising()
        
        // 블루투스 권한 확인
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE 권한이 없습니다")
            return
        }
        
        // 블루투스 어댑터 확인
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "블루투스 광고 기능을 사용할 수 없습니다")
            return
        }
        
        // Advertise Setting - 전송 속도와 안정성 최적화
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED) // 최적 모드로 변경
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // 전력 소모 최적화
            .setConnectable(false)
            .setTimeout(0) // 시간 제한 없음
            .build()
        
        var advertiseData: AdvertiseData? = null
        var scanResponseData: AdvertiseData? = null
        
        // 최초 데이터 전송인 경우
        if (state == 0) {
            // 메시지 고유 UUID 생성 (8자리 16진수)
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val adString = messageList.getOrNull(0) ?: ""
            val scString = messageList.getOrNull(1) ?: ""
            
            Log.d(TAG, "메시지 전송: $adString, $scString")
            
            val adCombined = "$uuid|$adString"
            val scCombined = "$email|$scString"
            val adBytes = adCombined.toByteArray()
            val scBytes = scCombined.toByteArray()
            
            // 내 기기에서도 메시지 수신시 중복 방지를 위한 처리
            ScannerManager.updateChatSet(uuid, adCombined + scCombined, activity)
            
            // 메시지 데이터 광고
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(MANUFACTURER_ID_MESSAGE, adBytes)
                .build()
            
            // 이메일 및 추가 데이터 응답
            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(MANUFACTURER_ID_EMAIL, scBytes)
                .build()
        }
        // 메시지 릴레이인 경우
        else {
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(MANUFACTURER_ID_MESSAGE, messageList.getOrNull(0)?.toByteArray())
                .build()
            
            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(MANUFACTURER_ID_EMAIL, messageList.getOrNull(1)?.toByteArray())
                .build()
        }
        
        // 광고 콜백
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d(TAG, "광고 시작 성공")
                
                isAdvertising = true
                
                // 광고는 짧은 시간만 활성화 후 종료 (배터리 절약)
                mainHandler.postDelayed({
                    stopAdvertising()
                    
                    // 현재 메시지 처리 중이면 다음 파트 전송
                    if (currentMessageId != null && currentMessageParts.isNotEmpty()) {
                        advertiseNextPart(activity)
                    } else {
                        isAdvertising = false
                    }
                }, ADVERTISE_DURATION)
            }
            
            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "광고 시작 실패: 에러 코드 $errorCode")
                
                isAdvertising = false
                
                // 실패 처리 및 재시도
                when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> {
                        // 이미 시작된 경우 성공으로 간주
                        Log.d(TAG, "광고가 이미 시작됨")
                        
                        if (currentMessageId != null) {
                            // 이미 광고중인 상태로 간주하고 다음 파트로 진행
                            mainHandler.postDelayed({
                                advertiseNextPart(activity)
                            }, ADVERTISE_DURATION)
                        }
                    }
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                        // 데이터가 너무 큰 경우, 더 작은 데이터로 재시도
                        Log.d(TAG, "광고 데이터가 너무 큼, 축소 후 재시도")
                        
                        if (currentMessageId != null && retryCount < MAX_RETRY_COUNT) {
                            retryCount++
                            
                            // 데이터 크기 축소 후 재시도
                            val simplifiedAdData = AdvertiseData.Builder()
                                .setIncludeDeviceName(false)
                                .addManufacturerData(MANUFACTURER_ID_MESSAGE, messageList.getOrNull(0)?.substring(0, messageList.getOrNull(0)?.length?.div(2) ?: 0)?.toByteArray())
                                .build()
                            
                            mainHandler.postDelayed({
                                try {
                                    bluetoothLeAdvertiser.startAdvertising(settings, simplifiedAdData, null, this)
                                } catch (e: Exception) {
                                    Log.e(TAG, "축소된 데이터로 재시도 실패: ${e.message}")
                                    
                                    // 현재 메시지 실패 처리
                                    finishCurrentMessage(false)
                                }
                            }, RETRY_DELAY)
                        } else {
                            // 최대 재시도 횟수 초과 또는 메시지 없음
                            Log.e(TAG, "최대 재시도 횟수 초과 또는 현재 메시지 없음")
                            finishCurrentMessage(false)
                        }
                    }
                    else -> {
                        // 기타 오류는 지연 후 재시도
                        if (currentMessageId != null && retryCount < MAX_RETRY_COUNT) {
                            retryCount++
                            
                            mainHandler.postDelayed({
                                try {
                                    bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, this)
                                } catch (e: Exception) {
                                    Log.e(TAG, "광고 재시도 실패: ${e.message}")
                                    finishCurrentMessage(false)
                                }
                            }, RETRY_DELAY * retryCount)
                        } else {
                            // 최대 재시도 횟수 초과
                            Log.e(TAG, "최대 재시도 횟수 초과")
                            finishCurrentMessage(false)
                        }
                    }
                }
            }
        }
        
        // 광고 시작
        try {
            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "광고 시작 중 오류: ${e.message}")
            finishCurrentMessage(false)
        }
    }
    
    /**
     * 광고 중지
     */
    fun stopAdvertising() {
        advertiseCallback?.let {
            try {
                bluetoothLeAdvertiser?.stopAdvertising(it)
                Log.d(TAG, "광고 중지 완료")
            } catch (e: Exception) {
                Log.e(TAG, "광고 중지 실패: ${e.message}")
            }
        }
        
        advertiseCallback = null
        isAdvertising = false
        mainHandler.removeCallbacksAndMessages(null)
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        stopAdvertising()
        currentMessageId = null
        currentMessageParts.clear()
        messageQueue.clear()
    }
}