package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionNear
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.ui.theme.LanternYellowDark
import com.ssafy.lanterns.utils.getConnectionColorByDistance

/**
 * 주변 사람 표시 점 컴포넌트
 */
@Composable
fun PersonDot(
    modifier: Modifier = Modifier, 
    signalStrength: Float, 
    pulseScale: Float,
    glowAlpha: Float,
    distance: Float = (1f - signalStrength) * 10f // 신호 강도에 따른 가상 거리 계산
) {
    // 신호 강도에 따라 색상 결정
    val dotColor = getConnectionColorByDistance(distance)
    
    // 더 큰 크기로 설정 (기존 8dp → 12dp)
    val baseSize = 12.dp
    
    Box(
        modifier = modifier
    ) {
        // 빛나는 효과 (반짝이는 효과 강화)
        Box(
            modifier = Modifier
                .size(baseSize * 3.0f) // 발광 영역 확대
                .alpha(glowAlpha * signalStrength * 1.2f) // 밝기 약간 증가
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.8f), // 중앙 색상 더 진하게
                            dotColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 점 (크기 증가)
        Box(
            modifier = Modifier
                .size(baseSize)
                .align(Alignment.Center)
                .shadow(6.dp, CircleShape) // 그림자 강화
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, dotColor),
                        radius = baseSize.value * 0.8f
                    )
                )
                .border(1.0.dp, Color.White.copy(alpha = 0.9f), CircleShape) // 테두리 두껍게
        )
        
        // 반짝임 효과 강화
        Box(
            modifier = Modifier
                .size(baseSize * 2.0f) // 크기 증가
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.6f * signalStrength) // 투명도 감소로 더 밝게
                .border(
                    width = 1.5.dp, // 테두리 두껍게
                    color = dotColor.copy(alpha = 0.8f), // 색상 더 진하게
                    shape = CircleShape
                )
        )
    }
}

/**
 * 랜턴 도트 컴포넌트 - 랜턴 표시를 위한 특별 도트
 */
@Composable
fun LanternDot(
    modifier: Modifier = Modifier, 
    signalStrength: Float, 
    pulseScale: Float,
    glowAlpha: Float,
    distance: Float // 실제 거리 값 사용 (카테고리화된 값)
) {
    // 거리에 따른 색상 결정 - 이미 카테고리화된 값 사용
    val dotColor = getConnectionColorByDistance(distance)
    
    // 거리 카테고리에 따른 사이즈 조정
    val baseSize = when {
        distance <= 50f -> 16.dp  // 가까운 거리는 더 크게
        distance <= 100f -> 14.dp // 중간 거리는 기본 크기
        else -> 12.dp            // 먼 거리는 작게
    }
    
    // 거리 카테고리에 따른 글로우 효과 조정
    val glowMultiplier = when {
        distance <= 50f -> 1.6f  // 가까운 거리는 더 밝게
        distance <= 100f -> 1.4f // 중간 거리는 기본 밝기
        distance <= 200f -> 1.2f // 먼 거리는 조금 어둡게
        else -> 0.8f            // 매우 먼 거리는 많이 어둡게
    }
    
    Box(
        modifier = modifier
    ) {
        // 발광 효과 (랜턴 느낌)
        Box(
            modifier = Modifier
                .size(baseSize * 3.5f)
                .alpha(glowAlpha * signalStrength * glowMultiplier)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.9f),
                            dotColor.copy(alpha = 0.5f),
                            dotColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 랜턴 점
        Box(
            modifier = Modifier
                .size(baseSize)
                .align(Alignment.Center)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LanternYellow,
                            dotColor
                        ),
                        radius = baseSize.value
                    )
                )
                .border(1.5.dp, LanternYellowDark.copy(alpha = 0.9f), CircleShape)
        )
        
        // 랜턴 파동 효과
        Box(
            modifier = Modifier
                .size(baseSize * 2.5f)
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.7f * signalStrength)
                .border(
                    width = 2.dp,
                    color = dotColor.copy(alpha = 0.8f),
                    shape = CircleShape
                )
        )
    }
} 