package com.example.bletest.utils

import android.os.ParcelUuid
import java.util.UUID

object Constants {
    // !!! 고유한 UUID로 변경하세요 !!!
    val MESH_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000aabb-0000-1000-8000-00805f9b34fb") // 예시 UUID
    val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ccdd-0000-1000-8000-00805f9b34fb") // 예시 UUID
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // 메시지 타입 구분자
    const val MSG_TYPE_DELIMITER = "::" // 타입과 나머지 내용 구분
    const val PAYLOAD_DELIMITER = "|" // 페이로드 내부 구분 (기존)

    // 메시지 타입 정의
    const val MSG_TYPE_INFO = "INFO" // 기기 정보 교환 메시지
    const val MSG_TYPE_APP = "APP" // 일반 앱 메시지 (Ping, Pong 등)

    // INFO 메시지 내 기기 ID 구분자
    const val INFO_DEVICE_DELIMITER = ","
} 