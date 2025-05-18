package com.ssafy.lanterns.service.ble

import android.app.Activity
import android.util.Log
import com.ssafy.lanterns.service.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.service.ble.scanner.ScannerManager
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner

/**
 * 전역 BLE 리소스 관리자 (개선 버전)
 * - 화면 간 BLE 리소스 충돌 방지
 * - 리소스 효율적 관리
 * - 채팅 화면 특화 BLE 설정
 */
object GlobalBleManager {
    private const val TAG = "GlobalBleManager"
    
    // 화면 타입 정의
    const val SCREEN_MAIN = 0
    const val SCREEN_DIRECT_CHAT = 1
    const val SCREEN_PUBLIC_CHAT = 2
    
    // 현재 활성 화면
    private var currentScreen = -1
    
    // 화면별 채팅 리스너 저장
    private val chatListeners = mutableMapOf<Int, (String, String, String) -> Unit>()
    
    // 활성화 상태
    private var bleInitialized = false
    private var scanningEnabled = false
    private var advertisingEnabled = false
    
    // 마지막 활성 화면 액티비티 (약한 참조로 변경하면 더 좋음)
    private var currentActivity: Activity? = null
    
    /**
     * BLE 초기화
     * - 모든 BLE 관련 매니저 초기화
     * @param activity 현재 액티비티
     */
    fun initialize(activity: Activity) {
        if (bleInitialized) return
        
        try {
            // 스캐너 초기화
            ScannerManager.init(activity)
            NeighborScanner.init(activity)
            
            // 광고자 초기화
            AdvertiserManager.init(activity)
            
            // 메시지 매니저는 자체 초기화 (싱글톤)
            bleInitialized = true
            Log.d(TAG, "BLE 리소스 초기화 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "BLE 리소스 초기화 실패: ${e.message}")
        }
    }
    
    /**
     * 현재 활성 화면 설정
     * @param screenType 화면 타입 (SCREEN_MAIN, SCREEN_DIRECT_CHAT, SCREEN_PUBLIC_CHAT)
     * @param activity 현재 액티비티
     * @param chatListener 채팅 메시지 리스너 (옵션)
     */
    fun setActiveScreen(screenType: Int, activity: Activity, chatListener: ((String, String, String) -> Unit)? = null) {
        if (currentScreen == screenType) {
            Log.d(TAG, "이미 활성화된 화면 타입: $screenType")
            return
        }
        
        // 이전 화면 리소스 해제
        releaseScreenResources(currentScreen)
        
        // 현재 화면/액티비티 업데이트
        currentScreen = screenType
        currentActivity = activity
        
        // 채팅 리스너 설정
        chatListener?.let {
            chatListeners[screenType] = it
        }
        
        // 화면 타입별 리소스 초기화
        initializeScreenResources(screenType, activity)
        
        Log.d(TAG, "활성 화면 변경: $screenType")
    }
    
    /**
     * 화면별 리소스 초기화
     * @param screenType 화면 타입
     * @param activity 현재 액티비티
     */
    private fun initializeScreenResources(screenType: Int, activity: Activity) {
        // BLE 초기화 확인
        if (!bleInitialized) {
            initialize(activity)
        }
        
        when (screenType) {
            SCREEN_MAIN -> {
                // 메인 화면에서는 주변 스캔 활성화
                startNeighborScanning(activity)
            }
            SCREEN_DIRECT_CHAT, SCREEN_PUBLIC_CHAT -> {
                // 채팅 화면에서는 채팅 스캔 활성화
                startChatScanning(activity, screenType)
            }
        }
    }
    
    /**
     * 화면 리소스 해제
     * @param screenType 화면 타입
     */
    private fun releaseScreenResources(screenType: Int) {
        when (screenType) {
            SCREEN_MAIN -> {
                // 메인 화면에서 사용 중이던 리소스 해제
                NeighborScanner.stopScanning()
            }
            SCREEN_DIRECT_CHAT, SCREEN_PUBLIC_CHAT -> {
                // 채팅 화면에서 사용 중이던 리소스 해제
                ScannerManager.stopScanning()
                AdvertiserManager.stopAdvertising()
                scanningEnabled = false
                advertisingEnabled = false
            }
        }
    }
    
    /**
     * 주변 스캐닝 시작
     * @param activity 현재 액티비티
     */
    private fun startNeighborScanning(activity: Activity) {
        try {
            // 기본 콜백 추가 - 메인 화면용
            val defaultCallback: (String, String, String) -> Unit = { nickname, deviceId, state ->
                Log.d(TAG, "주변 사용자 발견: $nickname, ID: $deviceId, 상태: $state")
            }
            
            // 등록된 메인 화면 리스너 또는 기본 콜백 사용
            val listener = chatListeners[SCREEN_MAIN] ?: defaultCallback
            
            NeighborScanner.startScanning(activity, listener)
            Log.d(TAG, "주변 사용자 스캔 시작")
        } catch (e: Exception) {
            Log.e(TAG, "주변 사용자 스캔 시작 실패: ${e.message}")
        }
    }
    
    /**
     * 채팅 스캐닝 시작
     * @param activity 현재 액티비티
     * @param screenType 화면 타입
     */
    private fun startChatScanning(activity: Activity, screenType: Int) {
        if (scanningEnabled) return
        
        try {
            // 채팅 리스너 가져오기
            val listener = chatListeners[screenType] ?: return
            
            // 스캔 시작
            ScannerManager.startScanning(activity, listener)
            scanningEnabled = true
            Log.d(TAG, "채팅 스캔 시작: 화면=$screenType")
            
        } catch (e: Exception) {
            Log.e(TAG, "채팅 스캔 시작 실패: ${e.message}")
        }
    }
    
    /**
     * 채팅 메시지 전송
     * @param sender 발신자 ID
     * @param content 메시지 내용
     * @param activity 현재 액티비티
     */
    fun sendChatMessage(sender: String, content: String, activity: Activity) {
        if (!bleInitialized) {
            initialize(activity)
        }
        
        try {
            // BleMessageManager를 통한 메시지 생성
            val message = BleMessageManager.BleMessage(
                sender = sender,
                content = content
            )
            
            // 메시지 전송
            AdvertiserManager.sendMessage(message, activity)
            advertisingEnabled = true
            Log.d(TAG, "메시지 전송 요청: ${message.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 요청 실패: ${e.message}")
        }
    }
    
    /**
     * 일시중지 처리
     * - 앱이 백그라운드로 전환될 때
     */
    fun pause() {
        // 현재 활성 화면 임시 저장
        val savedScreen = currentScreen
        val savedActivity = currentActivity
        
        // 모든 리소스 일시 중지
        pauseAllBleResources()
        
        // 화면 정보 유지
        currentScreen = savedScreen
        
        Log.d(TAG, "BLE 리소스 일시중지: 화면=$savedScreen")
    }
    
    /**
     * 재개 처리
     * - 앱이 포그라운드로 전환될 때
     */
    fun resume() {
        // 저장된 화면 정보가 있으면 재개
        val savedScreen = currentScreen
        val savedActivity = currentActivity
        
        if (savedScreen >= 0 && savedActivity != null) {
            // 현재 화면에 맞게 리소스 다시 초기화
            initializeScreenResources(savedScreen, savedActivity)
            Log.d(TAG, "BLE 리소스 재개: 화면=$savedScreen")
        }
    }
    
    /**
     * 모든 BLE 리소스 일시 중지
     */
    private fun pauseAllBleResources() {
        try {
            // 스캐너 일시 중지
            ScannerManager.pauseScanning()
            NeighborScanner.stopScanning()
            
            // 광고 중지
            AdvertiserManager.stopAdvertising()
            
            scanningEnabled = false
            advertisingEnabled = false
            
        } catch (e: Exception) {
            Log.e(TAG, "BLE 리소스 일시중지 중 오류: ${e.message}")
        }
    }
    
    /**
     * 모든 BLE 리소스 해제
     * - 앱 종료 시
     */
    fun releaseAllBleResources() {
        try {
            // 스캐너 해제
            ScannerManager.release()
            NeighborScanner.stopScanning()
            
            // 광고자 해제
            AdvertiserManager.release()
            
            // 메시지 매니저 해제
            BleMessageManager.release()
            
            // 상태 초기화
            currentScreen = -1
            currentActivity = null
            bleInitialized = false
            scanningEnabled = false
            advertisingEnabled = false
            chatListeners.clear()
            
            Log.d(TAG, "모든 BLE 리소스 해제 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "BLE 리소스 해제 중 오류: ${e.message}")
        }
    }
} 