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
    
    // BLE 스캔 및 광고 구성
    const val LEGACY_ADVERTISING_MAX_BYTES = 31 // 기본 광고 패킷 최대 크기
    const val SCAN_RESPONSE_MAX_BYTES = 31 // 스캔 응답 패킷 최대 크기
    const val TOTAL_ADVERTISING_BYTES = LEGACY_ADVERTISING_MAX_BYTES + SCAN_RESPONSE_MAX_BYTES // 총 62바이트
    
    // 블루투스 전파 환경 상수
    const val REFERENCE_RSSI_AT_1M = -59 // 1m 거리에서의 기준 RSSI 값
    const val PATH_LOSS_EXPONENT = 2.2 // 실내 환경에서의 경로 손실 지수 (일반적으로 2.0-4.0)

    // 타임아웃 및 간격 상수
    const val USER_TIMEOUT_MS = 30000L // 사용자 정보 유효시간 (30초)
    const val MIN_RSSI_UPDATE_INTERVAL_MS = 300L // RSSI 업데이트 최소 간격
    const val USER_INFO_UPDATE_INTERVAL_MS = 10000L // 사용자 정보 업데이트 간격 (10초)
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

    // 메인 광고 패킷 + 스캔 응답 패킷 활용시 가능한 최대 페이로드 크기
    const val MAX_MANUFACTURER_DATA_PAYLOAD_SIZE = 23
    const val MAX_CHAT_MESSAGE_CHUNK_LENGTH = MAX_MANUFACTURER_DATA_PAYLOAD_SIZE - PAYLOAD_HEADER_SIZE // 23 - 15 = 8 bytes
    
    // 스캔 응답 패킷을 활용한 확장된 최대 페이로드 크기
    const val MAX_EXTENDED_MANUFACTURER_DATA_SIZE = 54 // 31(메인) + 31(스캔응답) - 8(헤더)
}

// 주변 사용자 탐색 관련 상수
object NeighborDiscoveryConstants {
    const val MAX_NICKNAME_LENGTH_BYTES = 6 // 기존 NeighborAdvertiser의 MAX_NICKNAME_LENGTH
    const val DEVICE_ID_HASH_LENGTH = 3    // 기존 NeighborAdvertiser의 HASH_LENGTH
    
    // 거리 카테고리 (미터 단위)
    const val DISTANCE_CATEGORY_VERY_CLOSE = 10f
    const val DISTANCE_CATEGORY_CLOSE = 30f
    const val DISTANCE_CATEGORY_MEDIUM = 50f
    const val DISTANCE_CATEGORY_FAR = 100f
    const val DISTANCE_CATEGORY_VERY_FAR = 150f
    const val DISTANCE_CATEGORY_EXTREME = 200f
    const val DISTANCE_CATEGORY_OUT_OF_RANGE = 250f // 200m 초과
    
    // 위치 정밀도 관련 상수
    const val LOCATION_MULTIPLIER = 1000.0 // 위치 정보 인코딩 승수 (소수점 3자리)
    const val LOCATION_PRECISION = 1000.0 // 위치 정보 디코딩 나눗수 (소수점 3자리)
}

// 다른 상수들도 필요에 따라 이 파일에 추가 가능 