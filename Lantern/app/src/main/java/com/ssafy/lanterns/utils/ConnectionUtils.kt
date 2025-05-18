package com.ssafy.lanterns.utils

import androidx.compose.ui.graphics.Color
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionNear

/**
 * 거리에 따라 적절한 연결 강도 색상을 반환합니다.
 * - 0-100m: 초록색 (ConnectionNear)
 * - 101-200m: 노란색 (ConnectionMedium)
 * - 200m 초과: 빨간색 (ConnectionFar)
 * 
 * @param distance 거리 (미터 단위)
 * @return 거리에 따른 색상
 */
fun getConnectionColorByDistance(distance: Float): Color {
    return when {
        distance <= 100f -> ConnectionNear    // 100m 이하: 초록색
        distance <= 200f -> ConnectionMedium  // 200m 이하: 노란색
        else -> ConnectionFar                 // 200m 초과: 빨간색
    }
}

/**
 * 두 값 사이를 선형 보간합니다.
 * (현재 사용되지 않지만 향후 필요할 수 있어 유지)
 */
private fun lerp(start: Int, end: Int, ratio: Float = 0.5f): Int {
    return (start + (end - start) * ratio).toInt()
}

/**
 * 거리에 따른 연결 강도 텍스트를 반환합니다.
 * 
 * @param distance 거리 (미터 단위)
 * @return 연결 강도 설명 문자열
 */
fun getConnectionStrengthText(distance: Float): String {
    return when {
        distance <= 100f -> "강한 연결"
        distance <= 200f -> "중간 연결"
        else -> "약한 연결"
    }
} 