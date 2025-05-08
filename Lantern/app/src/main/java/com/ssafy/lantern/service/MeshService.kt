package com.ssafy.lantern.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.ssafy.lantern.data.model.ChatMessage
import com.ssafy.lantern.data.model.MessageType
import com.ssafy.lantern.service.ble.BleComm
import com.ssafy.lantern.service.ble.ConnectionManager
import com.ssafy.lantern.service.ble.ConnectionStateListener
import com.ssafy.lantern.service.network.MeshNetworkLayer
import com.ssafy.lantern.service.provisioning.ProvisioningManager
import com.ssafy.lantern.service.security.KeyType
import com.ssafy.lantern.service.security.SecurityManager
import com.ssafy.lantern.service.session.SessionManager
import com.ssafy.lantern.utils.JsonUtil
import com.ssafy.lantern.service.network.MeshNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메쉬 네트워크 서비스
 * 채팅 메시지 전송 및 수신, 프로비저닝 등의 고수준 기능 제공
 */
@Singleton
class MeshService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleComm: BleComm,
    private val networkLayer: MeshNetworkLayer,
    private val securityManager: SecurityManager,
    private val provisioningManager: ProvisioningManager,
    private val connectionManager: ConnectionManager,
    private val sessionManager: SessionManager
) {
    // 리스너 인터페이스 정의
    interface MessageListener {
        fun onMessageReceived(message: ChatMessage)
    }

    interface StatusListener {
        fun onNetworkStatusChanged(isConnected: Boolean)
    }

    interface ProvisioningListener {
        fun onProvisioningComplete(unicastAddress: Int)
        fun onProvisioningFailed(device: BluetoothDevice, error: String)
    }

    companion object {
        private const val TAG = "MeshService"
        const val BROADCAST_ADDR = 0xFFFF
        
        // 세션 복구 후 메시지 재전송 지연 (밀리초)
        private const val MESSAGE_RESEND_DELAY = 3_000L
        
        // 네트워크 상태 확인 주기 (밀리초)
        private const val NETWORK_CHECK_INTERVAL = 60_000L  // 1분
        
        // 메시지 재전송 주기 (밀리초)
        private const val MESSAGE_RETRY_INTERVAL = 300_000L  // 5분
    }
    
    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 메시지 리스너 (타입 변경)
    private val messageListeners = mutableListOf<MessageListener>()
    
    // 네트워크 상태 리스너 (타입 변경)
    private val statusListeners = mutableListOf<StatusListener>()
    
    // 프로비저닝 리스너 (타입 변경)
    private val provisionListeners = mutableListOf<ProvisioningListener>()
    
    // 로컬 유니캐스트 주소
    private var localAddress = 0x0001  // 기본값
    
    // 난수 생성기
    private val secureRandom = SecureRandom()
    
    // 네트워크 활성화 상태
    private val isNetworkActive = AtomicBoolean(false)
    
    // 서비스 UUID (메쉬 프로비저닝 서비스)
    private val meshServiceUUID = ProvisioningManager.MESH_PROVISIONING_UUID
    
    // 연결 상태 리스너 구현
    private val connectionStateListener = object : ConnectionStateListener {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "디바이스 연결됨: ${device.address}")
                    // 필요한 특성 등록
                    saveDeviceSession(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "디바이스 연결 해제됨: ${device.address}")
                    // 네트워크 레이어에 알림
                    checkNetworkStatus()
                }
            }
        }
        
        override fun onSessionRestored(device: BluetoothDevice, success: Boolean) {
            Log.d(TAG, "세션 복구 ${if (success) "성공" else "실패"}: ${device.address}")
            
            if (success) {
                // 세션 복구 성공 시 일정 시간 후 대기 메시지 재전송
                coroutineScope.launch {
                    delay(MESSAGE_RESEND_DELAY)
                    sessionManager.resendAllPendingMessages()
                }
                
                // 네트워크 상태 업데이트
                checkNetworkStatus()
            }
        }
    }
    
    init {
        // 초기화
        loadSessionData()
        setupNetworkLayer()
        initializeConnectionManagement()
        
        // 마스터 키 생성 및 키 파생
        initializeSecurity()
        
        // 네트워크 상태 확인
        checkNetworkStatus()
        
        // 주기적 작업 시작
        startPeriodicTasks()
    }
    
    /**
     * 세션 데이터 로드
     */
    private fun loadSessionData() {
        // 저장된 로컬 주소 불러오기
        val savedAddress = sessionManager.getLocalAddress()
        if (savedAddress > 0) {
            localAddress = savedAddress
            Log.d(TAG, "저장된 로컬 주소 로드: $localAddress")
        }
    }
    
    /**
     * 네트워크 레이어 설정
     */
    private fun setupNetworkLayer() {
        // 로컬 주소 설정
        networkLayer.setLocalAddress(localAddress)
        
        // 메시지 수신 리스너 등록
        networkLayer.addMessageListener { pdu ->
            if (pdu.type == MessageType.CHAT) {
                try {
                    // 채팅 메시지 파싱
                    val decryptedData = securityManager.decrypt(pdu.body, KeyType.APPLICATION_KEY)
                    if (decryptedData != null) {
                        val chatMessage = JsonUtil.fromBytes(decryptedData)
                        if (chatMessage != null) {
                            Log.d(TAG, "채팅 메시지 수신: $chatMessage")
                            notifyMessage(chatMessage)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "메시지 파싱 중 오류 발생", e)
                }
            }
        }
    }
    
    /**
     * 연결 관리 초기화
     */
    private fun initializeConnectionManagement() {
        // 연결 상태 리스너 등록
        connectionManager.addConnectionStateListener(connectionStateListener)
        
        // 메시지 재전송 콜백 설정
        sessionManager.setMessageResendCallback { type, dst, payload ->
            // 메시지 타입에 따라 재전송
            when (type) {
                MessageType.CHAT -> {
                    // 네트워크 레이어를 통한 전송
                    if (dst == BROADCAST_ADDR) {
                        networkLayer.broadcast(type, payload)
                    } else {
                        networkLayer.unicast(dst, type, payload)
                    }
                    Log.d(TAG, "대기 메시지 재전송: type=$type, dst=$dst")
                }
                else -> {
                    // 다른 메시지 타입 처리
                    Log.d(TAG, "기타 대기 메시지 재전송: type=$type, dst=$dst")
                    if (dst == BROADCAST_ADDR) {
                        networkLayer.broadcast(type, payload)
                    } else {
                        networkLayer.unicast(dst, type, payload)
                    }
                }
            }
        }
    }
    
    /**
     * 주기적 작업 시작
     */
    private fun startPeriodicTasks() {
        // 네트워크 상태 주기적 확인
        coroutineScope.launch {
            while (true) {
                delay(NETWORK_CHECK_INTERVAL)
                checkNetworkStatus()
            }
        }
        
        // 대기 메시지 주기적 재전송
        coroutineScope.launch {
            while (true) {
                delay(MESSAGE_RETRY_INTERVAL)
                if (isNetworkActive.get()) {
                    sessionManager.resendAllPendingMessages()
                }
            }
        }
    }
    
    /**
     * 세션 저장
     */
    private fun saveDeviceSession(device: BluetoothDevice) {
        try {
            // 서비스 및 특성 UUID 목록 정의
            val services = listOf(
                meshServiceUUID,
                UUID.fromString("00001828-0000-1000-8000-00805F9B34FB") // 메쉬 프록시 서비스
            )
            
            // 서비스별 특성 맵 정의
            val characteristics = mapOf(
                meshServiceUUID to listOf(
                    ProvisioningManager.PROVISIONING_DATA_IN_UUID,
                    ProvisioningManager.PROVISIONING_DATA_OUT_UUID
                ),
                UUID.fromString("00001828-0000-1000-8000-00805F9B34FB") to listOf(
                    UUID.fromString("00002ADD-0000-1000-8000-00805F9B34FB"), // 메쉬 프록시 데이터 입력
                    UUID.fromString("00002ADE-0000-1000-8000-00805F9B34FB")  // 메쉬 프록시 데이터 출력
                )
            )
            
            // 세션 정보 저장
            connectionManager.saveSession(device, services, characteristics)
            
            // 마지막 동기화 시간 갱신
            sessionManager.saveLastSyncTime()
            
            Log.d(TAG, "디바이스 세션 정보 저장 완료: ${device.address}")
        } catch (e: Exception) {
            Log.e(TAG, "세션 저장 중 오류 발생", e)
        }
    }
    
    /**
     * 보안 초기화
     */
    private fun initializeSecurity() {
        try {
            // 저장된 키 해시 확인
            val (networkKeyHash, appKeyHash) = sessionManager.getKeyHashes()
            
            if (networkKeyHash != null && appKeyHash != null) {
                // 키가 이미 존재하는지 확인 (SecurityManager 내부 구현에 따라 다름)
                val keysExist = try {
                    securityManager.encrypt(ByteArray(1), KeyType.NETWORK_KEY)
                    securityManager.encrypt(ByteArray(1), KeyType.APPLICATION_KEY)
                    true
                } catch (e: Exception) {
                    false
                }
                
                if (!keysExist) {
                    // 마스터 키 생성 필요
                    Log.d(TAG, "키 해시는 있으나 실제 키가 없음, 새 키 생성")
                    generateAndSaveKeys()
                } else {
                    Log.d(TAG, "기존 키 사용")
                }
            } else {
                // 새 키 생성
                Log.d(TAG, "저장된 키 해시 없음, 새 키 생성")
                generateAndSaveKeys()
            }
        } catch (e: Exception) {
            Log.e(TAG, "보안 초기화 중 오류 발생", e)
            // 오류 시 항상 새 키 생성
            generateAndSaveKeys()
        }
    }
    
    /**
     * 마스터 키 생성 및 저장
     */
    private fun generateAndSaveKeys() {
        try {
            // 마스터 키 생성
            val masterKey = ByteArray(32)  // 256비트
            secureRandom.nextBytes(masterKey)
            
            // 키 파생
            securityManager.deriveKeys(masterKey)
            
            // 키 해시 계산 및 저장
            val networkKeyHash = calculateKeyHash(KeyType.NETWORK_KEY)
            val appKeyHash = calculateKeyHash(KeyType.APPLICATION_KEY)
            
            if (networkKeyHash != null && appKeyHash != null) {
                sessionManager.saveKeyHashes(networkKeyHash, appKeyHash)
                Log.d(TAG, "새 키 생성 및 해시 저장 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "키 생성 및 저장 중 오류 발생", e)
        }
    }
    
    /**
     * 키 해시 계산
     */
    private fun calculateKeyHash(keyType: KeyType): String? {
        try {
            // 더미 데이터 암호화
            val dummyData = ByteArray(16)
            secureRandom.nextBytes(dummyData)
            val encrypted = securityManager.encrypt(dummyData, keyType)
            
            // 암호화된 데이터의 해시 계산
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(encrypted)
            
            // 해시를 16진수 문자열로 변환
            return hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "$keyType 해시 계산 중 오류 발생", e)
            return null
        }
    }
    
    /**
     * 메시지 수신 알림
     */
    private fun notifyMessage(msg: ChatMessage) {
        messageListeners.forEach { it.onMessageReceived(msg) }
    }
    
    /**
     * 네트워크 상태 알림
     */
    private fun notifyNetworkStatus(isConnected: Boolean) {
        isNetworkActive.set(isConnected)
        statusListeners.forEach { it.onNetworkStatusChanged(isConnected) }
    }
    
    /**
     * 프로비저닝 완료 알림
     */
    private fun notifyProvisioned(addr: Int) {
        // 프로비저닝된 주소 저장
        val provisionedNodes = sessionManager.getProvisionedNodes().toMutableList()
        if (!provisionedNodes.contains(addr)) {
            provisionedNodes.add(addr)
            sessionManager.saveProvisionedNodes(provisionedNodes)
        }
        provisionListeners.forEach { it.onProvisioningComplete(addr) }
    }
    
    /**
     * 프로비저닝 실패 알림
     */
    private fun notifyProvisioningFailed(device: BluetoothDevice, error: String) {
        provisionListeners.forEach { it.onProvisioningFailed(device, error) }
    }
    
    /**
     * 메시지 리스너 등록 (타입 변경)
     */
    fun addMessageListener(listener: MessageListener) = messageListeners.add(listener)
    
    /**
     * 메시지 리스너 제거 (타입 변경)
     */
    fun removeMessageListener(listener: MessageListener) = messageListeners.remove(listener)
    
    /**
     * 상태 리스너 등록 (타입 변경)
     */
    fun addStatusListener(listener: StatusListener) = statusListeners.add(listener)
    
    /**
     * 상태 리스너 제거 (타입 변경)
     */
    fun removeStatusListener(listener: StatusListener) = statusListeners.remove(listener)
    
    /**
     * 프로비저닝 리스너 등록 (타입 변경)
     */
    fun addProvisionListener(listener: ProvisioningListener) = provisionListeners.add(listener)
    
    /**
     * 프로비저닝 리스너 제거 (타입 변경)
     */
    fun removeProvisionListener(listener: ProvisioningListener) = provisionListeners.remove(listener)
    
    /**
     * 네트워크 상태 확인
     */
    fun checkNetworkStatus() {
        try {
            // 키 존재 여부 확인
            val networkKeyStatus = try {
                securityManager.encrypt(ByteArray(1), KeyType.NETWORK_KEY)
                true
            } catch (e: Exception) {
                false
            }
            
            val appKeyStatus = try {
                securityManager.encrypt(ByteArray(1), KeyType.APPLICATION_KEY)
                true
            } catch (e: Exception) {
                false
            }
            
            val isConnected = networkKeyStatus && appKeyStatus
            val previousState = isNetworkActive.getAndSet(isConnected)
            
            // 상태 변경 시에만 알림
            if (previousState != isConnected) {
                notifyNetworkStatus(isConnected)
            }
            
            // 갱신 필요 여부 확인
            if (isConnected) {
                if (securityManager.isKeyRefreshNeeded(KeyType.NETWORK_KEY) ||
                    securityManager.isKeyRefreshNeeded(KeyType.APPLICATION_KEY)) {
                    Log.d(TAG, "키 갱신 필요")
                    // 키 갱신 로직 호출
                    refreshSecurityKeys()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "네트워크 상태 확인 중 오류 발생", e)
            notifyNetworkStatus(false)
        }
    }
    
    /**
     * 보안 키 갱신
     */
    private fun refreshSecurityKeys() {
        try {
            // 마스터 키 생성
            val masterKey = ByteArray(32)
            secureRandom.nextBytes(masterKey)
            
            // 키 파생
            securityManager.deriveKeys(masterKey)
            
            // 키 해시 계산 및 저장
            val networkKeyHash = calculateKeyHash(KeyType.NETWORK_KEY)
            val appKeyHash = calculateKeyHash(KeyType.APPLICATION_KEY)
            
            if (networkKeyHash != null && appKeyHash != null) {
                sessionManager.saveKeyHashes(networkKeyHash, appKeyHash)
                Log.d(TAG, "보안 키 갱신 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "보안 키 갱신 중 오류 발생", e)
        }
    }

    /**
     * 브로드캐스트 메시지 전송
     */
    fun sendBroadcast(content: String, sender: String = ""): Boolean {
        return sendMessage(BROADCAST_ADDR, content, sender)
    }

    /**
     * 유니캐스트 메시지 전송
     */
    fun sendUnicast(targetAddr: Int, content: String, sender: String = ""): Boolean {
        return sendMessage(targetAddr, content, sender)
    }

    /**
     * 메시지 전송 구현
     */
    private fun sendMessage(dst: Int, content: String, sender: String): Boolean {
        try {
            // 네트워크 상태 확인
            if (!isNetworkActive.get()) {
                Log.e(TAG, "네트워크가 활성화되지 않음, 메시지 큐에 추가")
                queueMessageForLater(dst, content, sender)
                return false
            }
            
            // ChatMessage 객체 생성
            val msg = ChatMessage(localAddress, dst, System.currentTimeMillis(), content, sender)
            
            // JSON 변환
            val jsonBytes = JsonUtil.toBytes(msg) ?: throw IllegalStateException("메시지 직렬화 실패")
            
            // 보안 레이어를 통한 암호화
            val encryptedData = securityManager.encrypt(jsonBytes, KeyType.APPLICATION_KEY)
            
            // 네트워크 레이어를 통한 전송
            if (dst == BROADCAST_ADDR) {
                networkLayer.broadcast(MessageType.CHAT, encryptedData)
            } else {
                networkLayer.unicast(dst, MessageType.CHAT, encryptedData)
            }
            
            // 메시지를 대기 큐에도 추가 (안정성을 위해)
            coroutineScope.launch {
                sessionManager.addChatMessageToPending(msg, encryptedData)
            }
            
            // UI 알림
            notifyMessage(msg)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 중 오류 발생", e)
            queueMessageForLater(dst, content, sender)
            return false
        }
    }
    
    /**
     * 나중을 위해 메시지 큐에 추가
     */
    private fun queueMessageForLater(dst: Int, content: String, sender: String) {
        coroutineScope.launch {
            try {
                // ChatMessage 객체 생성
                val msg = ChatMessage(localAddress, dst, System.currentTimeMillis(), content, sender)
                
                // JSON 변환
                val jsonBytes = JsonUtil.toBytes(msg) ?: return@launch
                
                // 가능하면 암호화
                try {
                    val encryptedData = securityManager.encrypt(jsonBytes, KeyType.APPLICATION_KEY)
                    sessionManager.addChatMessageToPending(msg, encryptedData)
                    Log.d(TAG, "암호화된 메시지를 대기 큐에 추가: dst=$dst")
                } catch (e: Exception) {
                    // 암호화 실패 시 원본 저장
                    sessionManager.addPendingMessage(MessageType.CHAT, dst, jsonBytes)
                    Log.d(TAG, "원본 메시지를 대기 큐에 추가: dst=$dst")
                }
            } catch (e: Exception) {
                Log.e(TAG, "메시지 큐 추가 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 프로비저닝 시작
     */
    fun startProvisioning() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "미프로비저닝 노드 검색 시작")
                
                // 노드 검색
                val nodes = provisioningManager.discoverNodes()
                Log.d(TAG, "${nodes.size}개의 노드 발견")
                
                if (nodes.isEmpty()) {
                    Log.d(TAG, "프로비저닝할 노드를 찾지 못했습니다.")
                    return@launch
                }
                
                // 각 노드에 대해 프로비저닝 시도
                nodes.forEach { node ->
                    val result = provisioningManager.provision(node.device)
                    if (result.isSuccessful && result.unicastAddress != null) {
                        Log.d(TAG, "노드 프로비저닝 성공: ${node.device.address}, 주소: ${result.unicastAddress}")
                        notifyProvisioned(result.unicastAddress)
                        
                        // 세션 저장
                        saveDeviceSession(node.device)
                    } else {
                        Log.e(TAG, "노드 프로비저닝 실패: ${node.device.address}, 이유: ${result.errorMessage}")
                        notifyProvisioningFailed(node.device, result.errorMessage ?: "Unknown error")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "프로비저닝 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 로컬 유니캐스트 주소 설정
     */
    fun setLocalUnicastAddress(address: Int) {
        if (address > 0) {
            localAddress = address
            networkLayer.setLocalAddress(address)
            sessionManager.saveLocalAddress(address)
            Log.d(TAG, "로컬 주소 변경: $address")
        }
    }
    
    /**
     * 로컬 유니캐스트 주소 반환
     */
    fun getLocalUnicastAddress(): Int {
        return localAddress
    }
    
    /**
     * 프로비저닝된 노드 목록 반환
     */
    fun getProvisionedNodes(): List<Int> {
        return sessionManager.getProvisionedNodes()
    }
    
    /**
     * 메시 네트워크 정보 반환
     */
    fun getMeshNetwork(): MeshNetwork? {
        try {
            // 네트워크 키와 앱 키가 있는지 확인
            val hasNetworkKey = try {
                securityManager.encrypt(ByteArray(1), KeyType.NETWORK_KEY)
                true
            } catch (e: Exception) {
                false
            }
            
            val hasAppKey = try {
                securityManager.encrypt(ByteArray(1), KeyType.APPLICATION_KEY)
                true
            } catch (e: Exception) {
                false
            }
            
            // 키 이름(UUID)을 생성 - 실제로는 랜덤한 UUID이지만
            // 단순화를 위해 고정 UUID 반환
            val networkKey = if (hasNetworkKey) 
                UUID.fromString("8b3f4c20-adba-4b51-9142-5f825e34c413") else null
            val appKey = if (hasAppKey) 
                UUID.fromString("2dd69f80-45a0-4b6d-8f50-a786dc3e8e5c") else null
            
            // 네트워크 객체 생성
            val network = MeshNetwork(
                netKeys = networkKey?.let { listOf(it) } ?: emptyList(),
                appKeys = appKey?.let { listOf(it) } ?: emptyList(),
                localAddress = localAddress,
                provisionedNodes = sessionManager.getProvisionedNodes()
            )
            
            return network
        } catch (e: Exception) {
            Log.e(TAG, "메시 네트워크 정보 조회 중 오류 발생", e)
            return null
        }
    }
    
    /**
     * 모든 대기 메시지 지금 재전송
     */
    fun resendPendingMessages() {
        if (isNetworkActive.get()) {
            sessionManager.resendAllPendingMessages()
        } else {
            Log.d(TAG, "네트워크 비활성 상태, 재전송 보류")
        }
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        networkLayer.release()
        provisioningManager.release()
        connectionManager.release()
        
        // 리스너 해제
        connectionManager.removeConnectionStateListener(connectionStateListener)
        messageListeners.clear()
        statusListeners.clear()
        provisionListeners.clear()
        
        Log.d(TAG, "메쉬 서비스 리소스 해제 완료")
    }
}
