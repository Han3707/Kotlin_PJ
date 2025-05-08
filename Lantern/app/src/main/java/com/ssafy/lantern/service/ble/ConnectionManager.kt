package com.ssafy.lantern.service.ble

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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 연결 상태 리스너 인터페이스
 */
interface ConnectionStateListener {
    fun onConnectionStateChanged(device: BluetoothDevice, state: Int)
    fun onSessionRestored(device: BluetoothDevice, success: Boolean)
}

/**
 * 연결 관리자 클래스
 * BLE 연결 상태 모니터링, 자동 재연결, 세션 복구 기능 제공
 */
@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ConnectionManager"
        
        // 기본 최대 재연결 시도 횟수
        private const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
        
        // 기본 재연결 기본 간격 (밀리초)
        private const val DEFAULT_BASE_RECONNECT_DELAY = 1_000L
        
        // 기본 최대 재연결 간격 (밀리초)
        private const val DEFAULT_MAX_RECONNECT_DELAY = 30_000L
        
        // 연결 시간 제한 (밀리초)
        private const val CONNECTION_TIMEOUT = 10_000L
    }
    
    // 블루투스 관련 객체
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    // 연결된 GATT 클라이언트 맵 (디바이스 주소 -> GATT)
    private val connectedDevices = ConcurrentHashMap<String, BluetoothGatt>()
    
    // 세션 정보 (디바이스 주소 -> 세션 정보)
    private val sessionInfo = ConcurrentHashMap<String, SessionInfo>()
    
    // 재연결 시도 정보 (디바이스 주소 -> 재연결 정보)
    private val reconnectInfo = ConcurrentHashMap<String, ReconnectInfo>()
    
    // 연결 상태 리스너
    private val connectionStateListeners = mutableListOf<ConnectionStateListener>()
    
    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 핸들러 (메인 스레드 작업용)
    private val handler = Handler(Looper.getMainLooper())
    
    // 연결 시간 제한 런어블 맵
    private val connectionTimeoutRunnables = ConcurrentHashMap<String, Runnable>()
    
    // 블루투스 상태 변경 리시버
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    handleBluetoothStateChange(state)
                }
            }
        }
    }
    
    init {
        // 블루투스 상태 변경 리시버 등록
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
    }
    
    /**
     * 연결 상태 리스너 등록
     */
    fun addConnectionStateListener(listener: ConnectionStateListener) {
        connectionStateListeners.add(listener)
    }
    
    /**
     * 연결 상태 리스너 제거
     */
    fun removeConnectionStateListener(listener: ConnectionStateListener) {
        connectionStateListeners.remove(listener)
    }
    
    /**
     * GATT 콜백
     */
    private inner class GattCallback(private val device: BluetoothDevice) : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            
            // 연결 시간 제한 취소
            connectionTimeoutRunnables[deviceAddress]?.let {
                handler.removeCallbacks(it)
                connectionTimeoutRunnables.remove(deviceAddress)
            }
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "디바이스 연결됨: $deviceAddress")
                    
                    // 연결된 디바이스 맵에 추가
                    connectedDevices[deviceAddress] = gatt
                    
                    // 재연결 정보 초기화
                    reconnectInfo.remove(deviceAddress)
                    
                    // 서비스 검색
                    gatt.discoverServices()
                    
                    // 연결 상태 알림
                    notifyConnectionStateChanged(gatt.device, BluetoothProfile.STATE_CONNECTED)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "디바이스 연결 해제됨: $deviceAddress, status=$status")
                    
                    // 연결된 디바이스 맵에서 제거
                    connectedDevices.remove(deviceAddress)
                    
                    // 연결 상태 알림
                    notifyConnectionStateChanged(gatt.device, BluetoothProfile.STATE_DISCONNECTED)
                    
                    // 갑작스런 연결 해제인 경우 자동 재연결 시도
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        scheduleReconnect(gatt.device)
                    }
                    
                    // GATT 리소스 해제
                    gatt.close()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 발견 성공: ${gatt.device.address}")
                
                // 세션 복구 시도
                sessionInfo[gatt.device.address]?.let { session ->
                    restoreSession(gatt, session)
                }
            } else {
                Log.e(TAG, "서비스 발견 실패: ${gatt.device.address}, status=$status")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "특성 변경 감지: ${characteristic.uuid}")
        }
    }
    
    /**
     * 블루투스 상태 변경 처리
     */
    private fun handleBluetoothStateChange(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> {
                Log.d(TAG, "블루투스 켜짐")
                // 블루투스가 켜지면 이전에 연결된 디바이스에 재연결 시도
                reconnectAllDevices()
            }
            BluetoothAdapter.STATE_OFF -> {
                Log.d(TAG, "블루투스 꺼짐")
                // 모든 연결 정보 초기화 (GATT 리소스는 유지)
                reconnectInfo.clear()
            }
        }
    }
    
    /**
     * 모든 디바이스에 재연결 시도
     */
    private fun reconnectAllDevices() {
        // 세션 정보에 있는 모든 디바이스에 재연결 시도
        sessionInfo.keys.forEach { address ->
            bluetoothAdapter?.getRemoteDevice(address)?.let { device ->
                scheduleReconnect(device, true)
            }
        }
    }
    
    /**
     * 디바이스에 재연결 예약
     */
    private fun scheduleReconnect(device: BluetoothDevice, immediate: Boolean = false) {
        val deviceAddress = device.address
        
        // 이미 재연결 시도 중인 경우 스킵
        if (reconnectInfo.containsKey(deviceAddress)) {
            Log.d(TAG, "이미 재연결 시도 중: $deviceAddress")
            return
        }
        
        // 세션 정보가 없는 경우 재연결 불필요
        if (!sessionInfo.containsKey(deviceAddress)) {
            Log.d(TAG, "세션 정보 없음, 재연결 필요 없음: $deviceAddress")
            return
        }
        
        // 재연결 정보 생성
        val info = ReconnectInfo(
            device = device,
            attempt = 0,
            maxAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS,
            baseDelay = DEFAULT_BASE_RECONNECT_DELAY,
            maxDelay = DEFAULT_MAX_RECONNECT_DELAY,
            job = null
        )
        
        reconnectInfo[deviceAddress] = info
        
        // 재연결 시도 작업 시작
        val job = coroutineScope.launch {
            if (!immediate) {
                // 첫 재연결 지연 계산
                val delay = calculateReconnectDelay(info.attempt, info.baseDelay, info.maxDelay)
                delay(delay)
            }
            
            performReconnect(device)
        }
        
        // 작업 참조 저장
        info.job = job
    }
    
    /**
     * 재연결 지연 시간 계산 (지수 백오프)
     */
    private fun calculateReconnectDelay(attempt: Int, baseDelay: Long, maxDelay: Long): Long {
        val delay = baseDelay * (1 shl attempt.coerceAtMost(10)) // 2^attempt
        return delay.coerceAtMost(maxDelay)
    }
    
    /**
     * 실제 재연결 수행
     */
    @SuppressLint("MissingPermission")
    private fun performReconnect(device: BluetoothDevice) {
        val deviceAddress = device.address
        val info = reconnectInfo[deviceAddress] ?: return
        
        // 재연결 시도 횟수 증가
        info.attempt++
        
        Log.d(TAG, "재연결 시도: $deviceAddress, 시도=${info.attempt}/${info.maxAttempts}")
        
        // 최대 시도 횟수 초과 시 중단
        if (info.attempt > info.maxAttempts) {
            Log.e(TAG, "최대 재연결 시도 횟수 초과: $deviceAddress")
            reconnectInfo.remove(deviceAddress)
            notifySessionRestored(device, false)
            return
        }
        
        try {
            // 이미 연결된 경우 스킵
            if (connectedDevices.containsKey(deviceAddress)) {
                Log.d(TAG, "이미 연결됨, 재연결 불필요: $deviceAddress")
                reconnectInfo.remove(deviceAddress)
                return
            }
            
            // 재연결 시도
            val gatt = device.connectGatt(context, false, GattCallback(device))
            
            // 연결 시간 제한 설정
            val timeoutRunnable = Runnable {
                Log.e(TAG, "연결 시간 초과: $deviceAddress")
                gatt.disconnect()
                gatt.close()
                
                // 다음 재연결 시도 예약
                val nextJob = coroutineScope.launch {
                    val nextDelay = calculateReconnectDelay(info.attempt, info.baseDelay, info.maxDelay)
                    delay(nextDelay)
                    performReconnect(device)
                }
                
                info.job = nextJob
            }
            
            connectionTimeoutRunnables[deviceAddress] = timeoutRunnable
            handler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT)
        } catch (e: Exception) {
            Log.e(TAG, "재연결 시도 중 오류 발생: $deviceAddress", e)
            
            // 다음 재연결 시도 예약
            val nextJob = coroutineScope.launch {
                val nextDelay = calculateReconnectDelay(info.attempt, info.baseDelay, info.maxDelay)
                delay(nextDelay)
                performReconnect(device)
            }
            
            info.job = nextJob
        }
    }
    
    /**
     * 세션 복구
     */
    @SuppressLint("MissingPermission")
    private fun restoreSession(gatt: BluetoothGatt, session: SessionInfo) {
        val deviceAddress = gatt.device.address
        
        try {
            Log.d(TAG, "세션 복구 시도: $deviceAddress")
            
            // 특성 등록 등 세션 복구 로직 수행
            var success = true
            
            // 필요한 서비스와 특성 찾기
            for (serviceInfo in session.services) {
                val service = gatt.getService(serviceInfo.uuid)
                if (service == null) {
                    Log.e(TAG, "서비스를 찾을 수 없음: ${serviceInfo.uuid}")
                    success = false
                    continue
                }
                
                // 특성 처리
                for (charInfo in serviceInfo.characteristics) {
                    val characteristic = service.getCharacteristic(charInfo.uuid)
                    if (characteristic == null) {
                        Log.e(TAG, "특성을 찾을 수 없음: ${charInfo.uuid}")
                        success = false
                        continue
                    }
                    
                    // 알림 활성화가 필요한 경우
                    if (charInfo.notifyEnabled) {
                        // 알림 활성화
                        gatt.setCharacteristicNotification(characteristic, true)
                        
                        // 디스크립터 찾기
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            Log.e(TAG, "디스크립터를 찾을 수 없음")
                            success = false
                        }
                    }
                }
            }
            
            Log.d(TAG, "세션 복구 ${if (success) "성공" else "실패"}: $deviceAddress")
            notifySessionRestored(gatt.device, success)
            
            // 성공적으로 복구된 경우 재연결 정보 제거
            if (success) {
                reconnectInfo.remove(deviceAddress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "세션 복구 중 오류 발생: $deviceAddress", e)
            notifySessionRestored(gatt.device, false)
        }
    }
    
    /**
     * 세션 정보 저장
     */
    @SuppressLint("MissingPermission")
    fun saveSession(device: BluetoothDevice, services: List<UUID>, characteristics: Map<UUID, List<UUID>>) {
        val deviceAddress = device.address
        
        try {
            Log.d(TAG, "세션 정보 저장: $deviceAddress")
            
            // 서비스 정보 생성
            val serviceInfoList = mutableListOf<ServiceInfo>()
            
            for (serviceUuid in services) {
                val charInfoList = mutableListOf<CharacteristicInfo>()
                
                // 서비스에 속한 특성 정보 생성
                characteristics[serviceUuid]?.forEach { charUuid ->
                    val charInfo = CharacteristicInfo(
                        uuid = charUuid,
                        notifyEnabled = true  // 기본적으로 알림 활성화 필요
                    )
                    charInfoList.add(charInfo)
                }
                
                val serviceInfo = ServiceInfo(
                    uuid = serviceUuid,
                    characteristics = charInfoList
                )
                
                serviceInfoList.add(serviceInfo)
            }
            
            // 세션 정보 생성 및 저장
            val session = SessionInfo(
                deviceAddress = deviceAddress,
                deviceName = device.name ?: "Unknown",
                services = serviceInfoList,
                timestamp = System.currentTimeMillis()
            )
            
            sessionInfo[deviceAddress] = session
            
            Log.d(TAG, "세션 정보 저장 완료: $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "세션 정보 저장 중 오류 발생: $deviceAddress", e)
        }
    }
    
    /**
     * 연결 상태 변경 알림
     */
    private fun notifyConnectionStateChanged(device: BluetoothDevice, state: Int) {
        connectionStateListeners.forEach { listener ->
            try {
                listener.onConnectionStateChanged(device, state)
            } catch (e: Exception) {
                Log.e(TAG, "연결 상태 알림 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 세션 복구 결과 알림
     */
    private fun notifySessionRestored(device: BluetoothDevice, success: Boolean) {
        connectionStateListeners.forEach { listener ->
            try {
                listener.onSessionRestored(device, success)
            } catch (e: Exception) {
                Log.e(TAG, "세션 복구 알림 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 연결 해제
     */
    @SuppressLint("MissingPermission")
    fun disconnect(device: BluetoothDevice) {
        val deviceAddress = device.address
        
        try {
            Log.d(TAG, "디바이스 연결 해제 요청: $deviceAddress")
            
            // 재연결 시도 취소
            cancelReconnect(deviceAddress)
            
            // GATT 연결 해제
            connectedDevices[deviceAddress]?.let { gatt ->
                gatt.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "연결 해제 중 오류 발생: $deviceAddress", e)
        }
    }
    
    /**
     * 재연결 시도 취소
     */
    private fun cancelReconnect(deviceAddress: String) {
        reconnectInfo[deviceAddress]?.let { info ->
            info.job?.cancel()
            reconnectInfo.remove(deviceAddress)
        }
    }
    
    /**
     * 리소스 해제
     */
    @SuppressLint("MissingPermission")
    fun release() {
        try {
            // 모든 GATT 연결 해제 및 리소스 해제
            connectedDevices.values.forEach { gatt ->
                gatt.disconnect()
                gatt.close()
            }
            
            // 맵 초기화
            connectedDevices.clear()
            
            // 모든 재연결 시도 취소
            reconnectInfo.values.forEach { info ->
                info.job?.cancel()
            }
            reconnectInfo.clear()
            
            // 모든 핸들러 콜백 제거
            connectionTimeoutRunnables.values.forEach { runnable ->
                handler.removeCallbacks(runnable)
            }
            connectionTimeoutRunnables.clear()
            
            // 리시버 등록 해제
            context.unregisterReceiver(bluetoothStateReceiver)
            
            Log.d(TAG, "연결 관리자 리소스 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "리소스 해제 중 오류 발생", e)
        }
    }
    
    /**
     * 서비스 정보 클래스
     */
    data class ServiceInfo(
        val uuid: UUID,
        val characteristics: List<CharacteristicInfo>
    )
    
    /**
     * 특성 정보 클래스
     */
    data class CharacteristicInfo(
        val uuid: UUID,
        val notifyEnabled: Boolean
    )
    
    /**
     * 세션 정보 클래스
     */
    data class SessionInfo(
        val deviceAddress: String,
        val deviceName: String,
        val services: List<ServiceInfo>,
        val timestamp: Long
    )
    
    /**
     * 재연결 정보 클래스
     */
    data class ReconnectInfo(
        val device: BluetoothDevice,
        var attempt: Int,
        val maxAttempts: Int,
        val baseDelay: Long,
        val maxDelay: Long,
        var job: Job?
    )
} 