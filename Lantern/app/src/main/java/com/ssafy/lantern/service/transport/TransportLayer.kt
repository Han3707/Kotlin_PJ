package com.ssafy.lantern.service.transport

import android.util.Log
import com.ssafy.lantern.data.model.TransportHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 전송 레이어 구현
 * BLE 패킷의 크기 제한(20바이트)을 초과하는 데이터를 분할하고 재조립하는 역할
 */
@Singleton
class TransportLayer @Inject constructor() {
    companion object {
        private const val TAG = "TransportLayer"
        
        // BLE PDU 최대 크기 (바이트) - 헤더 제외
        // 더 작은 크기로 조정 (8바이트 광고 데이터 허용에 맞춤)
        private const val MAX_PAYLOAD_SIZE = 2  // 헤더(4바이트) 제외하고 2바이트만 사용
        
        // 재조립 타임아웃 (밀리초)
        private const val REASSEMBLY_TIMEOUT = 60_000L  // 60초로 늘림
        
        // 재전송 타임아웃 (밀리초)
        private const val RETRANSMIT_TIMEOUT = 10_000L  // 10초로 늘림
        
        // 최대 재전송 횟수
        private const val MAX_RETRANSMIT_COUNT = 3
    }
    
    // 시퀀스 번호 생성기 (Long -> Int로 변경)
    private val sequenceGenerator = AtomicInteger(0)
    
    // 재조립 중인 메시지 저장소: Map<시퀀스번호, Map<세그먼트인덱스, 데이터>>
    private val reassemblyStore = ConcurrentHashMap<Int, MutableMap<Int, ByteArray>>()
    
    // 타임아웃 작업 저장소: Map<시퀀스번호, Job>
    private val timeoutJobs = ConcurrentHashMap<Int, Job>()
    
    // 재전송 큐: Map<시퀀스번호, Map<세그먼트인덱스, 재전송 정보>>
    private val retransmitQueue = ConcurrentHashMap<Int, MutableMap<Int, RetransmitInfo>>()
    
    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // 뮤텍스 (동시 접근 제어)
    private val mutex = Mutex()
    
    // 재조립 완료 콜백 저장소: Map<시퀀스번호, 콜백>
    private val completionCallbacks = ConcurrentHashMap<Int, (ByteArray) -> Unit>()
    
    /**
     * 재전송 정보 클래스
     */
    private inner class RetransmitInfo(
        val data: ByteArray,
        var retryCount: Int = 0,
        var job: Job? = null
    )
    
    /**
     * 페이로드를 분할하여 여러 세그먼트로 만듦
     * 
     * @param payload 분할할 원본 페이로드
     * @return 세그먼트 목록 (헤더 포함)
     */
    fun segment(payload: ByteArray): List<ByteArray> {
        if (payload.isEmpty()) {
            Log.w(TAG, "세그먼트화할 빈 페이로드")
            return emptyList()
        }
        
        // 작은 페이로드는 분할 없이 바로 반환
        if (payload.size <= MAX_PAYLOAD_SIZE) {
            val seqNum = sequenceGenerator.incrementAndGet()
            val header = TransportHeader(seqNum, 0, 1)
            val headerBytes = header.toBytes()
            
            // 헤더 + 페이로드
            val result = ByteArray(headerBytes.size + payload.size)
            headerBytes.copyInto(result)
            payload.copyInto(result, headerBytes.size)
            
            Log.d(TAG, "작은 페이로드 세그먼트화: seqNum=$seqNum, 크기=${result.size}바이트")
            return listOf(result)
        }
        
        // 큰 페이로드 분할
        val seqNum = sequenceGenerator.incrementAndGet()
        val segments = mutableListOf<ByteArray>()
        
        // 세그먼트 수 계산
        val totalSegments = (payload.size + MAX_PAYLOAD_SIZE - 1) / MAX_PAYLOAD_SIZE
        Log.d(TAG, "큰 페이로드 세그먼트화: seqNum=$seqNum, 크기=${payload.size}바이트, 세그먼트 수=$totalSegments")
        
        for (i in 0 until totalSegments) {
            // 현재 세그먼트의 페이로드 계산
            val startIndex = i * MAX_PAYLOAD_SIZE
            val endIndex = (startIndex + MAX_PAYLOAD_SIZE).coerceAtMost(payload.size)
            val segmentPayloadSize = endIndex - startIndex
            
            // 헤더 생성
            val header = TransportHeader(seqNum, i, totalSegments)
            val headerBytes = header.toBytes()
            
            // 헤더 + 세그먼트 페이로드
            val segment = ByteArray(headerBytes.size + segmentPayloadSize)
            headerBytes.copyInto(segment)
            payload.copyInto(segment, headerBytes.size, startIndex, endIndex)
            
            segments.add(segment)
            Log.d(TAG, "세그먼트 생성: seqNum=$seqNum, 인덱스=$i/$totalSegments, 크기=${segment.size}바이트")
        }
        
        return segments
    }
    
