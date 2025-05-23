package com.ssafy.lanterns.service.ble.advertiser

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.service.ble.advertiser.GlobalApplication
import java.util.UUID
import kotlin.math.absoluteValue
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.NeighborDiscoveryConstants

object NeighborAdvertiser{
    private const val TAG = "NeighborAdvertiser"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var lastAdvertiseCallback: AdvertiseCallback? = null
    private var isAdvertising = false
    
    // 닉네임 최대 길이 제한 (바이트 수)
    private const val MAX_NICKNAME_LENGTH = NeighborDiscoveryConstants.MAX_NICKNAME_LENGTH_BYTES
    // 해시 길이 제한 (3자리)
    private const val HASH_LENGTH = NeighborDiscoveryConstants.DEVICE_ID_HASH_LENGTH
    
    // 제조사 ID 상수 정의
    private const val MANUFACTURER_ID_USER = BleConstants.MANUFACTURER_ID_USER
    private const val MANUFACTURER_ID_LOCATION = BleConstants.MANUFACTURER_ID_LOCATION
    
    // 광고 재시도 간격 및 횟수
    private const val RETRY_DELAY = 500L // ms
    private const val MAX_RETRY_COUNT = 3
    private var retryCount = 0
    
    // 광고 설정 상수
    private const val ADVERTISE_INTERVAL = 3000 // 3초 간격으로 단축 (4초→3초)
    
    // 광고 패킷 2개 활용을 위한 상수
    private const val USE_SCAN_RESPONSE = true // 스캔 응답 패킷 활용 (총 62바이트까지 가능)
    
    // 마지막 광고 데이터 캐싱용 변수들
    private var lastNickname = ""
    private var lastDeviceId = ""
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastState = 0
    
    private val handler = Handler(Looper.getMainLooper())
    
    fun init(activity: Activity){
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        Log.d(TAG, "광고자 초기화 완료, 블루투스 어댑터 상태: ${bluetoothAdapter != null}")
    }
    
    // 광고 재시도 작업을 취소하는 메서드 추가
    fun cancelRetries() {
        handler.removeCallbacksAndMessages(null) // 모든 예약된 작업 취소
        retryCount = 0 // 재시도 횟수 초기화
        Log.d(TAG, "모든 광고 재시도 작업이 취소되었습니다.")
    }
    
    /**
     * BLE 광고 시작
     * @param nickname 사용자 닉네임
     * @param deviceId 기기 고유 ID
     * @param lat 위도
     * @param lng 경도
     * @param state 사용자 상태
     * @param activity 액티비티 컨텍스트
     */
    fun startAdvertising(
        nickname: String,
        deviceId: String,
        lat: Double,
        lng: Double,
        state: Int = 0,
        activity: Activity
    ) {
        try {
            // 이전 광고 데이터와 동일한지 확인 (불필요한 재시작 방지)
            if (isAdvertising && 
                nickname == lastNickname && 
                deviceId == lastDeviceId && 
                state == lastState && 
                Math.abs(lat - lastLat) < 0.0001 && // 약 11m 이내 위치 변화는 무시
                Math.abs(lng - lastLng) < 0.0001) {
                Log.d(TAG, "동일한 데이터로 광고 중이므로 재시작하지 않음")
                return
            }
            
            // 현재 데이터 저장
            lastNickname = nickname
            lastDeviceId = deviceId
            lastLat = lat
            lastLng = lng
            lastState = state
            
            // 이전 광고가 있으면 중지
            stopAdvertising()
            
            // Thread.sleep 제거 (메인 스레드 블로킹 방지)
            
            val bluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "블루투스가 비활성화되어 있습니다.")
                return
            }
            
