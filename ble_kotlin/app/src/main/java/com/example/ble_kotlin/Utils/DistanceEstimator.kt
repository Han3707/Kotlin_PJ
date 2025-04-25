package com.example.ble_kotlin.Utils

import kotlin.math.pow

/**
 * BLE RSSI 값을 기반으로 거리를 추정하는 유틸리티 객체.
 * 
 * 거리 추정은 물리적 환경, 기기 특성, 방해물 등 다양한 요인에 영향을 받아
 * 정확도가 크게 달라질 수 있으므로 참고용으로만 사용하세요.
 */
object DistanceEstimator {

    /**
     * RSSI 값을 기반으로 대략적인 거리를 추정합니다.
     *
     * @param rssi 수신 신호 강도 (dBm) - 일반적으로 -30dBm(가까움)에서 -100dBm(멈) 사이의 값
     * @param txPower 송신 전력 또는 참조 거리에서의 RSSI 값 (기본값: -59, 1m 거리에서 측정된 일반적인 값)
     * @param n 경로 손실 지수 (기본값: 2.0, 자유 공간)
     *          실내 환경: 1.6~1.8, 장애물이 많은 환경: 2.7~4.3 정도의 값 사용
     * @return 추정 거리 (미터)
     * 
     * 주의: 이 계산은 대략적인 추정치이며, 환경 조건에 따라 정확도가 크게 달라질 수 있습니다.
     *      신뢰할 수 있는 정확한 거리 측정이 필요한 경우 다른 기술을 고려하세요.
     */
    fun estimateDistance(rssi: Int, txPower: Int = -59, n: Float = 2.0f): Double {
        // RSSI가 0이거나 비정상적으로 강한 경우 에러 처리
        if (rssi >= 0) return -1.0
        
        // 거리 계산 공식: d = 10^((txPower - rssi) / (10 * n))
        // 이는 Log-distance path loss model에 기반한 공식입니다.
        return 10.0.pow((txPower - rssi) / (10.0 * n))
    }
    
    /**
     * RSSI 값에 따른 신호 강도 카테고리를 반환합니다.
     * 
     * @param rssi 수신 신호 강도 (dBm)
     * @return 신호 강도 카테고리 (EXCELLENT, GOOD, FAIR, WEAK, NO_SIGNAL)
     */
    fun getSignalStrength(rssi: Int): SignalStrength {
        return when {
            rssi >= -60 -> SignalStrength.EXCELLENT
            rssi >= -70 -> SignalStrength.GOOD
            rssi >= -80 -> SignalStrength.FAIR
            rssi >= -90 -> SignalStrength.WEAK
            else -> SignalStrength.NO_SIGNAL
        }
    }
    
    /**
     * 신호 강도 카테고리
     */
    enum class SignalStrength {
        EXCELLENT, // 매우 좋음 (-60 dBm 이상)
        GOOD,      // 좋음 (-70 dBm ~ -60 dBm)
        FAIR,      // 보통 (-80 dBm ~ -70 dBm)
        WEAK,      // 약함 (-90 dBm ~ -80 dBm)
        NO_SIGNAL  // 매우 약함 (-90 dBm 미만)
    }
} 