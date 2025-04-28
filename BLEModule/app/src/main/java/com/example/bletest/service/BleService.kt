package com.example.bletest.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.bletest.utils.*
import com.example.bletest.R
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import com.example.bletest.MainActivity
import com.example.bletest.data.source.ble.BleDataSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * BLE 통신을 관리하는 서비스 클래스
 * 주요 역할:
 * 1. 포그라운드 서비스 실행 및 알림 관리
 * 2. BleDataSource와의 인터페이스 제공
 * 3. 장치 ID 관리
 */
@AndroidEntryPoint
class BleService : Service() {
    
    companion object {
        private const val TAG = "BleService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ble_service_channel"
    }
    
    // Binder 제공
    private val binder = LocalBinder()
    
    // 코루틴 스코프
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // BleDataSource 주입
    @Inject
    lateinit var bleDataSource: BleDataSource
    
    // 장치 ID (UUID)
    private var deviceId: String? = null
    
    // 장치 이름
    private var deviceName = "안드로이드 기기"
    
    private lateinit var notificationManager: NotificationManager

    // StateFlow 프록시
    val scanResults: StateFlow<List<ScanResultData>> get() = bleDataSource.scanResults
    val isScanning: StateFlow<Boolean> get() = bleDataSource.isScanning
    val connectionState: StateFlow<DeviceConnectionState> get() = bleDataSource.connectionState
    val messages: StateFlow<List<MessageData>> get() = bleDataSource.messages
    