            val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "BLE 광고가 지원되지 않습니다.")
                return
            }
            
            // 데이터 크기 최적화
            // 1. 닉네임 길이 제한 (최대 6바이트)
            val trimmedNickname = if (nickname.length > MAX_NICKNAME_LENGTH) {
                nickname.take(MAX_NICKNAME_LENGTH)
            } else {
                nickname
            }
            
            // 2. deviceId에서 짧은 해시 생성 (3자리로 제한)
            val shortHash = deviceId.hashCode().absoluteValue.toString().takeLast(HASH_LENGTH)
            
            // 3. 위치 정보 정밀도 감소 (소수점 3자리 = 약 110m 정확도)
            val microLat = (lat * NeighborDiscoveryConstants.LOCATION_MULTIPLIER).toInt()
            val microLng = (lng * NeighborDiscoveryConstants.LOCATION_MULTIPLIER).toInt()
            
            // 4. 데이터 분리 및 최적화
            // 닉네임과 해시 결합 (닉네임#해시,상태) - 간단하게 축약
            val userInfoStr = "$trimmedNickname#$shortHash,$state"
            val userInfoData = userInfoStr.toByteArray()
            
            // 위치 정보 바이너리 인코딩 (8바이트)
            val locationData = byteArrayOf(
                // 위도 바이트
                (microLat shr 24).toByte(),
                (microLat shr 16).toByte(),
                (microLat shr 8).toByte(),
                microLat.toByte(),
                // 경도 바이트
                (microLng shr 24).toByte(),
                (microLng shr 16).toByte(),
                (microLng shr 8).toByte(),
                microLng.toByte()
            )
            
            // 데이터 크기 확인 로깅
            Log.d(TAG, "광고 데이터 크기 - 사용자 정보: ${userInfoData.size}바이트, 위치: ${locationData.size}바이트")
            
            // 광고 설정 - 요구사항에 맞게 변경 (광고 간격 3000ms, 모드 LOW_LATENCY)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // BALANCED → LOW_LATENCY로 변경
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // MEDIUM → HIGH로 변경하여 범위 확장
                .setConnectable(false) // 연결 불필요
                .setTimeout(0) // 시간제한 없음 (계속 광고)
                .build()
            
            // 메인 광고 데이터 설정 (주 광고 패킷에 사용자 정보 포함)
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // 기기 이름 제외 (데이터 절약)
                .setIncludeTxPowerLevel(false) // 전송 출력 제외 (데이터 절약)
                .addManufacturerData(MANUFACTURER_ID_USER, userInfoData) // 사용자 정보
                .build()
            
            // 스캔 응답 데이터 설정 (스캔 응답 패킷에 위치 정보 포함)
            val scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(MANUFACTURER_ID_LOCATION, locationData) // 위치 정보
                .build()
            
            Log.d(TAG, "광고 데이터 분할 - 메인 패킷: 사용자 정보 ${userInfoData.size}바이트, 스캔 응답: 위치 정보 ${locationData.size}바이트")
            
            // 재시도 카운터 초기화
            retryCount = 0
            
            // 콜백 설정
            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "광고 시작 성공: $trimmedNickname#$shortHash (사용자 정보: ${userInfoData.size}바이트, 위치 정보: ${locationData.size}바이트)")
                    Log.d(TAG, "광고 위치 정보 확인: lat=${lat}, lng=${lng} → 인코딩 후 lat=${microLat}/1000=${microLat/1000.0}, lng=${microLng}/1000=${microLng/1000.0}")
                    lastAdvertiseCallback = this
                    isAdvertising = true
                }
                
                override fun onStartFailure(errorCode: Int) {
                    val errorMsg = when (errorCode) {
                        ADVERTISE_FAILED_ALREADY_STARTED -> "이미 시작됨"
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "데이터가 너무 큼"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "기능이 지원되지 않음"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "내부 오류"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "광고주가 너무 많음"
                        else -> "알 수 없는 오류 $errorCode"
                    }
                    Log.e(TAG, "광고 시작 실패: $errorMsg (데이터 크기: 사용자=${userInfoData.size}바이트, 위치=${locationData.size}바이트)")
                    isAdvertising = false
                    
                    // 오류 처리 전략 개선
                    when (errorCode) {
                        ADVERTISE_FAILED_ALREADY_STARTED -> {
                            // 이미 시작된 경우는 오류로 처리하지 않음
                            isAdvertising = true
                            Log.d(TAG, "광고가 이미 시작되어 있으므로 계속 진행")
                        }
                        
                        ADVERTISE_FAILED_INTERNAL_ERROR -> {
                            // 내부 오류는 지연 후 재시도
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++
                                val delayTime = RETRY_DELAY * retryCount // 지수적 백오프 적용
                                Log.d(TAG, "내부 오류로 인한 재시도 ${retryCount}/${MAX_RETRY_COUNT} (${delayTime}ms 후)")
                                
                                handler.postDelayed({
                                    // 재시도할 때 더 단순한 광고 데이터 사용
                                    retryAdvertising(activity, settings, userInfoData, this)
                                }, delayTime)
                            } else {
                                Log.e(TAG, "최대 재시도 횟수 초과, 내부 오류로 인한 광고 실패")
                            }
                        }
                        
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                            // 데이터가 너무 큰 경우 위치 정보 제외
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++
                                Log.d(TAG, "데이터 크기 축소 후 재시도 ${retryCount}/${MAX_RETRY_COUNT}")
                                
                                // 사용자 정보만 포함한 단순 광고 데이터
                                val smallerAdvertiseData = AdvertiseData.Builder()
                                    .setIncludeDeviceName(false)
                                    .setIncludeTxPowerLevel(false)
                                    .addManufacturerData(MANUFACTURER_ID_USER, userInfoData)
                                    .build()
                                
                                try {
                                    bluetoothLeAdvertiser.startAdvertising(settings, smallerAdvertiseData, this)
                                } catch (e: Exception) {
                                    Log.e(TAG, "축소된 데이터로 재시도 중 오류", e)
                                }
                            } else {
                                Log.e(TAG, "최대 재시도 횟수 초과, 데이터 크기 문제로 광고 실패")
                            }
                        }
                        
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> {
                            // 광고주가 너무 많은 경우 잠시 후 다시 시도
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++
                                val delayTime = RETRY_DELAY * 2 * retryCount // 더 긴 지연 시간
                                Log.d(TAG, "광고주 과다로 인한 재시도 ${retryCount}/${MAX_RETRY_COUNT} (${delayTime}ms 후)")
                                
                                handler.postDelayed({
                                    retryAdvertising(activity, settings, userInfoData, this)
                                }, delayTime)
                            } else {
                                Log.e(TAG, "최대 재시도 횟수 초과, 광고주 과다로 인한 광고 실패")
                            }
                        }
                        
                        else -> {
                            // 기타 오류는 한 번만 재시도
                            if (retryCount < 1) {
                                retryCount++
                                Log.d(TAG, "일반 오류로 인한 재시도 (1회)")
                                
                                handler.postDelayed({
                                    retryAdvertising(activity, settings, userInfoData, this)
                                }, RETRY_DELAY)
                            } else {
                                Log.e(TAG, "재시도 후에도 실패: $errorMsg")
                            }
                        }
                    }
                }
            }
            
            // 광고 시작 (메인 패킷 + 스캔 응답 패킷)
            try {
                bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
                Log.d(TAG, "BLE 광고 시작 요청 완료 (메인+스캔응답)")
            } catch (e: Exception) {
                Log.e(TAG, "BLE 광고 시작 중 예외 발생: ${e.message}")
                
                // 예외 발생 시 스캔 응답 없이 다시 시도
                if (USE_SCAN_RESPONSE && e.message?.contains("data too large", ignoreCase = true) == true) {
                    Log.d(TAG, "스캔 응답 없이 메인 광고 패킷만으로 재시도")
                    try {
                        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
                    } catch (e2: Exception) {
                        Log.e(TAG, "메인 광고 패킷만으로도 시작 실패: ${e2.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "광고 시작 중 오류 발생", e)
        }
    }
    
    /**
     * 광고 재시도 로직 분리 (코드 중복 감소)
     */
    private fun retryAdvertising(
        activity: Activity,
        settings: AdvertiseSettings,
        userInfoData: ByteArray,
        callback: AdvertiseCallback
    ) {
        try {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "재시도 중 블루투스 광고자를 찾을 수 없음")
                return
            }
            
            // 사용자 정보만 포함한 단순 광고 데이터
            val smallerAdvertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(MANUFACTURER_ID_USER, userInfoData)
                .build()
            
            bluetoothLeAdvertiser.startAdvertising(settings, smallerAdvertiseData, callback)
            Log.d(TAG, "단순화된 데이터로 광고 재시도 시작")
        } catch (se: SecurityException) {
            Log.e(TAG, "BLE 광고 재시도 중 권한 오류: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 광고 재시도 중 오류", e)
        }
    }
    
    fun stopAdvertising(){
        // 먼저 예약된 모든 재시도 작업 취소
        cancelRetries()
        
        if (bluetoothLeAdvertiser != null && lastAdvertiseCallback != null) {
            try {
                bluetoothLeAdvertiser!!.stopAdvertising(lastAdvertiseCallback)
                Log.d(TAG, "광고 중지 요청 성공 (콜백: ${lastAdvertiseCallback?.hashCode()})")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "광고 중지 실패 (아마도 이미 중지됨): ${e.message}")
            } catch (se: SecurityException) {
                Log.e(TAG, "광고 중지 권한 오류: ${se.message}")
            } finally {
                isAdvertising = false
                lastAdvertiseCallback = null // 콜백 참조 제거
                advertiseCallback = null // 현재 콜백도 제거
            }
        } else {
            Log.d(TAG, "광고가 실행 중이지 않거나 콜백이 없음 (Advertiser: ${bluetoothLeAdvertiser != null}, Callback: ${lastAdvertiseCallback != null})")
            isAdvertising = false // 확실히 상태 업데이트
        }
    }
    
    // 광고 상태 확인
    fun isAdvertising(): Boolean {
        return isAdvertising
    }
}