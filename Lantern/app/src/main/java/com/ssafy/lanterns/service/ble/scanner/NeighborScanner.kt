package com.ssafy.lanterns.service.ble.scanner

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import kotlinx.coroutines.sync.Mutex

object NeighborScanner {
    private const val TAG = "NeighborScanner"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    
    // 기기 ID를 키로 사용하는 맵
    val userMap = mutableMapOf<String, NearbyUser>() // <deviceId, user object>
    
    // 필터링에 사용할 마지막 처리 시간 맵
    private val lastProcessedTime = mutableMapOf<String, Long>() // <deviceId, timestamp>

    // 로컬 사용자 닉네임 (추가)
    private var myNickname: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private const val SCAN_TIMEOUT = 60000L // 60초로 증가 (30초→60초, 더 오래 기기 유지)
    private const val CLEANUP_INTERVAL = 20000L // 20초로 증가 (상수값 수정)
    private const val SCAN_RESTART_INTERVAL = 6000L // 6초로 조정 (8초→6초, 더 자주 스캔)
    private const val PROCESSING_INTERVAL = 300L // 300ms로 감소 (500ms→300ms, 더 자주 처리)
    private const val SCAN_WINDOW = 5000L // 5초로 증가 (4초→5초, 더 오래 스캔)

    // BLE 광고 패킷 최대 길이 및 관련 상수
    // private const val MAX_NICKNAME_LENGTH = 6 // NeighborDiscoveryConstants로 이동
    // private const val HASH_LENGTH = 3 // NeighborDiscoveryConstants로 이동
    
    // 제조사 ID 상수 정의 - 광고자와 일치시킴
    // private const val MANUFACTURER_ID_USER = 0xFFFF // BleConstants로 이동
    // private const val MANUFACTURER_ID_LOCATION = 0xFFFE // BleConstants로 이동
    
    // 로그 출력 제어용 상수
    private const val DEBUG = true // 개발 중 true, 배포 시 false

    private val restartHandler = Handler(Looper.getMainLooper())
    private var isCleanupRunning = false
    private var scanRestartRunnable: Runnable? = null

    // RSSI 값 이력을 저장하는 맵 추가
    private val rssiHistory = mutableMapOf<String, MutableList<Int>>()
    private const val MAX_RSSI_HISTORY = 5 // 저장할 최대 RSSI 값 개수

    // 공유 데이터 접근을 위한 뮤텍스
    private val userMapMutex = Mutex()

    /**
     * 주변 사용자 데이터 클래스
     * @param nickname 사용자 닉네임
     * @param bleId BLE 스캔으로 받은 고유 ID (예: "123")
     * @param depth 신호 깊이 (거리 관련)
     * @param lastSeen 마지막 발견 시간
     * @param lat 위도 (마이크로 단위)
     * @param lng 경도 (마이크로 단위)
     * @param rssi RSSI 신호 강도 값
     */
    data class NearbyUser(
        val nickname: String,
        val bleId: String, // deviceId에서 bleId로 변경
        val depth: Int,
        var lastSeen: Long,
        val lat: Int,
        val lng: Int,
        val rssi: Int = -127
    )

    /**
     * 스캐너 초기화
     * - 블루투스 어댑터 설정
     * - 리소스 초기화
     */
    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (DEBUG) Log.d(TAG, "스캐너 초기화 완료, 블루투스 어댑터 상태: ${bluetoothAdapter != null}")
        
