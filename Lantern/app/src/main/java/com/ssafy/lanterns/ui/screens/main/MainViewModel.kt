package com.ssafy.lanterns.ui.screens.main

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.repository.DeviceRepository
import com.ssafy.lanterns.data.repository.UserRepository
import com.ssafy.lanterns.service.ble.advertiser.NeighborAdvertiser
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner
import com.ssafy.lanterns.ui.screens.main.components.NearbyPerson
import com.ssafy.lanterns.utils.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 메인 화면의 상태를 관리하는 ViewModel
 * - 주변 사람 탐색 및 표시 기능 제공
 * - BLE 스캔 및 광고 관리
 * - GPS 위치 정보 관리
 */
data class MainScreenState(
    val isScanning: Boolean = true,              // 항상 스캔 중
    val nearbyPeople: List<NearbyPerson> = emptyList(),
    val showPersonListModal: Boolean = false,
    val buttonText: String = "탐색 중",           // 스캔 상태 텍스트를 더 간결하게 수정
    val subTextVisible: Boolean = true,          // 항상 표시
    val showListButton: Boolean = false,

    // BLE 상태
    val isBleServiceActive: Boolean = true,      // 항상 활성화
    val blePermissionsGranted: Boolean = false,  // BLE 권한 상태
    val isBluetoothEnabled: Boolean = false,     // 블루투스 활성화 상태

    // 프로필 이동용 userId
    val navigateToProfile: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    // BLE 의존성 주입을 위한 Repository 추가
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    /* -------------------------------------------------- *
     * constants
     * -------------------------------------------------- */
    companion object {
        private const val TAG = "MainViewModel"
        private const val AI_ACTIVATION_DEBOUNCE_MS = 2_000L // AI 활성화 디바운스 시간 (ms)
        
        // BLE 상수
        private const val BLE_SCAN_INTERVAL = 3 * 1000L // 3초마다 스캔 결과 업데이트
        private const val ADVERTISE_INTERVAL = 20 * 1000L // 20초마다 광고 갱신
        
        // 최소 위치 업데이트 시간 (밀리초)
        private const val MIN_LOCATION_UPDATE_TIME = 10000L
        // 최소 위치 업데이트 거리 (미터)
        private const val MIN_LOCATION_UPDATE_DISTANCE = 10f
    }

    /* -------------------------------------------------- *
     * UI 상태
     * -------------------------------------------------- */
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    /* -------------------------------------------------- *
     * AI 다이얼로그 상태
     * -------------------------------------------------- */
    private val _aiActive = MutableStateFlow(false)
    val aiActive: StateFlow<Boolean> = _aiActive.asStateFlow()

    private var lastAiActivationTime = 0L // 마지막 AI 활성화 시간 추적

    /* -------------------------------------------------- *
     * BLE 스캔 job
     * -------------------------------------------------- */
    private var scanningJob: Job? = null
    private var advertisingJob: Job? = null
    
    /* -------------------------------------------------- *
     * BLE 관련 변수
     * -------------------------------------------------- */
    private var permissionHelper: PermissionHelper? = null
    private var activity: Activity? = null
    
    // 내 사용자 정보
    private var userId: String = "" // DB에서 가져온 사용자 ID
    
    // 위치 정보 관련 변수와 mutex
    private val locationMutex = Mutex()
    private var myLatitude = 0.0
    private var myLongitude = 0.0
    private var myNickname = "" // 기본값은 빈 문자열로 설정
    
    // 주변 유저 맵과 mutex
    private val nearbyUsersMutex = Mutex()
    private val nearbyUsersMap = mutableMapOf<String, NearbyPerson>()
    
    // 위치 관리자
    private var locationManager: LocationManager? = null
    // 위치 리스너 (onCleared에서 해제 가능하도록 클래스 변수로 선언)
    private var locationListener: LocationListener? = null

    /* -------------------------------------------------- *
     * init
     * -------------------------------------------------- */
    init {
        // ViewModel 생성 시 자동 스캔 시작 (권한/BLE 상태는 화면에서 설정)
        // 실제 BLE 스캔은 updateBlePermissionStatus()에서 권한 확인 후 시작됨
        startScanning()
    }
    
    /* -------------------------------------------------- *
     * Activity 설정 (권한, BLE 초기화용)
     * -------------------------------------------------- */
    /**
     * Activity 설정
     * - 권한 및 BLE 초기화에 필요한 Activity 설정
     * - 위치 관리자 초기화 및 권한 상태 확인
     */
    fun setActivity(activity: Activity) {
        this.activity = activity
        this.permissionHelper = PermissionHelper(activity)
        
        // 위치 관리자 초기화
        locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // 로컬 DB에서 사용자 정보 가져오기 (닉네임 포함)
        loadUserInfoFromDB()
        
        // 권한 및 블루투스 상태 확인
        checkPermissionsAndBluetoothState()
    }
    
    /**
     * 로컬 DB에서 사용자 정보 가져오기
     * - 실제 DB에서 사용자 정보 조회
     */
    private fun loadUserInfoFromDB() {
        viewModelScope.launch {
            try {
                // UserRepository에서 현재 로그인한 사용자 정보 가져오기
                val user = userRepository.getCurrentUser()
                
                // 사용자 정보가 있으면 적용
                if (user != null) {
                    userId = user.userId.toString() // userId를 String으로 변환하여 사용
                    myNickname = user.nickname
                    Log.d(TAG, "로컬 DB에서 사용자 정보 로드: ID=$userId, 닉네임=$myNickname")
                } else {
                    // 사용자 정보가 없는 경우 기본값 설정
                    myNickname = "Guest_${UUID.randomUUID().toString().substring(0, 4)}"
                    Log.d(TAG, "로컬 DB에 사용자 정보 없음, 임시 닉네임 사용: $myNickname")
                }
                
                // 사용자 기기 정보 등록/업데이트
                registerDeviceInfo()
            } catch (e: Exception) {
                Log.e(TAG, "사용자 정보 로드 실패: ${e.message}")
                // 오류 시 기본 닉네임 설정
                myNickname = "Error_${UUID.randomUUID().toString().substring(0, 4)}"
            }
        }
    }
    
    /**
     * 기기 정보 등록/업데이트 
     * - 사용자의 기기 정보를 DB에 저장하고 관리
     */
    private fun registerDeviceInfo() {
        viewModelScope.launch {
            try {
                // 블루투스 어댑터에서 기기 이름 가져오기 (있는 경우)
                val bluetoothName = activity?.let { act ->
                    if (act.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val bluetoothManager = act.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                        bluetoothManager?.adapter?.name ?: "Unknown Device"
                    } else {
                        "Unknown Device" // 권한이 없는 경우 기본값 사용
                    }
                } ?: "Unknown Device"
                
                // 기기 모델명 가져오기
                val deviceModel = android.os.Build.MODEL
                val deviceId = try {
                    android.os.Build.SERIAL
                } catch (se: SecurityException) {
                    // READ_PHONE_STATE 권한이 없는 경우 임의의 ID 생성
                    UUID.randomUUID().toString()
                }
                
                // 기기 정보 등록/업데이트
                deviceRepository.registerDevice(
                    deviceId = deviceId,
                    userId = userId,
                    deviceName = bluetoothName,
                    deviceModel = deviceModel
                )
                
                Log.d(TAG, "기기 정보 등록 완료: $bluetoothName ($deviceModel)")
            } catch (e: Exception) {
                Log.e(TAG, "기기 정보 등록 실패: ${e.message}")
            }
        }
    }
    
    /* -------------------------------------------------- *
     * 권한 및 블루투스 상태 확인
     * -------------------------------------------------- */
    /**
     * 권한 및 블루투스 상태 확인
     * - BLE 및 위치 권한과 블루투스 활성화 상태 확인
     * - 모든 조건이 충족되면 위치 정보 및 BLE 작업 시작
     */
    private fun checkPermissionsAndBluetoothState() {
        permissionHelper?.let { helper ->
            val permissionsGranted = helper.hasPermission()
            val bluetoothEnabled = helper.isBluetoothEnabeld()
            
            _uiState.update { it.copy(
                blePermissionsGranted = permissionsGranted,
                isBluetoothEnabled = bluetoothEnabled
            )}
            
            if (permissionsGranted && bluetoothEnabled) {
                // 위치 정보 가져오기 시작
                startLocationUpdates()
                
                // BLE 스캔/광고 시작
                startBleOperations()
            }
        }
    }
    
    /* -------------------------------------------------- *
     * 위치 정보 가져오기
     * -------------------------------------------------- */
    /**
     * 위치 정보 업데이트 시작
     * - GPS 및 네트워크 기반 위치 정보 수집
     * - 내 위치 정보가 변경될 때마다 BLE 광고 업데이트
     */
    private fun startLocationUpdates() {
        try {
            // 먼저 위치 권한이 있는지 확인
            if (activity?.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
                activity?.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "위치 권한이 없습니다.")
                return
            }
            
            // 위치 정보 리스너 설정
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // 위치 업데이트 시 내 위치 정보 갱신 (스레드 안전하게)
                    viewModelScope.launch {
                        locationMutex.withLock {
                            myLatitude = location.latitude
                            myLongitude = location.longitude
                        }
                        
                        Log.d(TAG, "위치 업데이트: lat=$myLatitude, lng=$myLongitude")
                        
                        // 광고 업데이트 (새로운 위치 정보로)
                        updateAdvertising()
                    }
                }
                
                // 기타 필요한 LocationListener 메서드 구현
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            
            // 위치 정보 요청 시작
            locationManager?.let { manager ->
                // GPS 프로바이더 사용
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_LOCATION_UPDATE_TIME,
                        MIN_LOCATION_UPDATE_DISTANCE,
                        locationListener!!
                    )
                }
                
                // 네트워크 프로바이더 사용 (GPS가 안되는 환경을 위해)
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_LOCATION_UPDATE_TIME,
                        MIN_LOCATION_UPDATE_DISTANCE,
                        locationListener!!
                    )
                }
                
                // 마지막으로 알려진 위치 정보 가져오기
                val lastKnownLocation = try {
                    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (se: SecurityException) {
                    Log.e(TAG, "위치 정보 접근 권한 오류: ${se.message}")
                    null
                }
                
                if (lastKnownLocation != null) {
                    // 스레드 안전하게 위치 정보 업데이트
                    viewModelScope.launch {
                        locationMutex.withLock {
                            myLatitude = lastKnownLocation.latitude
                            myLongitude = lastKnownLocation.longitude
                        }
                        Log.d(TAG, "마지막 위치: lat=$myLatitude, lng=$myLongitude")
                    }
                }
            }
        } catch (e: SecurityException) {
            // 권한이 없는 경우
            Log.e(TAG, "위치 권한 없음: ${e.message}")
        }
    }

    /* ==================================================
     *  AI 다이얼로그 제어
     * ================================================== */

    /** 
     * "헤이 랜턴" 감지 시 호출 
     * - 음성 인식 서비스에서 웨이크워드 감지 시 호출
     * - 디바운싱 처리로 중복 활성화 방지
     */
    fun activateAI() {
        val now = System.currentTimeMillis()
        if (now - lastAiActivationTime < AI_ACTIVATION_DEBOUNCE_MS) {
            Log.d(TAG, "activateAI() 무시 (debounce)")
            return
        }
        lastAiActivationTime = now
        _aiActive.value = true
        Log.d(TAG, "activateAI() → _aiActive = true")
    }

    /** 
     * 다이얼로그 닫힐 때 호출 
     * - AI 대화 종료 시 상태 초기화
     */
    fun deactivateAI() {
        _aiActive.value = false
        Log.d(TAG, "deactivateAI() → _aiActive = false")
    }

    /* ==================================================
     *  BLE 스캔/광고 로직
     * ================================================== */
    
    /** 
     * BLE 작업 시작
     * - BLE 스캔 및 광고 초기화
     * - 주변 기기 탐색 및 내 정보 광고 시작
     */
    private fun startBleOperations() {
        activity?.let { act ->
            // BLE 스캔 및 광고 초기화
            NeighborScanner.init(act)
            NeighborAdvertiser.init(act)
            
            // 스캔 시작
            startRealScanning()
            
            // 내 정보 광고 시작
            startAdvertising()
        }
    }

    /** 
     * 실제 BLE 스캔 시작 
     * - 주변 장치 탐색을 시작하여 주변 사람 정보 수집
     * - 주기적으로 스캔 결과 처리
     */
    private fun startRealScanning() {
        activity?.let { act ->
            // 기존 스캔 취소
            NeighborScanner.stopScanning()
            
            // DB에서 가져온 실제 사용자 닉네임 설정
            NeighborScanner.setMyNickname(myNickname)
            
            // 새로운 스캔 시작
            NeighborScanner.startScanning(act) { nickname, deviceId, depthInfo ->
                // 스캔 결과 처리 (여기서는 간단히 로그만)
                Log.d(TAG, "스캔 결과: $nickname, 기기ID: $deviceId, 깊이 정보: $depthInfo")
                
                // 여기서 필요시 추가 로직 (사용자 저장 등) 구현 가능
            }
            
            // 주기적으로 스캔 결과 처리
            scanningJob = viewModelScope.launch {
                while (true) {
                    delay(BLE_SCAN_INTERVAL)
                    processScanResults()
                }
            }
        }
    }
    
    /** 
     * 내 정보 광고 시작 
     * - 내 위치 정보 및 기기 정보를 주변 기기에 광고
     * - 주기적으로 광고 갱신
     */
    private fun startAdvertising() {
        activity?.let { act ->
            // 고정 deviceId 사용 (기기의 MAC 주소 또는 고유 ID)
            val deviceId = android.os.Build.SERIAL.ifEmpty { 
                act.getSystemService(Context.BLUETOOTH_SERVICE)?.let {
                    (it as BluetoothManager).adapter?.address
                } ?: UUID.randomUUID().toString()
            }
            
            // 광고 시작
            viewModelScope.launch {
                val lat: Double
                val lng: Double
                
                // 위치 정보 접근 시 mutex 사용
                locationMutex.withLock {
                    lat = myLatitude
                    lng = myLongitude
                }
                
                NeighborAdvertiser.startAdvertising(
                    nickname = myNickname,
                    deviceId = deviceId,  // 고정 deviceId 사용
                    lat = lat,
                    lng = lng,
                    state = 0,
                    activity = act
                )
            }
            
            // 주기적으로 광고 갱신
            advertisingJob = viewModelScope.launch {
                while (true) {
                    delay(ADVERTISE_INTERVAL)
                    updateAdvertising()
                }
            }
        }
    }
    
    /** 
     * 광고 업데이트 
     * - 내 최신 위치 정보로 BLE 광고 내용 갱신
     */
    private fun updateAdvertising() {
        activity?.let { act ->
            // 고정 deviceId 사용 (기기의 MAC 주소 또는 고유 ID)
            val deviceId = android.os.Build.SERIAL.ifEmpty { 
                act.getSystemService(Context.BLUETOOTH_SERVICE)?.let {
                    (it as BluetoothManager).adapter?.address
                } ?: UUID.randomUUID().toString()
            }
            
            viewModelScope.launch {
                val lat: Double
                val lng: Double
                
                // 위치 정보 접근 시 mutex 사용
                locationMutex.withLock {
                    lat = myLatitude
                    lng = myLongitude
                }
                
                NeighborAdvertiser.startAdvertising(
                    nickname = myNickname,
                    deviceId = deviceId,  // 항상 동일한 deviceId 사용
                    lat = lat,
                    lng = lng,
                    state = 0,
                    activity = act
                )
            }
        }
    }
    
    /** 
     * 스캔 결과 처리 
     * - BLE 스캔으로 수집한 주변 사람 정보 처리
     * - GPS와 RSSI 정보를 조합하여 정확한 거리 및 방향 계산
     */
    private fun processScanResults() {
        viewModelScope.launch {
            val newNearbyList = mutableListOf<NearbyPerson>()
            val currentTime = System.currentTimeMillis()
            
            // 닉네임 기반으로 중복을 관리하기 위한 맵
            val nicknameBasedData = mutableMapOf<String, Pair<NeighborScanner.NearbyUser, Float>>() // <nickname, <user, distance>>
            
            // 현재 위치 정보 가져오기 (스레드 안전)
            val lat: Double
            val lng: Double
            locationMutex.withLock {
                lat = myLatitude
                lng = myLongitude
            }
            Log.d(TAG, "현재 기기 위치 - lat: $lat, lng: $lng")
            
            // NeighborScanner의 사용자 맵에서 데이터 가져오기 (동기화 블록 사용)
            val userMapCopy = NeighborScanner.userMap.toMap() // 스냅샷 복사
            
            userMapCopy.forEach { (deviceId, user) ->
                // 30초 이내에 수신된 데이터만 처리 (시간 임계값 증가)
                if (currentTime - user.lastSeen < 30000) {
                    // RSSI 기반 거리 계산 - Path Loss Model 사용
                    // d = 10^((RSSI0 - RSSI) / (10 * n))
                    val rssi = user.rssi
                    val rssi0 = -59  // 1m 거리에서의 기준 RSSI 값
                    val pathLossExponent = 2.2  // 실내 환경에서의 경로 손실 지수 (일반적으로 2.0-4.0)
                    
                    // RSSI 값이 유효한 경우에만 계산
                    val rssiDistance = if (rssi != 0 && rssi != -127) {
                        Math.pow(10.0, (rssi0 - rssi) / (10 * pathLossExponent)).toFloat()
                    } else {
                        50f // 기본값 (RSSI 값이 없는 경우)
                    }
                    Log.d(TAG, "사용자 ${user.nickname} (${user.deviceId}) - RSSI: $rssi, rssiDistance: $rssiDistance m")
                    
                    // GPS 거리 계산 (좌표 기반)
                    val userLatitude = user.lat.toDouble()
                    val userLongitude = user.lng.toDouble()
                    val gpsDistance = calculateDistance(lat, lng, userLatitude, userLongitude)
                    Log.d(TAG, "사용자 ${user.nickname} (${user.deviceId}) - GPS lat: $userLatitude, lng: $userLongitude, gpsDistance: $gpsDistance m (받은 user.lat: ${user.lat}, user.lng: ${user.lng})")
                    
                    // 칼만 필터 개념 활용 - RSSI와 GPS 거리의 가중 평균 계산
                    // RSSI는 가까운 거리에서 더 정확하고, GPS는 먼 거리에서 더 정확
                    val rssiWeight = if (rssiDistance < 10) 0.7f else 0.3f
                    val gpsWeight = 1.0f - rssiWeight
                    Log.d(TAG, "사용자 ${user.nickname} (${user.deviceId}) - rssiWeight: $rssiWeight, gpsWeight: $gpsWeight")
                    
                    // 최종 거리 계산 (가중 평균)
                    val rawDistance = (rssiDistance * rssiWeight + gpsDistance * gpsWeight)
                    Log.d(TAG, "사용자 ${user.nickname} (${user.deviceId}) - 최종 rawDistance: $rawDistance m (기존 로그: ${user.nickname} 원시 거리: $rawDistance, RSSI: $rssi)")
                    
                    // 거리를 카테고리화하여 표현 (10, 30, 50, 100, 150, 200, 200+)
                    val categorizedDistance = when {
                        rawDistance < 10f -> 10f
                        rawDistance < 30f -> 30f
                        rawDistance < 50f -> 50f
                        rawDistance < 100f -> 100f
                        rawDistance < 150f -> 150f
                        rawDistance < 200f -> 200f
                        else -> 250f // 200m 초과 표현용
                    }
                    Log.d(TAG, "사용자 ${user.nickname} (${user.deviceId}) - 최종 categorizedDistance: $categorizedDistance m (기존 로그: ${user.nickname} 카테고리화된 거리: $categorizedDistance)")
                    
                    // 닉네임 기반으로 더 가까운 정보를 선택하여 저장
                    val currentPair = nicknameBasedData[user.nickname]
                    if (currentPair == null || categorizedDistance < currentPair.second) {
                        nicknameBasedData[user.nickname] = Pair(user, categorizedDistance)
                    }
                }
            }
            
            // 닉네임 기반으로 정리된 데이터를 처리
            nicknameBasedData.forEach { (nickname, userData) ->
                val (user, distance) = userData
                
                val angle = calculateBearing(
                    lat, lng,
                    user.lat / 1e6, user.lng / 1e6
                )
                
                val signalStrength = calculateSignalStrength(user.rssi)
                
                // 유효한 사용자 이름이 없는 경우 대비
                val displayName = if (user.nickname.isNullOrBlank()) "사용자" else user.nickname
                
                // nearbyPeople 목록에 추가
                newNearbyList.add(
                    NearbyPerson(
                        id = user.deviceId.hashCode(),
                        userId = user.deviceId,
                        name = displayName,
                        distance = distance, // 카테고리화된 거리 사용
                        angle = angle,
                        signalStrength = signalStrength,
                        rssi = user.rssi // RSSI 값 전달
                    )
                )
            }
            
            // 거리 순으로 정렬
            newNearbyList.sortBy { it.distance }
            
            // 동기화 블록 내에서 로컬 맵 업데이트
            nearbyUsersMutex.withLock {
                nearbyUsersMap.clear()
                newNearbyList.forEach { person ->
                    nearbyUsersMap[person.userId] = person
                }
            }
            
            // UI 상태 업데이트 (원자적 연산)
            _uiState.update { currentState -> 
                currentState.copy(nearbyPeople = newNearbyList)
            }
        }
    }
    
    /**
     * RSSI 값으로부터 신호 강도 계산 (0.0 ~ 1.0)
     */
    private fun calculateSignalStrength(rssi: Int): Float {
        // RSSI 값은 일반적으로 -30 (매우 강함) ~ -100 (매우 약함) 범위
        return if (rssi == 0 || rssi == -127) {
            0.3f // 기본값
        } else {
            val normalized = (rssi + 100) / 70f // -100 -> 0, -30 -> 1
            normalized.coerceIn(0f, 1f)
        }
    }

    /**
     * 거리 계산 (하버사인 공식)
     * - 두 지리적 좌표 간의 거리를 계산하는 함수
     * - 지구의 곡률을 고려한 정확한 거리 계산 제공
     * 
     * @param lat1 첫 번째 지점의 위도
     * @param lon1 첫 번째 지점의 경도
     * @param lat2 두 번째 지점의 위도
     * @param lon2 두 번째 지점의 경도
     * @return 두 지점 간의 거리 (미터 단위)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val R = 6371e3 // 지구 반지름 (미터)
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (R * c).toFloat() // 미터 단위 거리
    }

    /**
     * 방위각 계산 (북쪽 기준)
     * - 두 지리적 좌표 간의 방위각을 계산하는 함수
     * - 북쪽 기준 시계 방향 각도 (0-360도) 반환
     * 
     * @param lat1 첫 번째 지점의 위도
     * @param lon1 첫 번째 지점의 경도
     * @param lat2 두 번째 지점의 위도
     * @param lon2 두 번째 지점의 경도
     * @return 방위각 (도 단위, 0-360)
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = lon2 - lon1
        
        val y = sin(Math.toRadians(dLon)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) - 
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(dLon))
        
        // atan2로 각도 계산 (라디안)
        var bearing = Math.toDegrees(atan2(y, x))
        
        // 각도를 0-360 범위로 조정
        if (bearing < 0) {
            bearing += 360.0
        }
        
        return bearing.toFloat()
    }

    /**
     * BLE 스캔 시작 함수
     * - 사용자가 스캔 버튼을 누를 때 호출
     * - 스캔 상태 업데이트 및 관련 UI 상태 변경
     */
    fun startScanning() {
        viewModelScope.launch {
            Log.d(TAG, "스캔 시작")
            
            // UI 상태 업데이트
            _uiState.update { it.copy(
                isScanning = true,
                buttonText = "탐색 중",
                subTextVisible = true,
                isBleServiceActive = true,
                showListButton = false
            )}
            
            // 실제 BLE 스캔 시작 - 권한이 있고 블루투스가 활성화된 경우에만 실행
            startBleOperations()
        }
    }
    
    /** 
     * 스캔 토글 (현재는 항상 스캔 중이므로 미사용) 
     * - 스캔 시작/중지 기능 (현재는 미사용)
     */
    fun toggleScan() { /* no-op */ }

    /* ==================================================
     *  UI 상호작용 헬퍼
     * ================================================== */

    /**
     * 사람 목록 모달 토글
     * - 주변 사람 목록을 표시하거나 숨김
     */
    fun togglePersonListModal() {
        // 닫기만 하는 경우 항상 작동하도록 수정
        if (_uiState.value.showPersonListModal) {
            _uiState.update { it.copy(showPersonListModal = false) }
            return
        }
        
        // 열 때는 주변 사람이 있는 경우에만 작동
        if (_uiState.value.nearbyPeople.isNotEmpty()) {
            _uiState.update { it.copy(showPersonListModal = true) }
        }
    }

    /**
     * 사람 목록 모달 닫기
     * - 모달을 명시적으로 닫는 함수
     */
    fun closePersonListModal() {
        _uiState.update { it.copy(showPersonListModal = false) }
    }

    /**
     * 필요시 스캔 상태 복원
     * - 화면 전환 등으로 스캔이 중단된 경우 재시작
     */
    fun restoreScanningStateIfNeeded() {
        if (scanningJob == null || !_uiState.value.isScanning) {
            scanningJob?.cancel()
            startScanning()
        }
    }

    /**
     * 사용자 선택 시 프로필 이동 처리
     * - 특정 사용자 프로필로 이동하기 위한 상태 설정
     */
    fun onPersonClick(userId: String) {
        _uiState.update { it.copy(navigateToProfile = userId) }
    }

    /**
     * 프로필 화면 이동 후 상태 초기화
     * - 프로필 화면으로 이동한 후 상태를 초기화하여 중복 이동 방지
     */
    fun onProfileScreenNavigated() {
        _uiState.update { it.copy(navigateToProfile = null) }
    }

    /* ==================================================
     *  BLE 권한/상태 처리
     * ================================================== */

    /**
     * BLE 권한 상태 업데이트
     * - 블루투스 권한 허용 여부에 따라 스캔 가능 상태 설정
     * - 권한이 허용되면 BLE 작업 시작
     */
    fun updateBlePermissionStatus(granted: Boolean) {
        _uiState.update { it.copy(blePermissionsGranted = granted) }
        
        if (granted && _uiState.value.isBluetoothEnabled) {
            startLocationUpdates()
            startBleOperations()
        }
    }

    /**
     * 블루투스 상태 업데이트
     * - 블루투스 활성화 여부에 따라 스캔 가능 상태 설정
     * - 활성화되면 BLE 작업 시작, 비활성화되면 BLE 작업 중지
     */
    fun updateBluetoothState(enabled: Boolean) {
        _uiState.update { it.copy(isBluetoothEnabled = enabled) }
        
        if (enabled && _uiState.value.blePermissionsGranted) {
            startLocationUpdates() 
            startBleOperations()
        } else if (!enabled) {
            // 블루투스가 비활성화되면 스캔/광고 중지
            NeighborScanner.stopScanning()
            NeighborAdvertiser.stopAdvertising()
        }
    }

    /* -------------------------------------------------- *
     * ViewModel 소멸 시 자원 정리
     * -------------------------------------------------- */
    override fun onCleared() {
        super.onCleared()
        scanningJob?.cancel()
        advertisingJob?.cancel()
        
        // BLE 서비스 정리
        NeighborScanner.stopScanning()
        NeighborAdvertiser.stopAdvertising()
        
        // 위치 업데이트 중지
        locationManager?.let { manager ->
            try {
                locationListener?.let { listener ->
                    if (activity?.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        activity?.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        manager.removeUpdates(listener)
                        Log.d(TAG, "위치 업데이트 중지 완료")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "위치 업데이트 중지 중 오류: ${e.message}")
            }
        }
    }
}
