package com.ssafy.lantern.data.model

/**
 * 메시 네트워크 PDU (프로토콜 데이터 유닛)
 * 메시지 형식 최적화: 헤더 크기를 최소화하여 작은 BLE 패킷에 더 많은 데이터를 담을 수 있도록 함
 */
data class MeshPdu(
    // 메시지 고유 ID (8바이트 -> 4바이트로 축소)
    val messageId: Int, 
    
    // 발신 노드 주소 (4바이트 -> 2바이트로 축소)
    val src: Int,  
    
    // 목적지 노드 주소 (4바이트 -> 2바이트로 축소)
    val dst: Int,  
    
    // TTL(Time To Live) (1바이트)
    var ttl: Int,  
    
    // 메시지 타입 (1바이트)
    val type: MessageType,  
    
    // 페이로드
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshPdu

        if (messageId != other.messageId) return false
        if (src != other.src) return false
        if (dst != other.dst) return false
        if (ttl != other.ttl) return false
        if (type != other.type) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + src.hashCode()
        result = 31 * result + dst.hashCode()
        result = 31 * result + ttl
        result = 31 * result + type.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/**
 * 메시지 타입 정의
 */
enum class MessageType {
    CHAT,           // 채팅 메시지
    PROVISIONING,   // 프로비저닝 메시지
    ACK,            // 확인 응답
    PING,           // 핑 요청
    PONG,           // 핑 응답
    STATUS          // 상태 메시지
} 