    // 메시 서비스 실행 상태
    private val _meshRunningState = MutableStateFlow(false)
    val meshRunningState: StateFlow<Boolean> = _meshRunningState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "BLE 서비스 생성됨")
        
        // 고유 장치 ID 생성 (앱 설치마다 다른 ID)
        initDeviceId()
        
        // 장치 이름 설정 
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        bluetoothAdapter?.name?.let {
            deviceName = it
        }
        
        // 전경 서비스 시작
        startForeground()
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // 데이터 소스 상태 구독
        observeDataSource()
    }
    
    private fun observeDataSource() {
        serviceScope.launch {
            // StateFlow 관찰 로직
            // 예: 연결 상태 변화 시 알림 업데이트 등
            connectionState.collect { state ->
                // 상태에 따른 알림 업데이트 등의 작업
                updateNotificationForConnectionState(state)
            }
        }
    }
    
    private fun updateNotificationForConnectionState(state: DeviceConnectionState) {
        // 연결 상태에 따라 알림 내용 업데이트
        val notificationText = when (state.state) {
            ConnectionState.CONNECTED -> "BLE 장치에 연결됨: ${state.name ?: state.address}"
            ConnectionState.CONNECTING -> "BLE 장치에 연결 시도 중..."
            ConnectionState.DISCONNECTED -> "BLE 서비스가 실행 중입니다"
            ConnectionState.ERROR -> "BLE 연결 오류 발생: ${state.errorMessage ?: "알 수 없는 오류"}"
            else -> "BLE 서비스가 실행 중입니다"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Service")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createPendingIntent())
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BLE 서비스 시작됨")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BLE 서비스 종료됨")
        
        // 스캔 중지 및 연결 해제
        bleDataSource.stopScan()
        bleDataSource.disconnect()
        
        // 메시 중지
        if (_meshRunningState.value) {
            stopMesh()
        }
        
        // 자원 해제
        bleDataSource.close()
        
        // 코루틴 취소
        serviceScope.cancel()
    }
    
    /**
     * 서비스를 전경 서비스로 시작
     */
    private fun startForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 채널 생성 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE 서비스 실행 중"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 전경 서비스 시작
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * 장치 ID 초기화
     */
    private fun initDeviceId() {
        // SharedPreferences에서 UUID 가져오기 또는 새로 생성
        val prefs = getSharedPreferences("BlePrefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString()
        
        // 새로 생성된 경우 저장
        if (!prefs.contains("deviceId")) {
            prefs.edit().putString("deviceId", deviceId).apply()
        }
    }
    
    /**
     * 권한 확인 메서드
     */
    private fun hasRequiredPermissions(): Boolean {
        return PermissionHelper.hasRequiredPermissions(this)
    }
    
    // --- 데이터 소스에 위임하는 BLE 기능 ---
    
    /**
     * BLE 장치 스캔 시작
     */
    fun startScan() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return
        }
        
        bleDataSource.startScan()
    }
    
    /**
     * BLE 스캔 중지
     */
    fun stopScan() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return
        }
        
        bleDataSource.stopScan()
    }
    
    /**
     * BLE 장치에 연결
     */
    fun connectToDevice(address: String) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return
        }
        
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val device = bluetoothAdapter?.getRemoteDevice(address)
            
            if (device != null) {
                Log.d(TAG, "장치 연결 시도: $address")
                bleDataSource.connect(device)
            } else {
                Log.e(TAG, "장치를 찾을 수 없습니다: $address")
            }
        } catch (e: Exception) {
            Log.e(TAG, "장치 연결 오류: ${e.message}")
        }
    }
    
    /**
     * 장치 연결 해제
     */
    fun disconnectDevice(address: String) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return
        }
        
        // BleDataSource는 현재 연결된 장치에 대한 disconnect()만 제공
        // 특정 주소에 대한 연결 해제가 필요하면 확장 필요
        bleDataSource.disconnect()
    }
    
    /**
     * 모든 장치 연결 해제
     */
    private fun disconnectAllDevices() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return
        }
        
        bleDataSource.disconnect() // 현재 구현상 단일 연결만 지원
    }
    
    /**
     * 메시지 전송
     */
    fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "필요한 권한이 없습니다")
            return false
        }
        
        return bleDataSource.sendMessage(targetId, messageType, content)
    }
    
    /**
     * 메시 서비스 시작
     */
    fun startMesh(): Boolean {
        // 디바이스 ID가 설정되어 있는지 확인
        if (deviceId.isNullOrEmpty()) {
            Log.e(TAG, "디바이스 ID가 설정되지 않았습니다.")
            return false
        }
        
        try {
            Log.d(TAG, "메시 서비스 시작 중... ID: $deviceId")
            
            // BleDataSource에 메시 네트워크 시작 위임
            // 현재 구현에서는 간단히 광고 시작만 함
            bleDataSource.startAdvertising()
            
            // 실제 메시 구현에서는 서버 시작, 라우팅 테이블 초기화 등 필요
            bleDataSource.startServer()
            
            _meshRunningState.value = true
            Log.d(TAG, "메시 서비스 시작 성공")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "메시 서비스 시작 중 오류: ${e.message}")
            return false
        }
    }
    
    /**
     * 메시 서비스 중지
     */
    fun stopMesh() {
        try {
            Log.d(TAG, "메시 서비스 중지 중...")
            
            // BleDataSource에 메시 네트워크 중지 위임
            bleDataSource.stopAdvertising()
            bleDataSource.stopServer()
            
            _meshRunningState.value = false
            Log.d(TAG, "메시 서비스가 중지되었습니다.")
        } catch (e: Exception) {
            Log.e(TAG, "메시 서비스 중지 중 오류: ${e.message}")
        }
    }
    
    /**
     * 장치 이름 가져오기
     */
    fun getDeviceName(): String {
        return deviceName
    }
    
    /**
     * 장치 ID 가져오기
     */
    fun getBleDeviceId(): String? {
        return deviceId
    }
    
    /**
     * 스캔 중인지 확인
     */
    fun isScanning(): Boolean {
        return bleDataSource.isScanning.value
    }
    
    /**
     * Local Binder 클래스
     */
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("BLE Service")
        .setContentText("BLE 서비스가 실행 중입니다")
        .setSmallIcon(R.drawable.ic_bluetooth)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(createPendingIntent())
        .build()
} 