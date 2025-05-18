package com.ssafy.lanterns.config

import java.util.UUID

object BleConstants {
    // 기존 NeighborScanner, NeighborAdvertiser에서 사용하던 UUID (현재 코드에서는 미사용으로 보임)
    // val NEIGHBOR_DISCOVERY_SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") 

    // 1:1 채팅용 서비스 UUID (새로 정의)
    val CHAT_SERVICE_UUID: UUID = UUID.fromString("00001102-0000-1000-8000-00805F9B34FB") // 예시 UUID

    // 공개 채팅용 서비스 UUID (필요시 정의)
    // val PUBLIC_CHAT_SERVICE_UUID: UUID = UUID.fromString("00001103-0000-1000-8000-00805F9B34FB") 

    // Manufacturer ID (NeighborAdvertiser/Scanner와 값 일치 필요)
    const val MANUFACTURER_ID_USER = 0xFFFF
    const val MANUFACTURER_ID_LOCATION = 0xFFFE
    const val MANUFACTURER_ID_CHAT = 0xFFFD // 채팅용 Manufacturer ID (새로 정의)

}

object ChatConstants {
    // Message Types
    const val MESSAGE_TYPE_DM_CHUNK: Byte = 0x01 // 1:1 DM 청크
    // const val MESSAGE_TYPE_PUBLIC_CHUNK: Byte = 0x02 // 공개 채팅 청크 (필요시)

    // Payload Structure for Manufacturer Specific Data (used by ChatAdvertiserManager and ChatScannerManager)
    // Assumed total available bytes for manufacturer data payload: 23 bytes
    // [TYPE(1)] [RECIPIENT_ID(2)] [SENDER_ID(2)] [CHUNK_NUM(1)] [TOTAL_CHUNKS(1)] [MESSAGE_ID(8)] [CHUNK_DATA(variable)]

    const val PAYLOAD_OFFSET_TYPE = 0
    const val PAYLOAD_SIZE_TYPE = 1

    const val PAYLOAD_OFFSET_RECIPIENT_ID = PAYLOAD_OFFSET_TYPE + PAYLOAD_SIZE_TYPE
    const val PAYLOAD_SIZE_RECIPIENT_ID = 2 // Short

    const val PAYLOAD_OFFSET_SENDER_ID = PAYLOAD_OFFSET_RECIPIENT_ID + PAYLOAD_SIZE_RECIPIENT_ID
    const val PAYLOAD_SIZE_SENDER_ID = 2 // Short

    const val PAYLOAD_OFFSET_CHUNK_NUMBER = PAYLOAD_OFFSET_SENDER_ID + PAYLOAD_SIZE_SENDER_ID
    const val PAYLOAD_SIZE_CHUNK_NUMBER = 1 // Byte

    const val PAYLOAD_OFFSET_TOTAL_CHUNKS = PAYLOAD_OFFSET_CHUNK_NUMBER + PAYLOAD_SIZE_CHUNK_NUMBER
    const val PAYLOAD_SIZE_TOTAL_CHUNKS = 1 // Byte

    const val PAYLOAD_OFFSET_MESSAGE_ID = PAYLOAD_OFFSET_TOTAL_CHUNKS + PAYLOAD_SIZE_TOTAL_CHUNKS
    const val PAYLOAD_SIZE_MESSAGE_ID = 8 // Long

    const val PAYLOAD_HEADER_SIZE = PAYLOAD_SIZE_TYPE +
                                    PAYLOAD_SIZE_RECIPIENT_ID +
                                    PAYLOAD_SIZE_SENDER_ID +
                                    PAYLOAD_SIZE_CHUNK_NUMBER +
                                    PAYLOAD_SIZE_TOTAL_CHUNKS +
                                    PAYLOAD_SIZE_MESSAGE_ID // 1 + 2 + 2 + 1 + 1 + 8 = 15 bytes

    // Assuming Manufacturer Specific Data can hold around 23 bytes of payload
    // (after Manufacturer ID itself which is 2 bytes)
    const val MAX_MANUFACTURER_DATA_PAYLOAD_SIZE = 23
    const val MAX_CHAT_MESSAGE_CHUNK_LENGTH = MAX_MANUFACTURER_DATA_PAYLOAD_SIZE - PAYLOAD_HEADER_SIZE // 23 - 15 = 8 bytes
}

// 기존 NeighborAdvertiser/Scanner의 값들도 여기로 옮길 수 있음
object NeighborDiscoveryConstants {
    const val MAX_NICKNAME_LENGTH_BYTES = 6 // 기존 NeighborAdvertiser의 MAX_NICKNAME_LENGTH
    const val DEVICE_ID_HASH_LENGTH = 3    // 기존 NeighborAdvertiser의 HASH_LENGTH
}

// 다른 상수들도 필요에 따라 이 파일에 추가 가능 