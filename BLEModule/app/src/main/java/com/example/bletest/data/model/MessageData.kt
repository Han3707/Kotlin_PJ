package com.example.bletest.data.model

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * BLE 메시지 데이터를 표현하는 통합 클래스
 * 기존의 ParsedMessage, MessageRequest, MessageResult 클래스 내용 통합 -> 이제 관련 없음
 */
@Parcelize
data class MessageData(
    // 기본 메시지 식별 정보
    val messageId: String = UUID.randomUUID().toString(),
    val sourceId: String = "",
    val targetId: String? = null,
    val messageType: MessageType = MessageType.UNKNOWN,
    val content: String? = null,
    
    // 메시지 메타데이터
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val ttl: Int = 3,
    val protocolVersion: Int = 1,
    
    // 전송 상태 정보
    val isSent: Boolean = false,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val error: String? = null,
    val errorCode: Int = 0,
    
    // 디바이스 정보
    val device: BluetoothDevice? = null,
    val deviceAddress: String = device?.address ?: "",
    
    // 실제 데이터
    val rawData: ByteArray? = null
) : Parcelable {
    
    /**
     * 이 메시지가 성공적으로 보내졌는지 확인
     */
    fun isSuccess(): Boolean {
        return error == null && errorCode == 0
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageData

        if (messageId != other.messageId) return false
        if (sourceId != other.sourceId) return false
        if (targetId != other.targetId) return false
        if (messageType != other.messageType) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (isOutgoing != other.isOutgoing) return false
        if (deviceAddress != other.deviceAddress) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + (targetId?.hashCode() ?: 0)
        result = 31 * result + messageType.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        return result
    }
    
    companion object {
        // 이전 클래스와의 호환성을 위한 생성 함수 제거
        // /**
        //  * ParsedMessage에서 MessageData를 생성
        //  */
        // fun fromParsedMessage(parsedMessage: ParsedMessage, device: BluetoothDevice? = null, isOutgoing: Boolean = false): MessageData {
        //     return MessageData(
        //         messageId = parsedMessage.messageId ?: UUID.randomUUID().toString(),
        //         sourceId = parsedMessage.sourceId ?: "",
        //         targetId = parsedMessage.targetId,
        //         messageType = parsedMessage.messageType,
        //         content = parsedMessage.content,
        //         error = parsedMessage.error,
        //         ttl = parsedMessage.ttl,
        //         protocolVersion = parsedMessage.protocolVersion,
        //         device = device,
        //         isOutgoing = isOutgoing
        //     )
        // }
        //
        // /**
        //  * MessageRequest에서 MessageData를 생성
        //  */
        // fun fromRequest(request: MessageRequest, sourceId: String): MessageData {
        //     return MessageData(
        //         sourceId = sourceId,
        //         targetId = request.targetId,
        //         messageType = request.messageType,
        //         content = request.content,
        //         ttl = request.ttl,
        //         isOutgoing = true
        //     )
        // }
    }
}

// 추후 메시지 데이터 모델 클래스가 위치할 파일입니다. 