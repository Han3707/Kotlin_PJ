package com.example.bletest

// 필요한 안드로이드 및 코틀린 라이브러리 import
import android.Manifest
import a
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bletest.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

// 권한 확인 누락 경고 무시 (코드 내에서 확인하므로)
@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    // 로그 출력을 위한 태그
    private val TAG = "BleMeshTestKt"
    // ViewBinding 인스턴스 (지연 초기화)
    private lateinit var binding: ActivityMainBinding

    // --- Bluetooth 관련 객체 (지연 초기화 또는 nullable) ---
    // 시스템 블루투스 서비스 접근 매니저
    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }
    // 블루투스 어댑터 (기기의 블루투스 하드웨어 제어)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    // BLE Advertising(광고) 제어 객체
    private var advertiser: BluetoothLeAdvertiser? = null
    // BLE Scanning(탐색) 제어 객체
    private var scanner: BluetoothLeScanner? = null
    // GATT 서버 역할 수행 객체 (다른 기기의 연결 요청 수신)
    private var gattServer: BluetoothGattServer? = null
    // 메시지 교환에 사용될 핵심 GATT Characteristic
    private var meshCharacteristic: BluetoothGattCharacteristic? = null

    // --- 앱 상태 변수 ---
    // 현재 기기의 메시 네트워크 ID (A, B, C 중 하나)
    private var myDeviceId: String = ""
    // 메시 서비스 실행 여부 플래그
    private var isServiceRunning = false
    // 코루틴 작업을 위한 스코프 (백그라운드 IO 작업에 적합한 Dispatcher 사용)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    // --- 연결 및 네트워크 정보 관리 ---
    // 현재 연결된 클라이언트 기기 목록 (Key: Bluetooth 주소, Value: BluetoothDevice)
    private val connectedClients = ConcurrentHashMap<String, BluetoothDevice>()
    // 현재 내가 연결한 서버 목록 (Key: Bluetooth 주소, Value: BluetoothGatt - 연결 관리 객체)
    private val connectedServers = ConcurrentHashMap<String, BluetoothGatt>()
    // 현재 연결 시도 중인 기기 목록 (Key: Bluetooth 주소, Value: true) - 중복 연결 방지용
    private val connectingDevices = ConcurrentHashMap<String, Boolean>()
    // 네트워크 내에서 발견된 모든 기기의 ID 목록 (자신 포함, Thread-safe Set)
    private val knownDevices = CopyOnWriteArraySet<String>()

    // 정보 메시지 브로드캐스트 Debounce (짧은 시간 내 중복 전송 방지) 관련 변수
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    // UI 업데이트 등을 위한 메인 스레드 핸들러
    private val handler = Handler(Looper.getMainLooper())

    // --- ActivityResultLauncher (권한 및 블루투스 활성화 결과 처리) ---
    // 여러 권한 요청 결과를 처리하는 런처
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // 모든 요청된 권한이 승인되었는지 확인
            if (permissions.all { it.value }) {
                log("모든 권한이 허용되었습니다.")
                // 권한 승인 시 메시 서비스 시작 로직 실행
                startMeshService()
            } else {
                log("필수 권한 중 일부 또는 전체가 거부되었습니다.")
                Toast.makeText(this, "앱 실행을 위해 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    // 블루투스 활성화 요청 결과를 처리하는 런처
    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                log("블루투스가 활성화되었습니다.")
                // 블루투스 활성화 성공 시 권한 확인 단계로 진행
                checkPermissionsAndStartService()
            } else {
                log("블루투스 활성화가 거부되었습니다.")
                Toast.makeText(this, "메시 서비스를 사용하려면 블루투스를 켜야 합니다.", Toast.LENGTH_LONG).show()
            }
        }

    // --- Activity 생명주기 메서드 ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding 초기화
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그 TextView 스크롤 가능하게 설정
        binding.tvLog.movementMethod = ScrollingMovementMethod()

        // 서비스 시작/중지 버튼 리스너
        binding.btnStartService.setOnClickListener {
            if (!isServiceRunning) { // 서비스가 실행 중이지 않으면
                myDeviceId = binding.etDeviceId.text.toString().trim().uppercase()
                // ID 유효성 검사 (A, B, C)
                if (myDeviceId.isNotEmpty() && myDeviceId in listOf("A", "B", "C")) {
                    knownDevices.clear() // 기존 목록 초기화
                    knownDevices.add(myDeviceId) // 자기 자신 ID 추가
                    checkBluetoothEnabled() // 1. 블루투스 활성화 확인
                } else {
                    log("유효한 기기 ID(A, B, C)를 입력하세요.")
                    Toast.makeText(this, "유효한 기기 ID(A, B, C)를 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            } else { // 서비스가 실행 중이면 중지
                stopMeshService()
            }
        }

        // Ping 전송 버튼 리스너
        binding.btnSendPing.setOnClickListener {
            val targetId = binding.etTargetId.text.toString().trim().uppercase()
            if (targetId.isNotEmpty() && isServiceRunning) {
                // 네트워크에서 알고 있는 기기인지 또는 자기 자신인지 확인 후 전송
                if (knownDevices.contains(targetId) || targetId == myDeviceId) {
                    sendMessage(targetId, "ping")
                } else {
                    log("알 수 없는 목표 ID: $targetId. 알려진 기기: $knownDevices")
                    Toast.makeText(this, "목표($targetId)를 알 수 없습니다. 네트워크 정보를 기다리거나 확인하세요.", Toast.LENGTH_LONG).show()
                }
            } else if (!isServiceRunning) {
                Toast.makeText(this, "서비스가 실행 중이 아닙니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "목표 ID를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 액티비티 종료 시 호출
    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy 호출됨, 서비스 정리...")
        stopMeshService() // 앱 종료 시 자원 정리
        coroutineScope.cancel() // 진행 중인 코루틴 작업 취소
        debounceHandler.removeCallbacksAndMessages(null) // Debounce 핸들러 콜백 제거
    }

    // --- 블루투스 활성화 확인 ---
    private fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { // 어댑터가 null이 아니면
            if (!it.isEnabled) { // 블루투스가 비활성화 상태이면
                log("블루투스 활성화 요청...")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                // Android 12 이상은 BLUETOOTH_CONNECT 권한 필요 (아래에서 체크함)
                requestEnableBluetoothLauncher.launch(enableBtIntent) // 활성화 요청 시작
            } else { // 이미 활성화 상태이면
                log("블루투스가 이미 활성화되어 있습니다.")
                checkPermissionsAndStartService() // 다음 단계: 권한 확인
            }
        } ?: run { // 어댑터가 null이면 (블루투스 미지원 기기)
            log("블루투스를 지원하지 않는 기기입니다.")
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
        }
    }

    // --- 권한 확인 ---
    private fun checkPermissionsAndStartService() {
        val requiredPermissions = mutableListOf<String>()

        // Android 12 (S, API 31) 이상 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // 위치 권한 (스캔에 필요)
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        // 필요한 권한이 있으면 요청, 없으면 서비스 시작
        if (requiredPermissions.isNotEmpty()) {
            log("필수 권한 요청: ${requiredPermissions.joinToString()}")
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            log("모든 필수 권한이 이미 허용되었습니다.")
            startMeshService()
        }
    }

    // --- 메시 서비스 시작/중지 로직 ---
    private fun startMeshService() {
        if (isServiceRunning) {
            log("서비스가 이미 실행 중입니다.")
            return
        }
        // 서비스 시작 전 필수 권한 재확인
        if (!hasRequiredPermissions()) {
            log("서비스 시작에 필요한 권한이 부족합니다.")
            checkPermissionsAndStartService() // 권한 다시 요청
            return
        }

        log("메시 서비스 시작 중... ID: $myDeviceId")
        isServiceRunning = true
        // UI 업데이트
        binding.btnStartService.text = "Stop Service"
        binding.btnSendPing.isEnabled = true
        binding.etDeviceId.isEnabled = false

        // BLE 제어 객체 초기화
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        scanner = bluetoothAdapter?.bluetoothLeScanner

        // 초기화 실패 시 서비스 중단
        if (advertiser == null || scanner == null) {
            log("BLE Advertiser 또는 Scanner 초기화 실패.")
            handler.post { stopMeshService() }
            return
        }

        // 코루틴으로 BLE 작업 시작
        coroutineScope.launch {
            try {
                startGattServer() // GATT 서버 시작
                delay(200) // 서버 준비 시간
                startAdvertising() // Advertising 시작
                delay(100) // Advertising 시작 시간
                startScanning() // Scanning 시작
                log("Advertising, Scanning, GATT 서버 시작 완료. 알려진 기기: $knownDevices")
            } catch (e: SecurityException) {
                log("보안 예외 발생 (권한 문제): ${e.message}")
                handler.post { stopMeshService() }
            } catch (e: IllegalStateException) {
                log("잘못된 상태 오류 (GATT 문제): ${e.message}")
                handler.post { stopMeshService() }
            } catch (e: Exception) {
                log("서비스 시작 중 예외: ${e.localizedMessage}")
                handler.post { stopMeshService() }
            }
        }
    }

    private fun stopMeshService() {
        if (!isServiceRunning && connectedServers.isEmpty() && connectedClients.isEmpty() && gattServer == null) {
            return
        }
        log("메시 서비스 중지 중...")
        isServiceRunning = false
        binding.btnStartService.text = "Start Service"
        binding.btnSendPing.isEnabled = false
        binding.etDeviceId.isEnabled = true

        coroutineScope.launch {
            try {
                stopScanning()
                stopAdvertising()
                disconnectAll()
                delay(200)
                stopGattServer()
            } catch (e: SecurityException) {
                log("서비스 중지 중 보안 예외: ${e.message}")
            } catch(e: Exception) {
                log("서비스 중지 중 오류: ${e.message}")
            } finally {
                // 상태 변수 및 리소스 완전 정리
                connectedClients.clear()
                connectedServers.clear()
                connectingDevices.clear()
                knownDevices.clear()
                advertiser = null
                scanner = null
                gattServer = null
                meshCharacteristic = null
                log("메시 서비스가 중지되었습니다.")
            }
        }
    }

    // 모든 GATT 연결 해제
    private fun disconnectAll() {
        log("모든 연결 해제 시도...")
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            log("연결 해제 권한 없음")
            return
        }
        // 1. 서버로서 연결된 클라이언트 연결 해제
        gattServer?.let { server ->
            val clientAddresses = connectedClients.keys.toList()
            clientAddresses.forEach { address ->
                connectedClients[address]?.let { device ->
                    try {
                        log("서버: 클라이언트 연결 해제 시도: ${device.name ?: address}")
                        server.cancelConnection(device) // 콜백에서 connectedClients.remove 처리
                    } catch (e: Exception) {
                        log("서버: 클라이언트 연결 해제 실패 ($address): ${e.message}")
                        connectedClients.remove(address) // 실패 시 강제 제거
                    }
                }
            }
        }
        // 2. 클라이언트로서 연결한 서버 연결 해제
        val serverAddresses = connectedServers.keys.toList()
        serverAddresses.forEach { address ->
            connectedServers[address]?.let { gatt ->
                try {
                    log("클라이언트: 서버 연결 해제 시도: ${gatt.device.name ?: address}")
                    gatt.disconnect() // 콜백에서 connectedServers.remove 처리 + close 호출
                    // gatt.close() // disconnect 후 콜백에서 close 하는 것이 더 안정적일 수 있음
                } catch (e: Exception) {
                    log("클라이언트: 서버 연결 해제 실패 ($address): ${e.message}")
                    connectedServers.remove(address) // 오류 시 강제 제거
                    try { gatt.close() } catch (_: Exception) {} // close 재시도
                }
            }
        }
        log("모든 연결 해제 요청 완료.")
    }

    // --- Advertising ---
    private fun startAdvertising() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) { log("Advertising 권한 없음"); return }
        advertiser?.let { adv ->
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(Constants.MESH_SERVICE_UUID)
                .build()
            adv.startAdvertising(settings, data, advertiseCallback)
        } ?: log("Advertiser null, Advertising 불가.")
    }

    private fun stopAdvertising() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            log("Advertising 중지 요청됨.")
        } catch (e: Exception) { log("Advertising 중지 실패: ${e.message}") }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { log("Advertising 시작 성공.") }
        override fun onStartFailure(errorCode: Int) { log("Advertising 시작 실패: $errorCode"); handleAdvertiseError(errorCode) }
    }

    private fun handleAdvertiseError(errorCode: Int) {
        when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> log("오류: Advertise 데이터 크기 초과 (31 Bytes).")
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> log("오류: 시스템 Advertiser 개수 제한 초과.")
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> log("정보: Advertising 이미 시작됨.")
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> log("오류: Advertising 내부 오류.")
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> log("오류: 기기가 Advertising 미지원.")
            else -> log("알 수 없는 Advertising 오류: $errorCode")
        }
    }

    // --- Scanning ---
    private fun startScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) { log("Scanning 권한 없음"); return }
        scanner?.let { scn ->
            val scanFilter = ScanFilter.Builder().setServiceUuid(Constants.MESH_SERVICE_UUID).build()
            val filters = listOf(scanFilter)
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scn.startScan(filters, settings, scanCallback)
            log("Scanning 시작됨 (서비스 UUID: ${Constants.MESH_SERVICE_UUID}).")
        } ?: log("Scanner null, Scanning 불가.")
    }

    private fun stopScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        try {
            scanner?.stopScan(scanCallback)
            log("Scanning 중지 요청됨.")
        } catch (e: Exception) { log("Scanning 중지 실패: ${e.message}") }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceAddress = device.address
                val deviceName = device.name ?: "Unknown"

                // 유효한 연결 대상인지 확인
                if (deviceAddress == bluetoothAdapter?.address ||
                    connectedServers.containsKey(deviceAddress) ||
                    connectedClients.containsKey(deviceAddress) ||
                    isConnecting(deviceAddress)) {
                    return
                }
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

                log("메시 노드 발견: $deviceName ($deviceAddress)")
                // 중복 연결 시도 방지
                if (connectingDevices.putIfAbsent(deviceAddress, true) == null) {
                    log("GATT 연결 시도 -> $deviceAddress")
                    handler.post { // Main 스레드에서 연결 시작
                        try {
                            device.connectGatt(this@MainActivity, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
                        } catch (e: Exception) {
                            log("GATT 연결 시작 오류 ($deviceAddress): ${e.message}")
                            connectingDevices.remove(deviceAddress) // 실패 시 플래그 제거
                        }
                    }
                }
            }
        }
        override fun onScanFailed(errorCode: Int) { log("Scanning 실패: $errorCode"); handleScanError(errorCode) }
        private fun handleScanError(errorCode: Int) {
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> log("정보: 스캔 이미 시작됨.")
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> log("오류: 스캔 앱 등록 실패.")
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> log("오류: 스캔 내부 오류.")
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> log("오류: 스캔 미지원 기기.")
                else -> log("알 수 없는 스캔 오류: $errorCode")
            }
        }
    }

    // --- GATT Server ---
    private fun startGattServer() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) throw SecurityException("GATT 서버 시작 권한 없음")
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
            ?: throw IllegalStateException("GATT 서버 열기 실패")

        val service = BluetoothGattService(Constants.MESH_SERVICE_UUID.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        meshCharacteristic = BluetoothGattCharacteristic(
            Constants.MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(BluetoothGattDescriptor(Constants.CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }

        if (!service.addCharacteristic(meshCharacteristic)) {
            stopGattServer()
            throw IllegalStateException("Characteristic 추가 실패")
        }
        if (gattServer?.addService(service) == false) {
            stopGattServer()
            throw IllegalStateException("Service 추가 실패")
        }

        log("GATT 서버 시작 및 서비스 추가 완료.")
    }

    private fun stopGattServer() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        try {
            gattServer?.close()
            gattServer = null
            log("GATT 서버 닫힘.")
        } catch (e: Exception) { log("GATT 서버 닫기 오류: ${e.message}") }
    }

    // GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            device ?: return
            val deviceAddress = device.address
            val deviceName = device.name ?: deviceAddress

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT 서버: 클라이언트 연결됨 - $deviceName ($deviceAddress)")
                connectedClients[deviceAddress] = device
                // 새 클라이언트 연결 시 내 정보 전송 (딜레이 후)
                coroutineScope.launch {
                    delay(1000)
                    sendDeviceInfo(deviceAddress)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT 서버: 클라이언트 연결 해제됨 - $deviceName ($deviceAddress), status=$status")
                connectedClients.remove(deviceAddress)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            device ?: return; characteristic ?: return; value ?: return;
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            var responseStatus = BluetoothGatt.GATT_FAILURE

            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                val messageStr = String(value, StandardCharsets.UTF_8)
                log("GATT 서버: 메시지 수신 from ${device.name ?: device.address} - '$messageStr'")

                val parts = messageStr.split(Constants.MSG_TYPE_DELIMITER, limit = 2)
                val messageType = parts.getOrNull(0)
                val messageContent = parts.getOrNull(1)

                if (messageContent != null) {
                    when (messageType) {
                        Constants.MSG_TYPE_INFO -> { processDeviceInfoMessage(messageContent); responseStatus = BluetoothGatt.GATT_SUCCESS }
                        Constants.MSG_TYPE_APP -> { processAppMessage(messageContent, device.address); responseStatus = BluetoothGatt.GATT_SUCCESS }
                        else -> log("경고: 알 수 없는 메시지 타입: $messageType")
                    }
                } else { log("경고: 잘못된 메시지 타입 형식: $messageStr") }
            } else { log("경고: 알 수 없는 Characteristic 쓰기 요청: ${characteristic.uuid}") }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, responseStatus, offset, if (responseStatus == BluetoothGatt.GATT_SUCCESS) value else null)
                    ?: log("GATT 서버 응답 실패 (서버 null)")
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            device ?: return; descriptor ?: return; value ?: return
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            var status = BluetoothGatt.GATT_FAILURE
            if (descriptor.uuid == Constants.CCCD_UUID) {
                if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    log("GATT 서버: ${device.name ?: device.address} 알림 활성화")
                    status = BluetoothGatt.GATT_SUCCESS
                } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    log("GATT 서버: ${device.name ?: device.address} 알림 비활성화")
                    status = BluetoothGatt.GATT_SUCCESS
                } else {
                    log("GATT 서버: 잘못된 CCCD 값 수신")
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                }
            } else { log("경고: 알 수 없는 Descriptor 쓰기 요청: ${descriptor.uuid}") }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, status, offset, value)
                    ?: log("GATT 서버 CCCD 응답 실패 (서버 null)")
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            device ?: return; characteristic ?: return
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            var status = BluetoothGatt.GATT_FAILURE
            var responseValue: ByteArray? = null
            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                responseValue = "ID:$myDeviceId Status:OK".toByteArray(StandardCharsets.UTF_8)
                status = BluetoothGatt.GATT_SUCCESS
            } else { log("경고: 알 수 없는 Characteristic 읽기 요청: ${characteristic.uuid}") }
            gattServer?.sendResponse(device, requestId, status, offset, responseValue)
                ?: log("GATT 서버 읽기 응답 실패 (서버 null)")
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            device ?: return; descriptor ?: return
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            var status = BluetoothGatt.GATT_FAILURE
            var responseValue: ByteArray? = null
            if (descriptor.uuid == Constants.CCCD_UUID) {
                // TODO: 클라이언트별 실제 CCCD 상태 읽어와야 함
                responseValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE // 기본값
                status = BluetoothGatt.GATT_SUCCESS
            } else { log("경고: 알 수 없는 Descriptor 읽기 요청: ${descriptor.uuid}") }
            gattServer?.sendResponse(device, requestId, status, offset, responseValue)
                ?: log("GATT 서버 Desc 읽기 응답 실패 (서버 null)")
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            // 알림 전송 완료 콜백
            if (status != BluetoothGatt.GATT_SUCCESS) log("알림 전송 실패 to ${device?.address}, status: $status")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            // 서비스 추가 결과 콜백
            if (status == BluetoothGatt.GATT_SUCCESS) log("GATT 서비스 추가 성공: ${service?.uuid}")
            else log("GATT 서비스 추가 실패: status=$status")
        }
    }

    // --- GATT Client ---
    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt ?: return
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: deviceAddress
            setConnecting(deviceAddress, false) // 연결 시도 완료

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("GATT 클라이언트: 서버 연결됨 - $deviceName ($deviceAddress)")
                    connectedServers[deviceAddress] = gatt
                    handler.postDelayed({
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                            log("서비스 탐색 시작 -> $deviceAddress")
                            if (!gatt.discoverServices()) { log("서비스 탐색 시작 실패: $deviceAddress"); disconnectAndCloseGatt(gatt) }
                        }
                    }, 600)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("GATT 클라이언트: 서버 연결 해제됨 - $deviceName ($deviceAddress)")
                    disconnectAndCloseGatt(gatt)
                }
            } else {
                log("GATT 클라이언트: 연결 오류 status=$status - $deviceName ($deviceAddress)")
                disconnectAndCloseGatt(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt ?: return
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("GATT 클라이언트: 서비스 발견 성공 - $deviceAddress")
                val service = gatt.getService(Constants.MESH_SERVICE_UUID.uuid)
                val characteristic = service?.getCharacteristic(Constants.MESH_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    log("메시 Characteristic 찾음, 알림 활성화 시도 -> $deviceAddress")
                    enableNotifications(gatt, characteristic)
                    // 연결 및 서비스 발견 후 내 정보 전송
                    coroutineScope.launch {
                        delay(1000) // 알림 설정 시간 확보
                        sendDeviceInfo(deviceAddress, gatt)
                    }
                } else {
                    log("메시 서비스 또는 Characteristic 찾을 수 없음: $deviceAddress"); disconnectAndCloseGatt(gatt)
                }
            } else {
                log("GATT 클라이언트: 서비스 발견 오류 status=$status - $deviceAddress")
                disconnectAndCloseGatt(gatt)
            }
        }

        // Android 13 (API 33) 이상 알림 콜백
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val deviceAddress = gatt.device.address
            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                val messageStr = String(value, StandardCharsets.UTF_8)
                log("GATT 클라이언트: 알림 수신 from $deviceAddress - '$messageStr'")

                val parts = messageStr.split(Constants.MSG_TYPE_DELIMITER, limit = 2)
                val messageType = parts.getOrNull(0)
                val messageContent = parts.getOrNull(1) ?: run { log("..."); return }

                when (messageType) {
                    Constants.MSG_TYPE_INFO -> processDeviceInfoMessage(messageContent)
                    Constants.MSG_TYPE_APP -> {
                        val appParts = messageContent.split(Constants.PAYLOAD_DELIMITER, limit = 3)
                        if (appParts.size == 3) {
                            val destId = appParts[0]; val sourceId = appParts[1]; val payload = appParts[2]
                            if (destId == myDeviceId) {
                                log(">> 나($myDeviceId)에게 온 알림 from $sourceId: $payload")
                                handler.post { handleReceivedMessage(sourceId, payload) }
                            }
                        } else { log("경고: 잘못된 APP 메시지 형식 (알림): $messageContent") }
                    }
                    else -> log("경고: 알 수 없는 메시지 타입 수신 (알림): $messageType")
                }
            }
        }

        // API 33 미만 알림 콜백 (호환성)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                onCharacteristicChanged(gatt, characteristic, characteristic.value ?: byteArrayOf())
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            gatt ?: return; characteristic ?: return
            val deviceAddress = gatt.device.address
            val message = String(characteristic.value ?: byteArrayOf(), StandardCharsets.UTF_8)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("GATT 클라이언트: 쓰기 실패 to $deviceAddress - status=$status, msg='$message'")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            gatt ?: return; descriptor ?: return
            val deviceAddress = gatt.device.address
            if (descriptor.uuid == Constants.CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("GATT 클라이언트: CCCD 쓰기 성공 (알림 설정 완료) - $deviceAddress")
                } else {
                    log("GATT 클라이언트: CCCD 쓰기 실패 - $deviceAddress, status=$status")
                }
            }
        }
    }

    // --- 메시지 처리 및 릴레이 ---
    // 일반 앱 메시지 전송 시작 (Ping 등)
    private fun sendMessage(targetId: String, payload: String) {
        if (!isServiceRunning) { log("서비스 미실행"); return }
        // 메시지 포맷: "APP::목표ID|송신자ID|페이로드"
        val appContent = "$targetId${Constants.PAYLOAD_DELIMITER}$myDeviceId${Constants.PAYLOAD_DELIMITER}$payload"
        val fullMessage = "${Constants.MSG_TYPE_APP}${Constants.MSG_TYPE_DELIMITER}$appContent"
        log("APP 메시지 전송 시도 -> $targetId: '$payload'")
        // 릴레이 함수 호출
        coroutineScope.launch { relayMessage(fullMessage, null) }
    }

    // 일반 앱 메시지 처리 (수신 시)
    private fun processAppMessage(appContent: String, senderAddress: String) {
        val parts = appContent.split(Constants.PAYLOAD_DELIMITER, limit = 3)
        if (parts.size == 3) {
            val destId = parts[0]
            val sourceId = parts[1]
            val payload = parts[2]

            if (destId == myDeviceId) {
                // 나에게 온 메시지 처리
                log(">> 나($myDeviceId)에게 온 APP 메시지 from $sourceId: $payload")
                handler.post { handleReceivedMessage(sourceId, payload) }
            } else {
                // 다른 노드에게 온 메시지 -> 릴레이
                log("APP 메시지 릴레이 필요: $destId 에게 from $sourceId (via $myDeviceId)")
                val originalMessage = "${Constants.MSG_TYPE_APP}${Constants.MSG_TYPE_DELIMITER}$appContent"
                coroutineScope.launch { relayMessage(originalMessage, senderAddress) }
            }
        } else {
            log("경고: 잘못된 APP 메시지 내용 형식: $appContent")
        }
    }

    // 메시지 릴레이 (모든 이웃에게 전달, APP 메시지만 해당)
    private fun relayMessage(fullMessage: String, senderAddress: String?) {
        if (!isServiceRunning || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

        // APP 메시지만 릴레이
        if (!fullMessage.startsWith(Constants.MSG_TYPE_APP + Constants.MSG_TYPE_DELIMITER)) {
            return
        }

        val messageBytes = fullMessage.toByteArray(StandardCharsets.UTF_8)
        log("릴레이 시작 (APP): \"$fullMessage\" (From: ${senderAddress ?: "나"})")

        // 1. 클라이언트로서 연결된 서버들에게 Write
        connectedServers.forEach { (address, gatt) ->
            if (address != senderAddress) {
                // 코루틴으로 비동기 쓰기 실행
                coroutineScope.launch { writeToGatt(gatt, messageBytes); delay(50) }
            }
        }

        // 2. 서버로서 연결된 클라이언트들에게 Notify
        gattServer?.let { server ->
            meshCharacteristic?.let { characteristic ->
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    connectedClients.forEach { (address, device) ->
                        if (address != senderAddress) {
                            // 코루틴으로 비동기 알림 실행
                            coroutineScope.launch { notifyClient(server, device, characteristic, messageBytes); delay(50) }
                        }
                    }
                }
            }
        }
    }

    // GATT Characteristic에 데이터 쓰기 (Client 역할 시)
    private suspend fun writeToGatt(gatt: BluetoothGatt, data: ByteArray) {
        // withContext(Dispatchers.IO) { // IO 작업 명시 (선택 사항)
        val service = gatt.getService(Constants.MESH_SERVICE_UUID.uuid)
        val characteristic = service?.getCharacteristic(Constants.MESH_CHARACTERISTIC_UUID)
        if (characteristic != null && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return//@withContext
            try {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, data, characteristic.writeType)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    if (gatt.writeCharacteristic(characteristic)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
                }
                if (writeResult != BluetoothStatusCodes.SUCCESS) {
                    log("GATT 쓰기 실패 (status=$writeResult): ${gatt.device.address}")
                }
            } catch (e: Exception) {
                log("GATT 쓰기 오류 (${gatt.device.address}): ${e.message}")
            }
        }
        // }
    }

    // GATT 클라이언트에게 데이터 알림 (Server 역할 시)
    private suspend fun notifyClient(server: BluetoothGattServer, device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        // withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return//@withContext
        try {
            val notifyResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, data)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                if (server.notifyCharacteristicChanged(device, characteristic, false)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
            }
            if (notifyResult != BluetoothStatusCodes.SUCCESS) {
                log("클라이언트 알림 실패 (status=$notifyResult): ${device.address}")
            }
        } catch (e: Exception) {
            log("클라이언트 알림 오류 (${device.address}): ${e.message}")
        }
        // }
    }


    // 수신된 앱 메시지 최종 처리 (Ping/Pong 등)
    private fun handleReceivedMessage(sourceId: String, payload: String) {
        log("최종 메시지 처리: Source=$sourceId, Payload=$payload")
        if (payload.equals("ping", ignoreCase = true)) {
            log("Ping 수신! Pong 응답 전송 -> $sourceId")
            coroutineScope.launch { sendMessage(sourceId, "pong") }
        } else if (payload.equals("pong", ignoreCase = true)) {
            log("<<<<< Pong 수신 from $sourceId >>>>>")
            handler.post { Toast.makeText(this, "Pong 수신 from $sourceId!", Toast.LENGTH_SHORT).show() }
        } else {
            log("알 수 없는 페이로드 수신: $payload")
        }
    }

    // --- 기기 정보 교환 로직 ---
    /**
     * 현재 알고 있는 기기 목록을 INFO 메시지로 만들어 반환
     */
    private fun createDeviceInfoMessage(): String {
        val deviceListStr = knownDevices.joinToString(Constants.INFO_DEVICE_DELIMITER)
        val content = "$myDeviceId${Constants.PAYLOAD_DELIMITER}$deviceListStr"
        return "${Constants.MSG_TYPE_INFO}${Constants.MSG_TYPE_DELIMITER}$content"
    }

    /**
     * INFO 메시지 수신 시 처리
     * @param infoContent "SourceID|DeviceID1,DeviceID2,..." 형식의 문자열
     */
    private fun processDeviceInfoMessage(infoContent: String) {
        val parts = infoContent.split(Constants.PAYLOAD_DELIMITER, limit = 2)
        if (parts.size != 2) { log("경고: 잘못된 INFO 메시지 내용 형식: $infoContent"); return }
        val sourceId = parts[0]
        val receivedDeviceListStr = parts[1]
        val receivedDeviceSet = receivedDeviceListStr.split(Constants.INFO_DEVICE_DELIMITER)
            .filter { it.isNotEmpty() }.toSet()

        log("INFO 메시지 수신 from $sourceId: $receivedDeviceSet")

        // CopyOnWriteArraySet의 addAll은 추가된 요소가 있으면 true 반환
        val updated = knownDevices.addAll(receivedDeviceSet)

        if (updated) {
            log("새로운 기기 발견! 업데이트된 목록: $knownDevices")
            // 변경된 정보를 다시 모든 이웃에게 전파 (Debounce 적용)
            debounceBroadcastDeviceInfo()
        }
    }

    /**
     * INFO 메시지를 특정 대상에게 전송 (단일 대상)
     */
    private fun sendDeviceInfo(targetAddress: String, gatt: BluetoothGatt? = null) {
        if (!isServiceRunning) return
        val infoMessage = createDeviceInfoMessage()
        val messageBytes = infoMessage.toByteArray(StandardCharsets.UTF_8)
        log("INFO 메시지 전송 -> $targetAddress: $knownDevices")

        coroutineScope.launch {
            if (gatt != null) { // Client -> Server (Write)
                writeToGatt(gatt, messageBytes)
            } else { // Server -> Client (Notify)
                gattServer?.let { server ->
                    connectedClients[targetAddress]?.let { device ->
                        meshCharacteristic?.let { characteristic ->
                            notifyClient(server, device, characteristic, messageBytes)
                        }
                    }
                }
            }
        }
    }

    /**
     * 현재 알고 있는 기기 정보를 모든 연결된 이웃에게 브로드캐스트
     */
    private fun broadcastDeviceInfo() {
        if (!isServiceRunning) return
        val infoMessage = createDeviceInfoMessage()
        val messageBytes = infoMessage.toByteArray(StandardCharsets.UTF_8)
        log("INFO 메시지 브로드캐스트: $knownDevices")

        // 1. 클라이언트로서 연결된 서버들에게 Write
        coroutineScope.launch {
            connectedServers.forEach { (_, gatt) ->
                writeToGatt(gatt, messageBytes)
                delay(50) // 딜레이
            }
        }

        // 2. 서버로서 연결된 클라이언트들에게 Notify
        gattServer?.let { server ->
            meshCharacteristic?.let { characteristic ->
                coroutineScope.launch {
                    connectedClients.forEach { (_, device) ->
                        notifyClient(server, device, characteristic, messageBytes)
                        delay(50) // 딜레이
                    }
                }
            }
        }
    }

    /**
     * 정보 업데이트 후 짧은 시간 내에 여러 번 전파되는 것을 방지 (Debounce)
     */
    private fun debounceBroadcastDeviceInfo() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        debounceRunnable = Runnable {
            broadcastDeviceInfo()
            debounceRunnable = null
        }
        debounceHandler.postDelayed(debounceRunnable!!, 500) // 500ms 후에 실행
    }

    // --- 알림 활성화 (Client 역할 시) ---
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) { log("알림 활성화 권한 없음"); return }
        val deviceAddress = gatt.device.address

        // 1. 로컬 setCharacteristicNotification
        try {
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                log("알림 활성화 실패 (setCharacteristicNotification): $deviceAddress"); return
            }
        } catch (e: SecurityException) { log("알림 활성화 실패 (setCharNot 권한): $deviceAddress"); return }

        // 2. CCCD 쓰기
        val descriptor = characteristic.getDescriptor(Constants.CCCD_UUID)
        if (descriptor != null) {
            try {
                val writeValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, writeValue)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = writeValue
                    if (gatt.writeDescriptor(descriptor)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
                }
                if (writeResult != BluetoothStatusCodes.SUCCESS) {
                    log("CCCD 쓰기 요청 실패 (status=$writeResult): $deviceAddress")
                } // 성공 로그는 콜백에서
            } catch (e: Exception) { log("CCCD 쓰기 오류 ($deviceAddress): ${e.message}") }
        } else { log("CCCD 찾을 수 없음: $deviceAddress") }
    }

    // --- 유틸리티 함수 ---
    // 필요한 모든 권한을 가지고 있는지 확인
    private fun hasRequiredPermissions(): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        // COARSE 권한도 필요하면 추가
        // required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        return required.all { hasPermission(it) }
    }

    // 특정 권한 확인
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    // 현재 연결 시도 중인지 확인
    private fun isConnecting(address: String): Boolean {
        return connectingDevices.getOrDefault(address, false)
    }

    // 연결 시도 상태 설정/해제
    private fun setConnecting(address: String, isConnecting: Boolean) {
        if (isConnecting) {
            connectingDevices[address] = true
        } else {
            connectingDevices.remove(address)
        }
    }

    // 로그 출력 (UI 스레드에서 TextView 업데이트)
    private fun log(message: String) {
        Log.d(TAG, "[${Thread.currentThread().name}] $message") // 로그캣에도 출력
        handler.post {
            val timestamp = getCurrentTimestamp()
            binding.tvLog.append("\n[$timestamp] $message") // 로그 추가
            // 자동 스크롤
            val scrollAmount = (binding.tvLog.layout?.getLineTop(binding.tvLog.lineCount) ?: 0) - binding.tvLog.height
            binding.tvLog.scrollTo(0, if (scrollAmount > 0) scrollAmount else 0)
        }
    }

    // 현재 시간 타임스탬프 문자열 반환
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    // GATT 연결 해제 및 닫기 (안전하게)
    private fun disconnectAndCloseGatt(gatt: BluetoothGatt?) {
        gatt ?: return
        val address = gatt.device.address
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        try {
            gatt.disconnect()
            // disconnect() 호출 후 close() 전에 약간의 시간 필요할 수 있음
            // handler.postDelayed({ gatt.close() }, 100) // 비동기 close
            gatt.close() // 동기 close (주의: ANR 가능성)
            connectedServers.remove(address) // 맵에서 제거
            log("GATT 연결 해제 및 닫기 완료: $address")
        } catch (e: Exception) {
            log("GATT 연결 해제/닫기 오류 ($address): ${e.message}")
            connectedServers.remove(address) // 오류 시에도 제거
            try { gatt.close() } catch (_: Exception) {} // close 재시도
        }
    }
}