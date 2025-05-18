package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.BleDarkBlue
import com.ssafy.lanterns.ui.theme.BluetoothColor
import com.ssafy.lanterns.ui.theme.BluetoothGlowColor
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.utils.getConnectionColorByDistance
import kotlin.math.cos
import kotlin.math.sin
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.ui.theme.LanternYellowDark
import com.ssafy.lanterns.ui.theme.DeepOrange
import com.ssafy.lanterns.ui.theme.MainScreenCardBg
import com.ssafy.lanterns.ui.theme.RadarEdgeColor
import com.ssafy.lanterns.ui.theme.RadarGradientEnd
import com.ssafy.lanterns.ui.theme.RadarGradientMiddle
import com.ssafy.lanterns.ui.theme.RadarGradientStart
import com.ssafy.lanterns.ui.theme.RadarLineColor

/**
 * 메인 화면의 중앙 컨텐츠
 */
@Composable
fun MainContent(
    @Suppress("UNUSED_PARAMETER") // 현재 직접 사용하지 않지만 향후 사용 예정
    isScanning: Boolean,
    onScanToggle: () -> Unit, // 더 이상 사용하지 않지만 호환성을 위해 유지
    nearbyPeople: List<NearbyPerson>,
    showPersonListModal: Boolean,
    onShowListToggle: () -> Unit,
    onDismiss: () -> Unit, // 모달 닫기 전용 콜백 추가
    onPersonClick: (userId: String) -> Unit,
    onCallClick: (userId: String) -> Unit,
    rippleStates: Triple<RippleState, RippleState, RippleState>,
    animationValues: AnimationValues,
    buttonText: String,
    subTextVisible: Boolean,
    showListButton: Boolean
) {
    // 리플 애니메이션 상태
    val (ripple1, ripple2, ripple3) = rippleStates
    
    // 리플 파동 효과
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // 전체 Box의 내용을 중앙 정렬
    ) {
        // 주변 랜턴 개수 표시 (상단에 배치 - 패딩값 줄임)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp) // 상단 패딩 줄임 (24dp → 8dp)
                .padding(horizontal = 20.dp)
        ) {
            NearbyLanternCount(count = nearbyPeople.size)
        }
        
        // 첫 번째 리플
        if (ripple1.visible) {
            RippleCircle(
                scale = 1f + ripple1.animationValue * 3.0f,
                alpha = (1f - ripple1.animationValue) * 0.5f,
                color = BleBlue2.copy(alpha = 0.3f)  // 첫 번째 파동은 밝은 시안
            )
        }
        
        // 두 번째 리플
        if (ripple2.visible) {
            RippleCircle(
                scale = 1f + ripple2.animationValue * 3.0f,
                alpha = (1f - ripple2.animationValue) * 0.5f,
                color = BleBlue1.copy(alpha = 0.3f)  // 두 번째 파동은 깊은 파란색
            )
        }

        // 세 번째 리플
        if (ripple3.visible) {
            RippleCircle(
                scale = 1f + ripple3.animationValue * 3.0f,
                alpha = (1f - ripple3.animationValue) * 0.5f,
                color = BleAccent.copy(alpha = 0.2f)  // 세 번째 파동은 네온 민트
            )
        }
        
        // 주변 사람 점 표시
        nearbyPeople.forEach { person ->
            // 중앙 버튼의 크기(160dp)보다 최소 40dp 이상 떨어지도록 설정
            // 160dp/2 + 40dp = 120dp가 최소 반경
            val minRadius = 120.0 // 중앙 버튼과 겹치지 않는 최소 반경
            val maxRadius = 220.0 // 화면 내에 표시되는 최대 반경
            
            // 신호 강도에 따라 거리를 계산
            // 신호 강도가 높을수록 더 가까이 배치
            val distanceRatio = when (person.signalLevel) {
                3 -> 0.1  // 신호가 강함(3) - 가장 가까이 배치
                2 -> 0.4  // 신호가 중간(2) - 중간 거리에 배치
                else -> 0.8  // 신호가 약함(1) - 멀리 배치
            }
            
            val radius = minRadius + (maxRadius - minRadius) * distanceRatio
            
            val angleInRadians = Math.toRadians(person.angle.toDouble())
            val x = radius * cos(angleInRadians)
            val y = radius * sin(angleInRadians)
            
            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(50.dp), // 클릭 가능한 영역 확보 (크기 고정)
                contentAlignment = Alignment.Center
            ) {
                // LanternDot 함수 호출 시 distance 매개변수를 전달
                // 신호 레벨에 따라 거리 값을 계산하여 전달
                val displayDistance = when (person.signalLevel) {
                    3 -> 10f  // 신호가 강함 - 가까운 거리로 표시
                    2 -> 50f  // 신호가 중간 - 중간 거리로 표시
                    else -> 150f  // 신호가 약함 - 먼 거리로 표시
                }
                
                LanternDot(
                    modifier = Modifier,
                    signalStrength = person.signalStrength,
                    pulseScale = animationValues.dotPulseScale,
                    glowAlpha = animationValues.dotGlowAlpha,
                    distance = displayDistance  // 오류가 있던 부분 수정
                )
            }
        }
        
        // 중앙 요소를 배치하는 Box를 추가하여 정확한 중앙 정렬 보장
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 중앙 스캔 버튼
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = 0.dp), // 정확히 중앙에 위치하도록 offset 추가
                contentAlignment = Alignment.Center
            ) {
                // Radar Glow (outer glow) - 아우터 글로우를 먼저 그려서 버튼 아래에 위치하도록 함
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(animationValues.buttonScale)
                        .alpha(animationValues.buttonGlowAlpha * 0.6f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(DeepOrange.copy(alpha = 0.3f), Color.Transparent),
                                radius = 200f * 0.75f
                            )
                        )
                )
                
                // 중앙 레이더 버튼
                LanternCenterButton(
                    buttonScale = animationValues.buttonScale,
                    buttonGlowAlpha = animationValues.buttonGlowAlpha,
                    radarAngle = animationValues.radarAngle
                )
            }
        }
        
        // 하단 정보 영역
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 0.dp),  // 패딩 제거
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)  // 간격을 16dp에서 8dp로 줄임
        ) {
            
            AnimatedVisibility(
                visible = subTextVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {

            }
            // 목록 보기 버튼
            GradientButton(
                text = "주변 사람 목록 보기",
                onClick = {
                    if (nearbyPeople.isNotEmpty()) {
                        onShowListToggle()
                    }
                    // else 블록은 아무 일도 하지 않음
                }
            )
        }
        
        // 사람 목록 모달 - AnimatedVisibility로 감싸서 수정
        AnimatedVisibility(
            visible = showPersonListModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            NearbyPersonListModal(
                people = nearbyPeople,
                onDismiss = onDismiss,
                onPersonClick = onPersonClick,
                onCallClick = onCallClick
            )
        }
    }
}

