package com.ssafy.lanterns.utils

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * RSSI 값 관리 및 연결 강도 계산 클래스
 * - BLE 신호 강도 필터링 및 연결 품질 계산
 */
object SignalStrengthManager {
    // 논문 및 공식 자료 기반 RSSI 임계값 (실내 환경 기준)
    private const val RSSI_STRONG = -60  // 강한 신호 (3단계) - 약 0~2m 거리
    private const val RSSI_MEDIUM = -80  // 중간 신호 (2단계) - 약 2~10m 거리
    // -80 이하는 약한 신호 (1단계) - 10m 이상 거리
    
    // Bluetooth 신호 특성 상수 
    private const val REFERENCE_RSSI_AT_1M = -59 // 1m 거리 기준 RSSI
    private const val PATH_LOSS_EXPONENT = 2.2   // 실내 환경 경로 손실 지수 (2.0-4.0)
    
    // RSSI 이동 평균 필터링 관련 상수
    private const val MAX_HISTORY_SIZE = 5      // 이동 평균 계산에 사용할 샘플 수
    private const val OUTLIER_THRESHOLD = 10    // 이상치 판별 임계값 (dBm)
    
    // RSSI 값 이력 관리
    private val rssiHistory = mutableMapOf<String, MutableList<Int>>()
    
    /**
     * RSSI 값에 따른 신호 강도 레벨 계산 (1-3)
     * @param rssi 현재 RSSI 값
     * @return 연결 강도 레벨 (1: 약함, 2: 중간, 3: 강함)
     */
    fun calculateSignalLevel(rssi: Int): Int {
        return when {
            rssi > RSSI_STRONG -> 3  // 강한 신호
            rssi > RSSI_MEDIUM -> 2  // 중간 신호
            else -> 1                // 약한 신호
        }
    }
    
    /**
     * RSSI와 Depth를 조합한 연결 품질 계산
     * @param rssi RSSI 값
     * @param depth 중계 홉 수 (1: 직접 연결, 2 이상: 간접 연결)
     * @return 연결 품질 레벨 (1-3)
     */
    fun calculateConnectionQuality(rssi: Int, depth: Int): Int {
        // 기본 RSSI 레벨 계산
        val rssiLevel = calculateSignalLevel(rssi)
        
        // Depth에 따른 페널티 적용
        return when (depth) {
            1 -> rssiLevel                // 직접 연결은 페널티 없음
            2 -> max(1, rssiLevel - 1)    // 1홉 중계는 1단계 낮춤
            else -> 1                     // 2홉 이상은 항상 낮은 품질
        }
    }
    
    /**
     * RSSI 값 이동 평균 계산 (노이즈 감소)
     * - Kalman-Histogram 방식 적용: RSSI 이력 저장 후 이상치 제거 및 평균 계산
     * 
     * @param bleId 기기 ID
     * @param rssi 현재 RSSI 값
     * @return 필터링된 RSSI 값
     */
    fun getSmoothedRssi(bleId: String, rssi: Int): Int {
        // 기기별 이력 가져오기 또는 새로 생성
        val history = rssiHistory.getOrPut(bleId) { mutableListOf() }
        
        // 심각한 이상치 필터링 (명백히 잘못된 값 제외)
        if (rssi != 0 && rssi != -127) {
            // 이전 값이 있는 경우 급격한 변화 검사
            if (history.isNotEmpty()) {
                val prevAvg = history.average().toInt()
                // 이전 평균과 현재 값의 차이가 심한 경우 (이상치) 처리
                if (Math.abs(prevAvg - rssi) > OUTLIER_THRESHOLD) {
                    // 이상치는 이전 값과 현재 값의 중간값으로 대체 (급격한 변화 완화)
                    history.add((prevAvg + rssi) / 2)
                } else {
                    history.add(rssi)
                }
            } else {
                history.add(rssi)
            }
        }
        
        // 이력 크기 제한
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(0)
        }
        
        // 평균 계산 (이상치 필터링)
        return if (history.size >= 3) {
            // 상위/하위 10% 제거 후 평균 계산
            val sorted = history.sorted()
            val trimCount = max(1, history.size / 10)
            val filtered = sorted.subList(trimCount, history.size - trimCount)
            filtered.sum() / filtered.size
        } else {
            // 샘플이 적은 경우 단순 평균
            history.sum() / history.size
        }
    }
    
    /**
     * RSSI를 거리(미터)로 변환
     * - 거리 = 10^((측정기준RSSI - 현재RSSI) / (10 * 손실지수))
     * 
     * @param rssi 현재 RSSI 값
     * @return 대략적인 거리 (미터)
     */
    fun rssiToDistance(rssi: Int): Float {
        if (rssi == 0 || rssi == -127) return 50f // 기본값
        
        return Math.pow(10.0, (REFERENCE_RSSI_AT_1M - rssi) / (10.0 * PATH_LOSS_EXPONENT)).toFloat()
    }
    
    /**
     * 신호 레벨에 따른 색상 반환
     * @param level 신호 강도 레벨 (1-3)
     * @return 색상 정수값
     */
    fun getColorForSignalLevel(level: Int): Int {
        return when (level) {
            3 -> Color.GREEN   // 강한 신호
            2 -> Color.YELLOW  // 중간 신호
            else -> Color.RED  // 약한 신호
        }
    }
    
    /**
     * 히스토리 정리 - 특정 시간 이상 업데이트되지 않은 기기 이력 제거
     * @param maxAgeMs 최대 보관 시간 (밀리초)
     */
    fun cleanupHistory(maxAgeMs: Long = 60000) {
        val currentTime = System.currentTimeMillis()
        val bleIdsToRemove = mutableListOf<String>()
        
        // 제거할 기기 ID 수집
        rssiHistory.keys.forEach { bleId ->
            val lastUpdateTime = rssiHistory[bleId]?.lastOrNull() ?: 0
            if (currentTime - lastUpdateTime > maxAgeMs) {
                bleIdsToRemove.add(bleId)
            }
        }
        
        // 오래된 항목 제거
        bleIdsToRemove.forEach { bleId ->
            rssiHistory.remove(bleId)
        }
    }
} 