        // 초기화 시 맵 비우기
        userMap.clear()
        lastProcessedTime.clear()
    }

    /**
     * 로컬 사용자 닉네임 설정
     * - 로컬 DB에서 가져온 사용자 닉네임 설정
     */
    fun setMyNickname(nickname: String) {
        myNickname = nickname
        if (DEBUG) Log.d(TAG, "로컬 사용자 닉네임 설정: $nickname")
    }

    /**
     * BLE 스캔 시작
     * - 주변 기기 스캔 시작
     * - 필터링 및 처리된 결과를 콜백으로 반환
     * 
     * @param activity 스캔 요청 액티비티 컨텍스트
     * @param onMessageReceived 스캔 결과 콜백 (닉네임, BLE ID, 상태)
     */
    fun startScanning(activity: Activity, onMessageReceived: (name: String, bleId: String, state: String) -> Unit) {
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        // 이전 스캔 중지
        stopScanning()
        if (DEBUG) Log.d(TAG, "이전 스캔 중지 완료, 새 스캔 준비")

        // 필터 설정 - 우리 앱에서 사용하는 제조사 ID만 필터링
        val userInfoFilter = ScanFilter.Builder()
            .setManufacturerData(
                BleConstants.MANUFACTURER_ID_USER,
                null,
                null
            )
            .build()
            
        val locationFilter = ScanFilter.Builder()
            .setManufacturerData(
                BleConstants.MANUFACTURER_ID_LOCATION,
                null,
                null
            )
            .build()
            
        // 모든 필터를 포함한 목록 생성 (여러 필터 중 하나라도 매칭되면 결과 수신)
        val scanFilters = listOf(userInfoFilter, locationFilter)
        if (DEBUG) Log.d(TAG, "스캔 필터 설정 완료: 제조사 ID ${BleConstants.MANUFACTURER_ID_USER}와 ${BleConstants.MANUFACTURER_ID_LOCATION} 필터링")

        // 스캔 설정 변경 - 안정성 개선을 위해 ScanMode를 LOW_LATENCY로 설정
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // BALANCED → LOW_LATENCY로 변경하여 연결성 향상
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // 모든 일치 항목에 대해 콜백 호출
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 적극적인 매칭 모드 (더 많은 기기 발견)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // 최대한 많은 광고 매칭
            .setReportDelay(0) // 지연 없이 즉시 결과 보고
            .build()

        // 스캔 콜백 구현
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    // 디버그 모드에서만 모든 스캔 결과 로깅
                    if (DEBUG) Log.d(TAG, "스캔 결과 수신: 기기 주소=${scanResult.device.address}, RSSI=${scanResult.rssi}")
                    processBleScanResult(scanResult, onMessageReceived)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "스캔 실패: $errorCode")
                
                // 오류 코드별 처리 추가
                when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> {
                        Log.d(TAG, "이미 스캔이 실행 중입니다. 재시작하지 않습니다.")
                        // 이미 실행 중인 경우 재시작하지 않음
                    }
                    else -> {
                        // 다른 오류는 일정 시간 후 재시도
                        handler.postDelayed({
                            try {
                                // 다시 시작
                                try {
                                    bluetoothLeScanner?.startScan(scanFilters, scanSettings, this)
                                    Log.d(TAG, "스캔 실패 후 재시도")
                                } catch (se: SecurityException) {
                                    Log.e(TAG, "스캔 재시도 중 권한 오류: ${se.message}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "스캔 재시도 실패: ${e.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "스캔 재시도 실패: ${e.message}")
                            }
                        }, 10000) // 10초 후 재시도
                    }
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                if (DEBUG) Log.d(TAG, "배치 스캔 결과: ${results?.size}개 발견")
                results?.forEach { result ->
                    processBleScanResult(result, onMessageReceived)
                }
            }
        }

        // 스캔 시작 - 간헐적 스캔 패턴 구현
        startIntervalScanning(scanFilters, scanSettings, scanCallback)
        
        // 오래된 사용자 정보 제거를 위한 타이머 시작
        startCleanupTimer()
    }
    
    /**
     * 간헐적 스캔 패턴 구현
     * - Android 스캔 빈도 제한 회피를 위해 짧은 스캔 후 대기하는 패턴 사용
     */
    private fun startIntervalScanning(scanFilters: List<ScanFilter>, scanSettings: ScanSettings, callback: ScanCallback?) {
        // 기존 재시작 작업 제거
        scanRestartRunnable?.let { restartHandler.removeCallbacks(it) }
        
        // 새 간헐적 스캔 작업 정의
        scanRestartRunnable = object : Runnable {
            override fun run() {
                try {
                    if (callback != null) {
                        // 스캔 시작 - SecurityException 처리 추가
                        try {
                            bluetoothLeScanner?.startScan(scanFilters, scanSettings, callback)
                            if (DEBUG) Log.d(TAG, "🔄 간헐적 스캔 시작")
                        } catch (se: SecurityException) {
                            // 권한 없음 오류 발생
                            Log.e(TAG, "BLE 스캔 권한이 없습니다: ${se.message}")
                        } catch (e: Exception) {
                            Log.e(TAG, "BLE 스캔 시작 중 오류: ${e.message}")
                        }
                        
                        // SCAN_WINDOW 후에 스캔 중지
                        handler.postDelayed({
                            try {
                                bluetoothLeScanner?.stopScan(callback)
                                if (DEBUG) Log.d(TAG, "🔄 간헐적 스캔 중지 (다음 주기 대기)")
                            } catch (se: SecurityException) {
                                Log.e(TAG, "BLE 스캔 중지 중 권한 오류: ${se.message}")
                            } catch (e: Exception) {
                                Log.e(TAG, "BLE 스캔 중지 중 오류: ${e.message}")
                            }
                        }, SCAN_WINDOW)
                    }
                    // 다음 스캔 주기 예약
                    restartHandler.postDelayed(this, SCAN_RESTART_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "간헐적 스캔 중 오류: ${e.message}")
                }
            }
        }
        
        // 최초 실행 - 즉시 시작
        scanRestartRunnable?.run()
    }

    /**
     * BLE 스캔 결과 처리
     * - 제조사 데이터에서 사용자 정보 추출
     * - 중복 필터링 및 처리
     */
    private fun processBleScanResult(scanResult: ScanResult, onMessageReceived: (name: String, bleId: String, state: String) -> Unit) {
        try {
            val scanRecord = scanResult.scanRecord ?: return
            
            val menufacturerData = scanRecord.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_USER)
            if (menufacturerData == null) {
                return
            }

            val latLng = scanRecord.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_LOCATION)
            // UTF-8로 디코딩 시도, 실패 시 기본 캐릭터셋 사용
            val combined = try {
                String(menufacturerData, Charsets.UTF_8)
            } catch (e: Exception) {
                String(menufacturerData) // 폴백
            }
            
            if (DEBUG) Log.d(TAG, "유효한 Lantern 기기 발견: ${scanResult.device.address}, RSSI: ${scanResult.rssi}, Data: $combined")
            
            val userInfo = combined.split(",")
            
            if (userInfo.isEmpty()) {
                if (DEBUG) Log.d(TAG, "잘못된 형식의 데이터 (userInfo 비어있음): $combined")
                return
            }
            
            // 닉네임과 ID 분리 ("닉네임#ID" 형식)
            val nicknameAndId = userInfo[0].split("#")
            if (nicknameAndId.size != 2) {
                if (DEBUG) Log.d(TAG, "닉네임과 ID 형식 오류: ${userInfo[0]}")
                return
            }
            
            val nickname = nicknameAndId[0]
            val bleId = nicknameAndId[1] // shortHash 대신 bleId 사용
            val state = if (userInfo.size > 1) userInfo[1].toIntOrNull() ?: 0 else 0
            
            if (nickname.isBlank() || bleId.isBlank()) {
                if (DEBUG) Log.d(TAG, "닉네임 또는 bleId가 비어있음: nickname='$nickname', bleId='$bleId'")
                return
            }

            if (nickname == myNickname) {
                if (DEBUG) Log.d(TAG, "자신의 광고 데이터 무시: $nickname")
                return
            }
            
            var lat = 0
            var lng = 0
            
            if (latLng != null && latLng.size >= 8) {
                try {
                    lat = ((latLng[0].toInt() and 0xFF) shl 24) or
                          ((latLng[1].toInt() and 0xFF) shl 16) or
                          ((latLng[2].toInt() and 0xFF) shl 8) or
                          (latLng[3].toInt() and 0xFF)
                    
                    lng = ((latLng[4].toInt() and 0xFF) shl 24) or
                          ((latLng[5].toInt() and 0xFF) shl 16) or
                          ((latLng[6].toInt() and 0xFF) shl 8) or
                          (latLng[7].toInt() and 0xFF)
                    if (DEBUG) Log.d(TAG, "바이너리 위치 데이터 파싱 성공: $lat, $lng")
                    
                    // 실제 위치 값 계산 및 로깅 (디버깅용)
                    val actualLat = lat / NeighborDiscoveryConstants.LOCATION_PRECISION
                    val actualLng = lng / NeighborDiscoveryConstants.LOCATION_PRECISION
                    Log.d(TAG, "사용자 ${nickname} 실제 위치: $actualLat, $actualLng (원시값: $lat, $lng)")
                } catch (e: Exception) {
                    Log.e(TAG, "바이너리 위치 데이터 파싱 오류: ${e.message}")
                }
            }
            
            val rssi = scanResult.rssi
            val currentTime = System.currentTimeMillis()
            
            // deviceId 대신 bleId를 사용
            val lastTime = lastProcessedTime[bleId] ?: 0L
            if (currentTime - lastTime < PROCESSING_INTERVAL) {
                return
            }
            lastProcessedTime[bleId] = currentTime
            
            val deviceRssiHistory = rssiHistory.getOrPut(bleId) { mutableListOf() }
            deviceRssiHistory.add(rssi)
            if (deviceRssiHistory.size > MAX_RSSI_HISTORY) {
                deviceRssiHistory.removeAt(0)
            }
            val averageRssi = deviceRssiHistory.average().toInt()
            val distanceMeters = calculateDistance(averageRssi)
            
            val user = NearbyUser(
                nickname = nickname,
                bleId = bleId, // deviceId 대신 bleId 사용
                depth = state,
                lastSeen = currentTime,
                lat = lat,
                lng = lng,
                rssi = averageRssi
            )
            
            // userMap의 키를 bleId로 사용
            val existingUser = userMap[bleId]
            
            if (existingUser == null || averageRssi > existingUser.rssi) {
                // 만약 기존 사용자의 닉네임과 현재 닉네임이 다른데 bleId가 같다면, 로그를 남겨 확인 (Advertiser 쪽 문제일 수 있음)
                if (existingUser != null && existingUser.nickname != nickname) {
                    Log.w(TAG, " 동일 bleId($bleId)에 다른 닉네임 감지: 기존='${existingUser.nickname}', 새_닉네임='$nickname'. 새 정보로 업데이트합니다.")
                }
                userMap[bleId] = user // bleId를 키로 사용
                
                Log.d(TAG, "사용자 ${nickname}(${bleId}) 업데이트: " +
                    "RSSI=$averageRssi (원본: $rssi), 위치=$lat,$lng, 거리=${String.format("%.2f", distanceMeters)}m, 상태=$state")
                
                // 콜백 호출 시 nickname과 bleId, state 전달
                onMessageReceived(nickname, bleId, state.toString())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스캔 결과 처리 중 오류: ${e.message}", e) // 스택 트레이스 포함
        }
    }

    /**
     * RSSI 값을 거리(미터)로 변환하는 Path Loss 모델 구현
     * @param rssi 측정된 RSSI 값
     * @return 예상 거리 (미터)
     */
    private fun calculateDistance(rssi: Int): Double {
        // 자유 공간 경로 손실 모델 사용 (환경에 따라 조정 필요)
        val txPower = -59 // 1미터 거리에서의 예상 RSSI (환경에 따라 조정)
        
        // 음수 RSSI 값 처리 (절대값으로 계산)
        val absRssi = Math.abs(rssi)
        val absTxPower = Math.abs(txPower)
        
        if (absRssi == 0 || absTxPower == 0) {
            return 0.0
        }
        
        // 환경 인자 (N) - 환경에 따라 조정 가능
        // 2.0: 자유 공간, 2.5-3.0: 일반적인 실내, 3.0-5.0: 장애물이 많은 환경
        val environmentFactor = 2.5
        
        // 거리 계산: d = 10^((TxPower - RSSI)/(10 * N))
        return Math.pow(10.0, (absTxPower - absRssi) / (10.0 * environmentFactor))
    }

    /**
     * 스캔 중지
     */
    fun stopScanning() {
        if (bluetoothLeScanner == null || scanCallback == null) {
            Log.d(TAG, "Scanner not initialized or callback is null, cannot stop.")
            return
        }
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "BLE 스캔 중지 요청")
        } catch (se: SecurityException) {
            Log.e(TAG, "BLE 스캔 중지 중 권한 오류: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 스캔 중지 중 오류: ${e.message}")
        }
        scanCallback = null // 콜백 참조 해제

        scanRestartRunnable?.let {
            restartHandler.removeCallbacks(it)
            Log.d(TAG, "간헐적 스캔 재시작 작업 제거")
        }
        scanRestartRunnable = null

        // 스캔 관련 데이터 초기화 (선택적: 앱 로직에 따라 유지할 수도 있음)
        // userMap.clear()
        // lastProcessedTime.clear()
        // rssiHistory.clear()
        // Log.d(TAG, "스캔 관련 데이터 초기화")
        
        isCleanupRunning = false // 정리 타이머 플래그도 초기화 (startCleanupTimer와 연관)
        handler.removeCallbacksAndMessages(null) // 핸들러의 다른 작업들도 정리 (만약 있다면)
    }

    /**
     * 주기적인 사용자 정보 정리 타이머 시작
     * - 오래된 사용자 정보 자동 제거
     */
    private fun startCleanupTimer() {
        if (isCleanupRunning) return // 중복 실행 방지
        
        isCleanupRunning = true
        handler.removeCallbacksAndMessages(null) // 기존 타이머 제거
        
        val cleanupRunnable: Runnable = object : Runnable {
            override fun run() {
                try {
                    removeOldUsers()
                    if (DEBUG) Log.d(TAG, "주기적 사용자 정보 정리 완료")
                    
                    // 주기적으로 다음 정리 작업 예약 (20초마다)
                    if (isCleanupRunning) {
                        handler.postDelayed(this, CLEANUP_INTERVAL)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "사용자 정보 정리 중 오류: ${e.message}")
                }
            }
        }
        
        // 최초 실행 지연 없이 바로 시작 후 주기적 실행
        handler.post(cleanupRunnable)
        
        if (DEBUG) Log.d(TAG, "사용자 정보 정리 타이머 시작됨 (${CLEANUP_INTERVAL}ms 간격)")
    }
    
    /**
     * 오래된 사용자 정보 제거
     * - 지정된 시간 이상 스캔되지 않은 사용자 제거
     */
    private fun removeOldUsers() {
        val currentTime = System.currentTimeMillis()
        val toRemove = mutableListOf<String>() // 제거할 사용자 ID 목록

        // 사용자 맵에서 오래된 항목 찾기
        userMap.forEach { (bleId, user) ->
            // 마지막 감지 후 30초가 지난 사용자 제거
            if (currentTime - user.lastSeen > 30000) {
                toRemove.add(bleId)
                if (DEBUG) Log.d(TAG, "오래된 사용자 제거 대상: ${user.nickname} (${bleId}), 마지막 감지 후 ${(currentTime - user.lastSeen) / 1000}초 경과")
            }
        }

        // 오래된 사용자 제거
        toRemove.forEach { bleId ->
            userMap.remove(bleId)
            rssiHistory.remove(bleId)
            lastProcessedTime.remove(bleId)
            if (DEBUG) Log.d(TAG, "사용자 제거 완료: ${bleId}")
        }

        // 제거된 사용자 수 로깅
        if (toRemove.isNotEmpty()) {
            Log.i(TAG, "${toRemove.size}명의 오래된 사용자 정보 제거됨")
        }
    }

    /**
     * 현재 발견된 모든 사용자 정보 로깅
     * - 디버깅용 함수로, 발견된 모든 사용자의 정보를 로그로 출력
     */
    fun logAllUsers() {
        if (userMap.isEmpty()) {
            Log.d(TAG, "발견된 사용자 없음")
            return
        }
        
        Log.d(TAG, "=== 현재 발견된 사용자 목록 (${userMap.size}명) ===")
        userMap.forEach { (bleId, user) ->
            val latActual = user.lat / 1000.0
            val lngActual = user.lng / 1000.0
            Log.d(TAG, "사용자: ${user.nickname} (${bleId})")
            Log.d(TAG, "  - 위치: 원시값(${user.lat}, ${user.lng}) → 실제값($latActual, $lngActual)")
            Log.d(TAG, "  - RSSI: ${user.rssi}, 마지막 발견: ${System.currentTimeMillis() - user.lastSeen}ms 전")
        }
        Log.d(TAG, "===================================================")
    }
}