/**
 * 주변 랜턴 개수를 표시하는 컴포넌트
 */
@Composable
fun NearbyLanternCount(count: Int) {
    Row(
        modifier = Modifier
            .background(
                color = MainScreenCardBg.copy(alpha = 0.8f), // 배경 투명도 증가
                shape = CircleShape
            )
            .padding(horizontal = 20.dp, vertical = 12.dp), // 패딩 증가
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "주변에 ",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium, // 스타일 변경
            fontSize = 18.sp, // 폰트 크기 증가
            fontWeight = FontWeight.Medium // 중간 두께 폰트
        )
        Text(
            text = count.toString(),
            color = LanternYellow,
            style = MaterialTheme.typography.titleMedium, // 스타일 변경
            fontSize = 20.sp, // 더 큰 폰트 크기
            fontWeight = FontWeight.Bold // 굵은 폰트 유지
        )
        Text(
            text = "명의 사람이 있습니다",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium, // 스타일 변경
            fontSize = 18.sp, // 폰트 크기 증가
            fontWeight = FontWeight.Medium // 중간 두께 폰트
        )
    }
}

/**
 * 중앙 랜턴 버튼 (클릭 기능 없음)
 */
@Composable
fun LanternCenterButton(
    buttonScale: Float,
    buttonGlowAlpha: Float,
    radarAngle: Float
) {
    val baseColor = LanternYellow
    val darkColor = LanternYellowDark
    val highlightColor = DeepOrange // Color(0xFFE65100) -> DeepOrange
    
    // MaterialTheme 값을 블록 외부에서 변수로 미리 가져옴
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    
    // 블루투스 펄스 애니메이션
    val bluetoothPulse = rememberInfiniteTransition(label = "bluetoothPulse")
    val bluetoothAlpha by bluetoothPulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing)
        ),
        label = "bluetoothAlpha"
    )
    
    // 블루투스 스케일 애니메이션
    val bluetoothScale by bluetoothPulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing)
        ),
        label = "bluetoothScale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, darkColor),
                    radius = 150f
                )
            )
            .border(2.dp, BleDarkBlue, CircleShape) // BleDarkBlue is from Color.kt
            .padding(8.dp)
            .drawBehind {
                // 레이더 스캔 라인 (회전하는 효과)
                for (i in 1..3) {
                    val radius = size.width * i / 8
                    drawCircle(
                        color = RadarLineColor, // 테마 색상 사용
                        radius = radius,
                        style = Stroke(width = 2.0f) // 선 두께 조금 줄임
                    )
                }
                
                // 십자선 (고정)
                drawLine(
                    color = RadarLineColor, // 테마 색상 사용
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 2.0f // 선 두께 조금 줄임
                )
                drawLine(
                    color = RadarLineColor, // 테마 색상 사용
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 2.0f // 선 두께 조금 줄임
                )
                
                // 레이더 스캔 부채꼴 (회전하는 영역) - 선 대신 부채꼴 영역으로 변경
                val sweepAngle = 60f // 부채꼴 각도
                val startAngle = radarAngle - sweepAngle / 2
                
                // 레이더 부채꼴 - 투명한 영역
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RadarGradientStart, // 테마 색상 사용 - 더 투명한 흰색
                            RadarGradientMiddle, // 테마 색상 사용 - 더 투명한 흰색
                            RadarGradientEnd // 테마 색상 사용 - 완전 투명
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = size
                )
                
                // 레이더 부채꼴 테두리
                drawArc(
                    color = RadarEdgeColor, // 테마 색상 사용 - 테두리 투명도 높임
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    style = Stroke(width = 1.5f), // 테두리 두께 조금 줄임
                    size = size
                )
                
                // 부채꼴 앞쪽 에지에 밝은 선 추가
                val angleRadians = Math.toRadians(radarAngle.toDouble())
                val endX = center.x + (size.width / 2) * cos(angleRadians).toFloat()
                val endY = center.y + (size.height / 2) * sin(angleRadians).toFloat()
                
                // 선명한 에지 선
                drawLine(
                    color = Color.White.copy(alpha = 0.7f), // 에지는 조금 선명하게 유지
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.0f, // 선 두께 조금 줄임
                    cap = StrokeCap.Round
                )
            },
        contentAlignment = Alignment.Center // 중앙 정렬 추가
    ) {
        // 블루투스 아이콘 추가
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = "블루투스 연결",
            tint = BluetoothColor.copy(alpha = bluetoothAlpha), // 테마 색상 사용
            modifier = Modifier
                .size(45.dp) // 아이콘 크기 유지
                .scale(bluetoothScale) // 스케일 애니메이션 유지
                .shadow(elevation = 10.dp, spotColor = BluetoothGlowColor, shape = CircleShape) // 테마 색상 사용
        )
    }
    
    // Radar Glow (outer glow) 부분은 중앙 배치를 위해 상위 Box로 이동시킴
}

