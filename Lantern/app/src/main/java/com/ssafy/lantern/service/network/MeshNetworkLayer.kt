package com.ssafy.lantern.service.network

import android.util.Log
import android.util.LruCache
import com.ssafy.lantern.data.model.MeshPdu
import com.ssafy.lantern.data.model.MessageType
import com.ssafy.lantern.service.ble.BleComm
import com.ssafy.lantern.service.ble.BleCommImpl
import com.ssafy.lantern.service.transport.TransportLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메쉬 네트워크 레이어 구현
 * 라우팅과 플러딩 방식으로 메시지 전달을 담당
 */
@Singleton
class MeshNetworkLayer @Inject constructor(
    private val bleComm: BleComm,
    private val transportLayer: TransportLayer
) {
    companion object {
        private const val TAG = "MeshNetworkLayer"
        
        // 브로드캐스트 주소
        const val BROADCAST_ADDRESS = 0xFFFF
        
        // 기본 TTL
        private const val DEFAULT_TTL = 5
        
        // 메시지 캐시 크기
        private const val MESSAGE_CACHE_SIZE = 100
        
        // 메시지 캐시 유효 시간 (밀리초)
        private const val MESSAGE_CACHE_TIMEOUT = 60_000L  // 1분
    }
    
    // 메시지 ID 생성기 (AtomicLong -> AtomicInteger)
    private val messageIdGenerator = AtomicInteger(0)
    
    // 로컬 유니캐스트 주소
    private var localAddress = 0
    
    // 메시지 처리 리스너
    private val messageListeners = mutableListOf<(MeshPdu) -> Unit>()
    
    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 메시지 캐시 (중복 제거용) - 키를 Long에서 Int로 변경
    private val messageCache = object : LruCache<Int, Long>(MESSAGE_CACHE_SIZE) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Long, newValue: Long?) {
            // 캐시 항목 제거 시 호출 (필요시 정리 작업 수행)
            Log.d(TAG, "메시지 캐시에서 제거: msgId=$key")
        }
    }
    
    // BLE 스캔 콜백
    private val scanCallback: (android.bluetooth.le.ScanResult) -> Unit = scanCallback@{ result ->
        val scanRecord = result.scanRecord ?: return@scanCallback
        val serviceData = scanRecord.serviceData
        
        // 서비스 UUID 확인
        val meshUuid = android.os.ParcelUuid(BleCommImpl.MESH_SERVICE_UUID)
        if (serviceData?.containsKey(meshUuid) == true) {
            val rawData = serviceData[meshUuid] ?: return@scanCallback
            if (rawData.isEmpty()) return@scanCallback
            
            coroutineScope.launch {
                handleIncoming(rawData)
            }
        }
    }
    
    init {
        // BLE 스캔은 startScan() 메서드 호출 시 시작
    }
    
    /**
     * BLE 스캔 시작
     * 권한 및 기타 요구사항이 충족되었는지 확인 후 호출해야 함
     */
    fun startScan() {
        Log.d(TAG, "BLE 메시 스캔 시작")
        bleComm.startScanning(scanCallback)
    }
    
    /**
     * 로컬 주소 설정
     */
    fun setLocalAddress(address: Int) {
        localAddress = address
        Log.d(TAG, "로컬 주소 설정: $address")
    }
    
    /**
     * 메시지 리스너 추가
     */
    fun addMessageListener(listener: (MeshPdu) -> Unit) {
        messageListeners.add(listener)
    }
    
    /**
     * 메시지 리스너 제거
     */
    fun removeMessageListener(listener: (MeshPdu) -> Unit) {
        messageListeners.remove(listener)
    }
    
    /**
     * 수신된 원시 데이터 처리
     */
    suspend fun handleIncoming(rawData: ByteArray) {
        try {
            // 전송 레이어에서 처리 (분할된 패킷 재조립)
            val payloadData = transportLayer.processFragment(rawData) { reassembled ->
                // 재조립 완료 콜백
                processMeshPdu(reassembled)
            }
            
            // 단일 패킷이면 즉시 처리
            if (payloadData != null) {
                processMeshPdu(payloadData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "수신 데이터 처리 중 오류 발생", e)
        }
    }
    
    /**
     * PDU를 파싱하여 처리
     */
    private fun processMeshPdu(payload: ByteArray) {
        try {
            // PDU 파싱
            val pdu = parseMeshPdu(payload) ?: return
            
            // 캐시 확인 (중복 메시지 필터링)
            val currentTime = System.currentTimeMillis()
            val cachedTime = messageCache.get(pdu.messageId)
            
            if (cachedTime != null && currentTime - cachedTime < MESSAGE_CACHE_TIMEOUT) {
                // 이미 처리한 메시지
                Log.d(TAG, "중복 메시지 무시: msgId=${pdu.messageId}")
                return
            }
            
            // 캐시에 추가
            messageCache.put(pdu.messageId, currentTime)
            
            // 목적지 확인
            if (pdu.dst != BROADCAST_ADDRESS && pdu.dst != localAddress) {
                // 다른 노드를 위한 메시지인 경우, TTL 체크하여 재전파
                if (pdu.ttl > 0) {
                    // TTL 감소하여 재전파
                    pdu.ttl--
                    Log.d(TAG, "다른 노드로 메시지 재전파: msgId=${pdu.messageId}, 남은 TTL=${pdu.ttl}")
                    send(pdu)
                }
                return
            }
            
            // 목적지가 로컬 또는 브로드캐스트인 경우 리스너에게 알림
            Log.d(TAG, "메시지 수신 완료: type=${pdu.type}, src=${pdu.src}, dst=${pdu.dst}, size=${pdu.body.size}바이트")
            messageListeners.forEach { it(pdu) }
            
            // 브로드캐스트 메시지이고 TTL이 남아있으면 재전파
            if (pdu.dst == BROADCAST_ADDRESS && pdu.ttl > 0) {
                pdu.ttl--
                Log.d(TAG, "브로드캐스트 메시지 재전파: msgId=${pdu.messageId}, 남은 TTL=${pdu.ttl}")
                send(pdu)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDU 처리 중 오류 발생", e)
        }
    }
    
    /**
     * 바이트 배열로부터 MeshPdu 파싱
     */
    private fun parseMeshPdu(bytes: ByteArray): MeshPdu? {
        if (bytes.size < 10) {  // 최소 헤더 크기 확인 (Int + Int + Int + Byte + Byte = 4 + 2 + 2 + 1 + 1 = 10바이트)
            Log.e(TAG, "PDU 파싱 실패: 데이터 크기 부족 (${bytes.size}바이트)")
            return null
        }
        
        try {
            ByteArrayInputStream(bytes).use { byteStream ->
                DataInputStream(byteStream).use { dataStream ->
                    // 헤더 파싱 (최적화된 구조)
                    val messageId = dataStream.readInt()  // Long -> Int (4바이트)
                    val src = dataStream.readShort().toInt() and 0xFFFF  // Int -> Short (2바이트)
                    val dst = dataStream.readShort().toInt() and 0xFFFF  // Int -> Short (2바이트)
                    val ttl = dataStream.readByte().toInt() and 0xFF  // 1바이트
                    val typeOrdinal = dataStream.readByte().toInt() and 0xFF  // 1바이트
                    
                    // 메시지 타입
                    val type = MessageType.values().getOrElse(typeOrdinal) { MessageType.CHAT }
                    
                    // 페이로드 읽기
                    val bodySize = bytes.size - 10  // 새 헤더 크기(10바이트) 제외
                    val body = ByteArray(bodySize)
                    dataStream.read(body)
                    
                    return MeshPdu(messageId, src, dst, ttl, type, body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDU 파싱 중 오류 발생", e)
            return null
        }
    }
    
    /**
     * MeshPdu를 바이트 배열로 직렬화
     */
    private fun serializeMeshPdu(pdu: MeshPdu): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { dataStream ->
            // 헤더 작성 (최적화된 구조)
            dataStream.writeInt(pdu.messageId)  // Long -> Int (4바이트)
            dataStream.writeShort(pdu.src.toShort().toInt())  // Int -> Short (2바이트)
            dataStream.writeShort(pdu.dst.toShort().toInt())  // Int -> Short (2바이트)
            dataStream.writeByte(pdu.ttl)  // 1바이트
            dataStream.writeByte(pdu.type.ordinal)  // 1바이트
            
            // 페이로드 작성
            dataStream.write(pdu.body)
        }
        
        return output.toByteArray()
    }
    
    /**
     * 메시지 전송
     */
    fun send(pdu: MeshPdu) {
        if (localAddress == 0) {
            Log.e(TAG, "로컬 주소가 설정되지 않아 전송할 수 없습니다")
            return
        }
        
        try {
            // PDU 직렬화
            val serialized = serializeMeshPdu(pdu)
            
            // 전송 레이어에서 세그먼트화
            val segments = transportLayer.segment(serialized)
            
            // 세그먼트 광고
            Log.d(TAG, "메시지 전송 시작: type=${pdu.type}, dst=${pdu.dst}, ${segments.size}개 세그먼트")
            
            // 코루틴 내에서 실행하여 메인 스레드 블로킹 방지
            coroutineScope.launch {
                for (segment in segments) {
                    bleComm.startAdvertising(segment)
                    
                    // 재전송 큐에 추가 (신뢰성 있는 전송을 위해)
                    transportLayer.addToRetransmitQueue(segment) { retryData ->
                        bleComm.startAdvertising(retryData)
                    }
                    
                    // 세그먼트 간 지연 증가 - 광고 시스템 안정화
                    delay(300)
                }
                
                Log.d(TAG, "메시지 세그먼트 전송 완료: type=${pdu.type}, dst=${pdu.dst}, ${segments.size}개 세그먼트")
            }
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 중 오류 발생", e)
        }
    }
    
    /**
     * 새 PDU 생성 및 전송
     * 
     * @param dst 목적지 주소
     * @param type 메시지 타입
     * @param body 페이로드
     * @param ttl 생존 시간 (TTL)
     */
    fun sendMessage(dst: Int, type: MessageType, body: ByteArray, ttl: Int = DEFAULT_TTL) {
        if (localAddress == 0) {
            Log.e(TAG, "로컬 주소가 설정되지 않아 전송할 수 없습니다")
            return
        }
        
        // 새 메시지 ID 생성 (AtomicInteger.incrementAndGet()은 이미 Int를 반환)
        val messageId = messageIdGenerator.incrementAndGet()
        
        // PDU 생성
        val pdu = MeshPdu(messageId, localAddress, dst, ttl, type, body)
        
        // 메시지 캐시에 추가 (자신의 메시지도 캐싱)
        messageCache.put(messageId, System.currentTimeMillis())
        
        // 전송
        send(pdu)
    }
    
    /**
     * 브로드캐스트 메시지 전송
     */
    fun broadcast(type: MessageType, body: ByteArray, ttl: Int = DEFAULT_TTL) {
        sendMessage(BROADCAST_ADDRESS, type, body, ttl)
    }
    
    /**
     * 유니캐스트 메시지 전송
     */
    fun unicast(dstAddress: Int, type: MessageType, body: ByteArray, ttl: Int = DEFAULT_TTL) {
        sendMessage(dstAddress, type, body, ttl)
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        bleComm.stopScanning()
        bleComm.stopAdvertising()
        messageCache.evictAll()
        messageListeners.clear()
    }
} 