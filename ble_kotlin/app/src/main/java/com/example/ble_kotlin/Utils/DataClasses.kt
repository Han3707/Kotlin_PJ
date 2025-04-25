package com.example.ble_kotlin.Utils

/**
 * 스캔된 BLE 기기를 나타내는 데이터 클래스입니다.
 *
 * @property deviceName 기기 이름 (Nullable).
 * @property deviceAddress 기기 MAC 주소 (고유 식별자).
 * @property rssi 수신 신호 강도 (dBm).
 * @property estimatedDistance 추정 거리 (미터, Nullable). RSSI 기반으로 계산됩니다.
 */
data class ScannedDevice(
    val deviceName: String?,
    val deviceAddress: String,
    val rssi: Int,
    var estimatedDistance: Double? = null // 초기에는 null, 필요시 계산 후 업데이트
)

/**
 * GATT 채팅 메시지를 나타내는 데이터 클래스입니다.
 *
 * @property sender 메시지 발신자 ("Me" 또는 상대방 기기 주소).
 * @property text 메시지 내용.
 * @property timestamp 메시지 수신/발신 시각 (MILLISECONDS).
 */
data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) 