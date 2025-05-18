package com.ssafy.lanterns.service.ble

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * BLE 메시지 관리를 위한 공통 클래스
 * - 메시지 분할/조립 담당
 * - 메시지 큐 관리
 * - 재시도 및 오류 처리
 */
object BleMessageManager {
    private const val TAG = "BleMessageManager"
    
    // 메시지 분할 관련 상수
    private const val MAX_PAYLOAD_SIZE = 17 // BLE 패킷 최대 크기 제한
    
    // 재전송 관련 상수
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 1000L
    
    // 메시지 만료 시간 (5분)
    private const val MESSAGE_EXPIRATION_MS = 5 * 60 * 1000L
    
    // 메시지 타입 정의
    const val MESSAGE_TYPE_CHAT = 0 // 일반 채팅 메시지
    const val MESSAGE_TYPE_ACK = 1 // 확인 응답 메시지
    
    // 메시지 상태
    private const val STATUS_PENDING = 0
    private const val STATUS_SENDING = 1
    private const val STATUS_SENT = 2
    private const val STATUS_DELIVERED = 3
    private const val STATUS_FAILED = 4
    
    // 발신 메시지 큐
    private val outgoingMessageQueue = ConcurrentLinkedQueue<BleMessage>()
    
    // 수신 메시지 조각 맵 (메시지ID -> 부분 메시지 목록)
    private val incomingMessageParts = ConcurrentHashMap<String, MutableMap<Int, BleMessagePart>>()
    
    // 최근 수신한 메시지 ID 세트 (중복 수신 방지)
    private val recentlyReceivedMessageIds = ConcurrentHashMap<String, Long>()
    
    // 전송 중인 메시지 맵 (메시지ID -> 메시지)
    private val pendingMessages = ConcurrentHashMap<String, BleMessage>()
    
    // 스케줄러
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    init {
        // 정기적인 정리 작업 스케줄링
        scheduler.scheduleAtFixedRate({
            cleanupExpiredMessages()
        }, 30, 30, TimeUnit.SECONDS)
    }
    
    /**
     * BLE 메시지 데이터 클래스
     * @param id 메시지 고유 ID
     * @param sender 발신자 ID
     * @param content 메시지 내용
     * @param timestamp 타임스탬프
     * @param type 메시지 타입 (일반, ACK 등)
     * @param sequenceNumber 메시지 시퀀스 번호 (순서 보장)
     * @param retryCount 재시도 횟수
     * @param status 메시지 상태
     */
    data class BleMessage(
        val id: String = UUID.randomUUID().toString().substring(0, 8),
        val sender: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val type: Int = MESSAGE_TYPE_CHAT,
        val sequenceNumber: Int = 0,
        var retryCount: Int = 0,
        var status: Int = STATUS_PENDING
    )
    
