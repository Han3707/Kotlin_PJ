package com.example.ble_kotlin.Utils

import java.util.UUID

/**
 * 앱 전반에 사용되는 상수들을 정의하는 객체입니다.
 */
object Constants {
    /**
     * BLE 스캔 및 광고에 사용될 서비스 UUID.
     * 이 UUID를 가진 기기만 필터링하여 표시합니다.
     */
    const val APP_SERVICE_UUID_STRING = "00001234-0000-1000-8000-00805F9B34FB"
    val APP_SERVICE_UUID: UUID = UUID.fromString(APP_SERVICE_UUID_STRING)

    /**
     * GATT 통신에 사용될 서비스 UUID (예: 배터리 서비스).
     */
    const val GATT_SERVICE_UUID_STRING = "0000180F-0000-1000-8000-00805F9B34FB"
    val GATT_SERVICE_UUID: UUID = UUID.fromString(GATT_SERVICE_UUID_STRING)

    /**
     * GATT 통신에 사용될 캐릭터리스틱 UUID (예: 배터리 레벨 - 읽기/쓰기 가능).
     * 쓰기 가능한 속성을 가진 캐릭터리스틱을 사용합니다.
     */
    const val GATT_CHARACTERISTIC_UUID_STRING = "00002A19-0000-1000-8000-00805F9B34FB"
    val GATT_CHARACTERISTIC_UUID: UUID = UUID.fromString(GATT_CHARACTERISTIC_UUID_STRING)

    // 권한 요청 코드 정의 (필요시 추가)
    const val REQUEST_ENABLE_BT = 1001
    const val REQUEST_FINE_LOCATION = 1002
    const val REQUEST_BLUETOOTH_SCAN = 1003
    const val REQUEST_BLUETOOTH_ADVERTISE = 1004
    const val REQUEST_BLUETOOTH_CONNECT = 1005

} 