    /**
     * 세그먼트를 재조립하여 원본 페이로드로 복원
     * 
     * @param fragment 수신된 세그먼트 (헤더 포함)
     * @param onComplete 재조립 완료 시 호출될 콜백
     * @return 재조립된 페이로드 (재조립 중이면 null 반환)
     */
    suspend fun processFragment(fragment: ByteArray, onComplete: (ByteArray) -> Unit): ByteArray? {
        if (fragment.size <= TransportHeader.HEADER_SIZE) {
            Log.e(TAG, "유효하지 않은 세그먼트: 크기가 너무 작음 (${fragment.size}바이트)")
            return null
        }
        
        // 헤더 파싱
        val header = TransportHeader.fromBytes(fragment) ?: run {
            Log.e(TAG, "헤더 파싱 실패")
            return null
        }
        
        Log.d(TAG, "세그먼트 수신: seqNum=${header.seqNum}, 인덱스=${header.segmentIndex}/${header.totalSegments}")
        
        // 단일 세그먼트인 경우 바로 페이로드 반환
        if (header.totalSegments == 1 && header.segmentIndex == 0) {
            val payload = fragment.copyOfRange(TransportHeader.HEADER_SIZE, fragment.size)
            Log.d(TAG, "단일 세그먼트 페이로드 반환: ${payload.size}바이트")
            return payload
        }
        
        // 세그먼트 저장 및 재조립 시도
        return mutex.withLock {
            // 완료 콜백 등록
            completionCallbacks.putIfAbsent(header.seqNum, onComplete)
            
            // 세그먼트 저장
            val segments = reassemblyStore.getOrPut(header.seqNum) {
                // 새 시퀀스 번호의 첫 세그먼트인 경우 타임아웃 작업 등록
                scheduleReassemblyTimeout(header.seqNum)
                Collections.synchronizedMap(mutableMapOf())
            }
            
            // 페이로드 추출 및 저장
            val payload = fragment.copyOfRange(TransportHeader.HEADER_SIZE, fragment.size)
            segments[header.segmentIndex] = payload
            
            // 모든 세그먼트가 수신되었는지 확인
            if (segments.size == header.totalSegments) {
                // 재조립 완료
                cancelTimeoutJob(header.seqNum)
                
                // 세그먼트를 순서대로 연결
                val reassembledData = reassembleSegments(segments, header.totalSegments)
                
                // 저장소에서 해당 시퀀스 제거
                reassemblyStore.remove(header.seqNum)
                val callback = completionCallbacks.remove(header.seqNum)
                
                Log.d(TAG, "페이로드 재조립 완료: seqNum=${header.seqNum}, 크기=${reassembledData.size}바이트")
                
                // 콜백 호출
                callback?.invoke(reassembledData)
                
                return@withLock reassembledData
            }
            
            // 아직 재조립 중
            Log.d(TAG, "페이로드 재조립 중: ${segments.size}/${header.totalSegments} 세그먼트 수신됨")
            return@withLock null
        }
    }
    
    /**
     * 재조립 타임아웃 설정
     */
    private fun scheduleReassemblyTimeout(seqNum: Int) {
        val job = coroutineScope.launch {
            delay(REASSEMBLY_TIMEOUT)
            
            mutex.withLock {
                Log.w(TAG, "시퀀스 $seqNum 재조립 타임아웃")
                
                // 저장소에서 해당 시퀀스 제거
                reassemblyStore.remove(seqNum)
                completionCallbacks.remove(seqNum)
                timeoutJobs.remove(seqNum)
            }
        }
        
        timeoutJobs[seqNum] = job
    }
    
    /**
     * 타임아웃 작업 취소
     */
    private fun cancelTimeoutJob(seqNum: Int) {
        timeoutJobs.remove(seqNum)?.cancel("재조립 완료")
    }
    