/**
 * 리플 원형 효과
 */
@Composable
fun RippleCircle(
    scale: Float,
    alpha: Float,
    color: Color
) {
    // 랜턴 빛이 퍼져나가는 느낌의 파동 - 흰색 계열로 변경
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .alpha(alpha)
            .drawBehind {
                // 랜턴 빛 효과를 내기 위한 그라데이션 원 (흰색 기반)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f), // 흰색으로 변경
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f
                )
                
                // 테두리 - 흰색 계열로 변경하고 더 두껍게
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f), // 흰색으로 변경
                    radius = size.width * 0.5f,
                    style = Stroke(width = 4.0f) // 더 두껍게
                )
            }
    )
}

/**
 * 그라데이션 버튼 - 노란색 계열(랜턴색)으로 변경 및 크기 유지
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit
) {
    // 랜턴 색상 정의 - 노란색 계열로 변경
    val lanternColor = Color(0xFFFFC107) // 밝은 노란색
    val lanternColorDark = Color(0xFFFF9800) // 어두운 노란색

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f) // 너비 유지
            .height(56.dp) // 고정 높이 유지
            .clip(CircleShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        lanternColor, // 밝은 노란색
                        lanternColorDark // 어두운 노란색
                    )
                )
            )
            .border(
                width = 2.dp, // 테두리 두께 유지
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f), // 테두리도 밝게
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        Text(
            text = text,
            color = Color.White, // 검정색에서 흰색으로 변경
            fontSize = 17.sp, // 폰트 크기 유지
            fontWeight = FontWeight.Bold, // 폰트 두께 증가
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp) // 패딩 유지
        )
    }
} 