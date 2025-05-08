package com.ssafy.lantern.service.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ssafy.lantern.data.model.ChatMessage
import com.ssafy.lantern.data.model.MessageType
import com.ssafy.lantern.utils.JsonUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 상태 및 메시지 큐 관리 클래스
 * 연결 끊김 시 세션 정보 저장 및 복구, 메시지 재전송 큐 관리
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SessionManager"
        
        // SharedPreferences 키
        private const val PREFS_NAME = "mesh_session_prefs"
        private const val KEY_LOCAL_ADDRESS = "local_address"
        private const val KEY_NETWORK_KEY_HASH = "network_key_hash"
        private const val KEY_APP_KEY_HASH = "app_key_hash"
        private const val KEY_PROVISIONED_NODES = "provisioned_nodes"
        private const val KEY_PENDING_MESSAGES = "pending_messages"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        // 최대 저장 메시지 수
        private const val MAX_PENDING_MESSAGES = 50
    }
    
    // SharedPreferences 인스턴스
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // 메시지 큐 (전송 대기 중인 메시지)
    private val pendingMessages = ConcurrentLinkedQueue<PendingMessage>()
    
    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 뮤텍스 (동시 액세스 제어)
    private val mutex = Mutex()
    
    // 메시지 재전송 콜백
    private var messageResendCallback: ((MessageType, Int, ByteArray) -> Unit)? = null
    
    init {
        // 저장된 메시지 로드
        loadPendingMessages()
    }
    
    /**
     * 로컬 주소 저장
     */
    fun saveLocalAddress(address: Int) {
        sharedPreferences.edit().putInt(KEY_LOCAL_ADDRESS, address).apply()
        Log.d(TAG, "로컬 주소 저장: $address")
    }
    
    /**
     * 로컬 주소 조회
     */
    fun getLocalAddress(): Int {
        return sharedPreferences.getInt(KEY_LOCAL_ADDRESS, 0)
    }
    
    /**
     * 키 해시 저장 (보안 상 실제 키 값은 저장하지 않고 해시만 저장)
     */
    fun saveKeyHashes(networkKeyHash: String, appKeyHash: String) {
        sharedPreferences.edit()
            .putString(KEY_NETWORK_KEY_HASH, networkKeyHash)
            .putString(KEY_APP_KEY_HASH, appKeyHash)
            .apply()
        Log.d(TAG, "키 해시 저장 완료")
    }
    
    /**
     * 키 해시 조회
     */
    fun getKeyHashes(): Pair<String?, String?> {
        val networkKeyHash = sharedPreferences.getString(KEY_NETWORK_KEY_HASH, null)
        val appKeyHash = sharedPreferences.getString(KEY_APP_KEY_HASH, null)
        return Pair(networkKeyHash, appKeyHash)
    }
    
    /**
     * 프로비저닝된 노드 저장
     */
    fun saveProvisionedNodes(nodeAddresses: List<Int>) {
        val addressesJson = JsonUtil.toJson(nodeAddresses)
        sharedPreferences.edit().putString(KEY_PROVISIONED_NODES, addressesJson).apply()
        Log.d(TAG, "${nodeAddresses.size}개 노드 주소 저장 완료")
    }
    
    /**
     * 프로비저닝된 노드 조회
     */
    fun getProvisionedNodes(): List<Int> {
        val addressesJson = sharedPreferences.getString(KEY_PROVISIONED_NODES, null) ?: return emptyList()
        return JsonUtil.fromJsonGeneric<List<Int>>(addressesJson) ?: emptyList()
    }
    
    /**
     * 마지막 동기화 시간 저장
     */
    fun saveLastSyncTime() {
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit().putLong(KEY_LAST_SYNC_TIME, currentTime).apply()
    }
    
    /**
     * 마지막 동기화 시간 조회
     */
    fun getLastSyncTime(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0)
    }
    
    /**
     * 메시지 재전송 콜백 설정
     */
    fun setMessageResendCallback(callback: (MessageType, Int, ByteArray) -> Unit) {
        messageResendCallback = callback
    }
    
    /**
     * 전송 대기 메시지 추가
     */
    suspend fun addPendingMessage(
        messageType: MessageType,
        destination: Int,
        payload: ByteArray,
        expiryTime: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 기본 1일 만료
    ): Boolean = mutex.withLock {
        // 큐 크기 제한
        if (pendingMessages.size >= MAX_PENDING_MESSAGES) {
            // 가장 오래된 메시지 제거
            pendingMessages.poll()
        }
        
        val pendingMessage = PendingMessage(
            id = System.currentTimeMillis(),
            messageType = messageType,
            destination = destination,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            retryCount = 0,
            expiryTime = expiryTime
        )
        
        val added = pendingMessages.offer(pendingMessage)
        if (added) {
            // 메시지 추가 성공 시 영구 저장소에도 저장
            savePendingMessagesToStorage()
            Log.d(TAG, "전송 대기 메시지 추가: type=${messageType}, dst=$destination")
        }
        
        return@withLock added
    }
    
    /**
     * 전송 대기 메시지 제거
     */
    suspend fun removePendingMessage(messageId: Long): Boolean = mutex.withLock {
        val removed = pendingMessages.removeIf { it.id == messageId }
        if (removed) {
            savePendingMessagesToStorage()
            Log.d(TAG, "전송 대기 메시지 제거: id=$messageId")
        }
        return@withLock removed
    }
    
    /**
     * 모든 전송 대기 메시지 재전송 시도
     */
    fun resendAllPendingMessages() {
        coroutineScope.launch {
            mutex.withLock {
                val currentTime = System.currentTimeMillis()
                val expiredMessages = mutableListOf<Long>()
                
                pendingMessages.forEach { message ->
                    // 만료된 메시지 확인
                    if (message.expiryTime < currentTime) {
                        expiredMessages.add(message.id)
                        return@forEach
                    }
                    
                    // 재전송 콜백 호출
                    messageResendCallback?.invoke(
                        message.messageType,
                        message.destination,
                        message.payload
                    )
                    
                    // 재시도 횟수 증가
                    message.retryCount++
                    Log.d(TAG, "메시지 재전송: id=${message.id}, type=${message.messageType}, dst=${message.destination}, 시도=${message.retryCount}")
                }
                
                // 만료된 메시지 제거
                expiredMessages.forEach { messageId ->
                    pendingMessages.removeIf { it.id == messageId }
                    Log.d(TAG, "만료된 메시지 제거: id=$messageId")
                }
                
                // 변경사항 저장
                if (expiredMessages.isNotEmpty()) {
                    savePendingMessagesToStorage()
                }
            }
        }
    }
    
    /**
     * 영구 저장소에 대기 메시지 저장
     */
    private fun savePendingMessagesToStorage() {
        try {
            val pendingMessageList = pendingMessages.toList()
            val json = JsonUtil.toJson(pendingMessageList)
            json?.let {
                sharedPreferences.edit().putString(KEY_PENDING_MESSAGES, it).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "대기 메시지 저장 중 오류 발생", e)
        }
    }
    
    /**
     * 영구 저장소에서 대기 메시지 로드
     */
    private fun loadPendingMessages() {
        try {
            val json = sharedPreferences.getString(KEY_PENDING_MESSAGES, null) ?: return
            // 명시적으로 타입을 지정하여 fromJsonGeneric 호출
            val messageList: List<PendingMessage>? = JsonUtil.fromJsonGeneric<List<PendingMessage>>(json)
            
            if (messageList == null) {
                Log.e(TAG, "저장된 대기 메시지 로드 실패: JSON 파싱 오류 또는 null 반환")
                return
            }
            
            // 현재 시간 확인
            val currentTime = System.currentTimeMillis()
            
            // 만료되지 않은 메시지만 큐에 추가
            messageList.forEach { message -> // 이제 messageList의 타입이 명확하므로 forEach 사용 가능
                if (message.expiryTime > currentTime) {
                    pendingMessages.offer(message)
                }
            }
            
            Log.d(TAG, "${pendingMessages.size}개의 대기 메시지 로드 완료")
        } catch (e: Exception) {
            Log.e(TAG, "대기 메시지 로드 중 오류 발생", e)
        }
    }
    
    /**
     * 세션 정보 초기화 (로그아웃 등에 사용)
     */
    fun clearSessionData() {
        coroutineScope.launch {
            mutex.withLock {
                // 모든 메시지 큐 초기화
                pendingMessages.clear()
                
                // SharedPreferences 초기화
                sharedPreferences.edit().clear().apply()
                
                Log.d(TAG, "세션 정보 초기화 완료")
            }
        }
    }
    
    /**
     * 채팅 메시지를 대기 메시지로 변환하여 추가
     */
    suspend fun addChatMessageToPending(message: ChatMessage, payload: ByteArray): Boolean {
        return addPendingMessage(
            messageType = MessageType.CHAT,
            destination = message.toAddress, // message.dst 대신 message.toAddress 사용
            payload = payload
        )
    }
    
    /**
     * 전송 대기 메시지 데이터 클래스
     */
    data class PendingMessage(
        val id: Long,                  // 메시지 고유 ID
        val messageType: MessageType,  // 메시지 타입
        val destination: Int,          // 목적지 주소
        val payload: ByteArray,        // 메시지 페이로드
        val timestamp: Long,           // 생성 시간
        var retryCount: Int,           // 재시도 횟수
        val expiryTime: Long           // 만료 시간
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PendingMessage

            if (id != other.id) return false
            if (messageType != other.messageType) return false
            if (destination != other.destination) return false
            if (!payload.contentEquals(other.payload)) return false
            if (timestamp != other.timestamp) return false
            if (retryCount != other.retryCount) return false
            if (expiryTime != other.expiryTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + messageType.hashCode()
            result = 31 * result + destination
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + retryCount
            result = 31 * result + expiryTime.hashCode()
            return result
        }
    }
} 