    /**
     * 세그먼트를 순서대로 연결하여 원본 페이로드 복원
     */
    private fun reassembleSegments(segments: Map<Int, ByteArray>, totalSegments: Int): ByteArray {
        // 전체 크기 계산
        var totalSize = 0
        for (i in 0 until totalSegments) {
            val segment = segments[i] ?: continue
            totalSize += segment.size
        }
        
        // 순서대로 연결
        val result = ByteArray(totalSize)
        var offset = 0
        
        for (i in 0 until totalSegments) {
            val segment = segments[i] ?: continue
            segment.copyInto(result, offset)
            offset += segment.size
        }
        
        return result
    }
    
    /**
     * 재전송 큐에 세그먼트 추가
     * 
     * @param data 재전송할 세그먼트 데이터 (헤더 포함)
     * @param onRetransmit 재전송 시 호출될 콜백
     */
    fun addToRetransmitQueue(data: ByteArray, onRetransmit: (ByteArray) -> Unit) {
        val header = TransportHeader.fromBytes(data) ?: run {
            Log.e(TAG, "재전송 큐에 추가 실패: 유효하지 않은 헤더")
            return
        }
        
        // 재전송 작업이 너무 많으면 가장 오래된 작업 취소 (선택적)
        if (retransmitQueue.size > 50) {
            Log.w(TAG, "재전송 큐 크기가 너무 큽니다. 일부 작업을 취소합니다.")
            val oldestSeqNum = retransmitQueue.keys.minOrNull()
            oldestSeqNum?.let {
                val segmentMap = retransmitQueue.remove(it)
                segmentMap?.values?.forEach { info -> info.job?.cancel() }
            }
        }
        
        // 이미 있는 항목이면 재전송 횟수만 초기화
        val segmentMap = retransmitQueue.getOrPut(header.seqNum) {
            Collections.synchronizedMap(mutableMapOf())
        }
        
        // 재전송 정보 생성 또는 업데이트
        val retransmitInfo = segmentMap.getOrPut(header.segmentIndex) {
            RetransmitInfo(data)
        }
        
        // 기존 작업 취소
        retransmitInfo.job?.cancel()
        
        // 새 재전송 작업 시작 - 각 세그먼트에 지수 백오프 적용
        retransmitInfo.job = coroutineScope.launch {
            val baseDelay = RETRANSMIT_TIMEOUT
            val currentRetry = retransmitInfo.retryCount
            
            // 현재 재시도 횟수에 따라 지수적으로 지연 시간 증가
            val delayMs = baseDelay * (1 shl (currentRetry.coerceAtMost(3)))
            
            delay(delayMs)
            
            if (retransmitInfo.retryCount < MAX_RETRANSMIT_COUNT) {
                // 재시도 횟수 증가
                retransmitInfo.retryCount++
                
                // 로그에 지연 시간 및 재시도 횟수 표시
                Log.d(TAG, "세그먼트 재전송: seqNum=${header.seqNum}, 인덱스=${header.segmentIndex}, 시도=${retransmitInfo.retryCount}/$MAX_RETRANSMIT_COUNT, 지연=${delayMs}ms")
                
                // 재전송 실행
                onRetransmit(data)
                
                // 다음 재전송 예약
                addToRetransmitQueue(data, onRetransmit)
            } else {
                // 최대 재전송 횟수 초과
                Log.w(TAG, "최대 재전송 횟수 초과: seqNum=${header.seqNum}, 인덱스=${header.segmentIndex}")
                
                // 큐에서 제거
                segmentMap.remove(header.segmentIndex)
                if (segmentMap.isEmpty()) {
                    retransmitQueue.remove(header.seqNum)
                }
            }
        }
    }
    
    /**
     * 재전송 큐에서 세그먼트 제거
     */
    fun removeFromRetransmitQueue(seqNum: Int, segmentIndex: Int) {
        retransmitQueue[seqNum]?.let { segmentMap ->
            segmentMap[segmentIndex]?.job?.cancel()
            segmentMap.remove(segmentIndex)
            
            if (segmentMap.isEmpty()) {
                retransmitQueue.remove(seqNum)
            }
        }
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        coroutineScope.cancel()
        reassemblyStore.clear()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        retransmitQueue.values.forEach { segmentMap ->
            segmentMap.values.forEach { it.job?.cancel() }
        }
        retransmitQueue.clear()
        completionCallbacks.clear()
    }
} 