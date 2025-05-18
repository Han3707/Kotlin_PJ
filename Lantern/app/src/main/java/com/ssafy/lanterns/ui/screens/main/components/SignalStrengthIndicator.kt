package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.lanterns.utils.SignalStrengthManager

/**
 * 신호 강도 표시 컴포넌트
 * - RSSI 값에 따른 3단계 연결 강도 시각화
 * 
 * @param level 신호 강도 레벨 (1-3)
 * @param modifier 컴포넌트 수정자
 */
@Composable
fun SignalStrengthIndicator(
    level: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 3개의 바 표시
        repeat(3) { index ->
            val isActive = index < level
            val color = when {
                isActive && level == 3 -> Color.Green
                isActive && level == 2 -> Color.Yellow
                isActive && level == 1 -> Color.Red
                else -> Color.Gray.copy(alpha = 0.3f)
            }
            
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(4.dp + (index * 3).dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * 신호 강도 텍스트 색상 계산
 * @param level 신호 강도 레벨 (1-3)
 * @return 해당 레벨에 적합한 색상
 */
@Composable
fun signalLevelColor(level: Int): Color {
    return when (level) {
        3 -> Color.Green
        2 -> Color.Yellow
        else -> Color.Red
    }
} 