    /**
     * BLE 메시지 분할 조각 클래스
     * @param messageId 원본 메시지 ID
     * @param partIndex 분할 순서 인덱스
     * @param totalParts 총 분할 수
     * @param data 데이터 내용
     * @param timestamp 수신 시간
     */
    data class BleMessagePart(
        val messageId: String,
        val partIndex: Int,
        val totalParts: Int,
        val data: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 메시지 전송을 위한 분할 처리
     * @param message 전송할 메시지
     * @return 분할된 메시지 파트 리스트
     */
    fun splitMessage(message: BleMessage): List<Map<String, String>> {
        val content = message.content
        val parts = mutableListOf<Map<String, String>>()
        
        // 메시지 너무 작아 분할 필요 없는 경우
        if (content.toByteArray().size <= MAX_PAYLOAD_SIZE) {
            val part = mapOf(
                "id" to message.id,
                "s" to message.sender,
                "c" to content,
                "t" to message.timestamp.toString(),
                "p" to "0",
                "tp" to "0",
                "seq" to message.sequenceNumber.toString(),
                "type" to message.type.toString()
            )
            parts.add(part)
            return parts
        }
        
        // 메시지 분할 필요한 경우
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        var currentIndex = 0
        var partIndex = 0
        
        while (currentIndex < contentBytes.size) {
            val endIndex = minOf(currentIndex + MAX_PAYLOAD_SIZE, contentBytes.size)
            val partContent = contentBytes.copyOfRange(currentIndex, endIndex).toString(Charsets.UTF_8)
            
            val totalParts = (contentBytes.size + MAX_PAYLOAD_SIZE - 1) / MAX_PAYLOAD_SIZE
            
            val part = mapOf(
                "id" to message.id,
                "s" to message.sender,
                "c" to partContent,
                "t" to message.timestamp.toString(),
                "p" to partIndex.toString(),
                "tp" to totalParts.toString(),
                "seq" to message.sequenceNumber.toString(),
                "type" to message.type.toString()
            )
            
            parts.add(part)
            currentIndex = endIndex
            partIndex++
        }
        
        Log.d(TAG, "메시지 분할: ID=${message.id}, 총 ${parts.size}개 파트")
        return parts
    }
    
    /**
     * 수신한 메시지 파트 처리 및 조립
     * @param rawDataMap 수신한 메시지 파트 데이터
     * @return 완전히 조립된 메시지 반환 (미완성이면 null)
     */
    fun processIncomingMessagePart(rawDataMap: Map<String, String>): BleMessage? {
        try {
            // 필수 필드 추출
            val messageId = rawDataMap["id"] ?: return null
            val sender = rawDataMap["s"] ?: return null
            val content = rawDataMap["c"] ?: ""
            val timestamp = rawDataMap["t"]?.toLongOrNull() ?: System.currentTimeMillis()
            val partIndex = rawDataMap["p"]?.toIntOrNull() ?: 0
            val totalParts = rawDataMap["tp"]?.toIntOrNull() ?: 0
            val sequenceNumber = rawDataMap["seq"]?.toIntOrNull() ?: 0
            val type = rawDataMap["type"]?.toIntOrNull() ?: MESSAGE_TYPE_CHAT
            
            // 이미 수신했던 메시지인지 확인
            if (isMessageAlreadyReceived(messageId)) {
                Log.d(TAG, "이미 수신한 메시지: $messageId")
                return null
            }
            
            // 단일 파트 메시지인 경우 (분할 없음)
            if (totalParts == 0 || (partIndex == 0 && totalParts == 1)) {
                markMessageAsReceived(messageId)
                return BleMessage(
                    id = messageId,
                    sender = sender,
                    content = content,
                    timestamp = timestamp,
                    sequenceNumber = sequenceNumber,
                    type = type,
                    status = STATUS_DELIVERED
                )
            }
            
            // 분할된 메시지 처리
            val messagePart = BleMessagePart(
                messageId = messageId,
                partIndex = partIndex,
                totalParts = totalParts,
                data = content,
                timestamp = timestamp
            )
            
            // 메시지 파트 저장
            val parts = incomingMessageParts.getOrPut(messageId) { mutableMapOf() }
            parts[partIndex] = messagePart
            
            // 모든 파트가 수신되었는지 확인
            if (parts.size == totalParts) {
                // 모든 파트가 수신되었으면 메시지 조립
                val fullContent = buildString {
                    for (i in 0 until totalParts) {
                        parts[i]?.let { append(it.data) }
                    }
                }
                
                // 메시지 조각 맵에서 제거
                incomingMessageParts.remove(messageId)
                
                // 수신 완료 표시
                markMessageAsReceived(messageId)
                
                return BleMessage(
                    id = messageId,
                    sender = sender,
                    content = fullContent,
                    timestamp = timestamp,
                    sequenceNumber = sequenceNumber,
                    type = type,
                    status = STATUS_DELIVERED
                )
            }
            
            // 아직 모든 파트가 수신되지 않음
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "메시지 파트 처리 중 오류: ${e.message}")
            return null
        }
    }
    
    /**
     * 메시지 큐에 추가
     * @param message 전송할 메시지
     */
    fun enqueueMessage(message: BleMessage) {
        outgoingMessageQueue.add(message)
        pendingMessages[message.id] = message
        Log.d(TAG, "메시지 큐에 추가: ${message.id}, 큐 크기: ${outgoingMessageQueue.size}")
    }
    
    /**
     * 다음 전송할 메시지 가져오기
     * @return 전송할 메시지 또는 없으면 null
     */
    fun getNextMessageToSend(): BleMessage? {
        return outgoingMessageQueue.poll()
    }
    
    /**
     * 메시지 전송 성공 처리
     * @param messageId 전송 성공한 메시지 ID
     */
    fun markMessageAsSent(messageId: String) {
        pendingMessages[messageId]?.let { message ->
            message.status = STATUS_SENT
            Log.d(TAG, "메시지 전송 성공: $messageId")
        }
    }
    
    /**
     * 메시지 전송 실패 처리 및 재시도
     * @param messageId 실패한 메시지 ID
     * @return 재시도 가능 여부
     */
    fun handleMessageFailure(messageId: String): Boolean {
        pendingMessages[messageId]?.let { message ->
            if (message.retryCount < MAX_RETRY_COUNT) {
                message.retryCount++
                message.status = STATUS_PENDING
                
                // 재시도 큐에 다시 추가
                outgoingMessageQueue.add(message)
                
                Log.d(TAG, "메시지 재전송 예약: $messageId (시도 ${message.retryCount}/$MAX_RETRY_COUNT)")
                return true
            } else {
                message.status = STATUS_FAILED
                pendingMessages.remove(messageId)
                Log.e(TAG, "메시지 전송 최종 실패: $messageId (최대 재시도 횟수 초과)")
                return false
            }
        }
        return false
    }
    
    /**
     * 이미 수신한 메시지인지 확인
     * @param messageId 확인할 메시지 ID
     * @return 이미 수신했으면 true
     */
    private fun isMessageAlreadyReceived(messageId: String): Boolean {
        return recentlyReceivedMessageIds.containsKey(messageId)
    }
    
    /**
     * 메시지 수신 완료 표시
     * @param messageId 수신한 메시지 ID
     */
    private fun markMessageAsReceived(messageId: String) {
        recentlyReceivedMessageIds[messageId] = System.currentTimeMillis()
    }
    
    /**
     * 만료된 메시지 정리
     */
    private fun cleanupExpiredMessages() {
        val currentTime = System.currentTimeMillis()
        
        // 오래된 수신 메시지 ID 제거
        val expiredIds = recentlyReceivedMessageIds.entries
            .filter { currentTime - it.value > MESSAGE_EXPIRATION_MS }
            .map { it.key }
        
        expiredIds.forEach { recentlyReceivedMessageIds.remove(it) }
        
        // 오래된 메시지 조각 제거
        val expiredPartIds = incomingMessageParts.entries
            .filter { entry ->
                entry.value.values.any { part ->
                    currentTime - part.timestamp > MESSAGE_EXPIRATION_MS
                }
            }
            .map { it.key }
        
        expiredPartIds.forEach { incomingMessageParts.remove(it) }
        
        // 오래된 대기 메시지 제거
        val expiredPendingIds = pendingMessages.entries
            .filter { currentTime - it.value.timestamp > MESSAGE_EXPIRATION_MS }
            .map { it.key }
        
        expiredPendingIds.forEach { pendingMessages.remove(it) }
        
        if (expiredIds.isNotEmpty() || expiredPartIds.isNotEmpty() || expiredPendingIds.isNotEmpty()) {
            Log.d(TAG, "만료된 메시지 정리: ${expiredIds.size}개 수신 ID, ${expiredPartIds.size}개 조각, ${expiredPendingIds.size}개 대기")
        }
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        scheduler.shutdown()
        outgoingMessageQueue.clear()
        incomingMessageParts.clear()
        recentlyReceivedMessageIds.clear()
        pendingMessages.clear()
    }
} 