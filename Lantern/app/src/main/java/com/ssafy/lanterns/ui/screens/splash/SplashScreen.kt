package com.ssafy.lanterns.ui.screens.splash

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ssafy.lanterns.ui.theme.NavyBottom // 다크 네이비 배경색
import com.ssafy.lanterns.ui.theme.TextWhite // 텍스트 및 글로우 색상
import com.ssafy.lanterns.utils.PreferenceUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    // 로그인 상태를 반환하는 콜백으로 변경
    onTimeout: (isLoggedIn: Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val preferenceUtil = remember { PreferenceUtil(context) }

    // 애니메이션 상태 변수들
    val glowAlpha = remember { Animatable(0f) } // 중앙 점광원 밝기 (0.0f to 0.4f)
    val textAlpha = remember { Animatable(0f) } // "LANTERN" 텍스트 투명도
    val textScale = remember { Animatable(0.8f) } // "LANTERN" 텍스트 크기, 초기값 0.8f
    val rippleGlowAlpha = remember { Animatable(0f) } // 텍스트 주변 파동 투명도 (0.0f to 0.2f)
    val rippleGlowRadiusFactor = remember { Animatable(0f) } // 파동 반경 계수 (0에서 1로)

    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textSizePx = remember(textLayoutResultState) {
        textLayoutResultState?.size ?: IntSize.Zero
    }

    // 시스템 UI 전체 화면 설정
    LaunchedEffect(Unit) {
        (context as? Activity)?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
        }
    }

    // 전체 애니메이션 시퀀스
    LaunchedEffect(Unit) {
        // 1. 중앙 점광원 애니메이션 (총 800ms)
        // 0 -> 0.4 (200ms), 유지 (400ms), 0.4 -> 0 (200ms)
        launch {
            glowAlpha.animateTo(0.4f, tween(durationMillis = 200, easing = LinearEasing))
            delay(400) // 40% 밝기 유지 (200ms ~ 600ms 구간)
            glowAlpha.animateTo(0f, tween(durationMillis = 200, easing = LinearEasing)) // 600ms ~ 800ms 구간
        }

        // 2. "LANTERN" 텍스트 등장 애니메이션 (점광원 최고 밝기 시점에 시작, 총 600ms)
        // 스플래시 시작 후 200ms 지점부터 800ms 지점까지 (총 600ms)
        launch {
            delay(200) // 점광원 최고 밝기 도달 시점 (스플래시 시작 후 200ms)

            // 페이드 인 (텍스트 애니메이션 시작부터 300ms 소요)
            // 즉, 스플래시 시간 200ms ~ 500ms 구간
            textAlpha.animateTo(1f, tween(durationMillis = 300, easing = LinearEasing))

            // 스케일 업 (Bounce) 0.8 -> 1.1 (다음 300ms) -> 1.0 (마지막 300ms)
            // 총 600ms 소요
            // textScale 초기값 0.8f
            // 200ms ~ 500ms 구간 : 0.8f -> 1.1f
            textScale.animateTo(1.1f, tween(durationMillis = 300, easing = LinearOutSlowInEasing))
            // 500ms ~ 800ms 구간 : 1.1f -> 1.0f
            textScale.animateTo(1.0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        }

        // 3. 텍스트 주변 글로우 림 (Pulse Ripple) 애니메이션
        // 텍스트 등장 애니메이션 끝난 후 (800ms 지점) 시작, 총 800ms 소요 (800ms ~ 1600ms)
        launch {
            delay(800) // 텍스트 애니메이션 종료 시점

            // 파동 반경 애니메이션 (0 -> 1), 800ms 소요
            rippleGlowRadiusFactor.animateTo(1f, tween(durationMillis = 800, easing = LinearEasing))
        }
        launch {
            delay(800) // 텍스트 애니메이션 종료 시점

            // 파동 알파 애니메이션 (0 -> 0.2 -> 0), 총 800ms 소요
            // 0 -> 0.2 (400ms)
            rippleGlowAlpha.animateTo(0.2f, tween(durationMillis = 400, easing = LinearEasing))
            // 0.2 -> 0 (다음 400ms)
            rippleGlowAlpha.animateTo(0f, tween(durationMillis = 400, easing = LinearEasing))
        }

        // 4. 전체 스플래시 종료 및 화면 전환
        // 가장 긴 애니메이션은 글로우 림이므로 1600ms 후 종료.
        delay(1600)
        
        // 로그인 상태 확인 후 콜백 호출
        val token = preferenceUtil.getString("token", "")
        val isLoggedIn = token.isNotEmpty()
        onTimeout(isLoggedIn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBottom) // 다크 네이비 배경
            .systemBarsPadding(), // 상태바/네비게이션바 영역 확보
        contentAlignment = Alignment.Center
    ) {
        // 1. 중앙 점광원 (Glow Focus)
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (glowAlpha.value > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = glowAlpha.value * 0.5f), Color.Transparent), // 중앙은 약간 밝게, 주변은 투명
                        center = center,
                        radius = size.minDimension / 3.0f // 점광원 크기 (조절 가능)
                    ),
                    radius = size.minDimension / 3.0f
                )
            }
        }

        // 3. 텍스트 주변 글로우 림 (Pulse Ripple) - 텍스트보다 뒤에 그려지도록 순서 조정
        // Box 내에서 텍스트보다 먼저 선언되면 뒤에 그려짐
        if (textSizePx != IntSize.Zero && rippleGlowRadiusFactor.value > 0f && rippleGlowAlpha.value > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) { // Text와 동일한 중앙 정렬 사용
                val rippleActualRadius = with(density) { (textSizePx.height.toFloat() * 2.0f) * rippleGlowRadiusFactor.value }
                if (rippleActualRadius > 0f) {
                    drawCircle(
                        color = TextWhite.copy(alpha = rippleGlowAlpha.value), // 파동 색상 및 투명도 (최대 20%)
                        radius = rippleActualRadius,
                        center = center, // Box의 중앙 사용
                        style = Stroke(width = 1.5.dp.toPx()) // 얇은 파동 두께 (조절 가능)
                    )
                }
            }
        }

        // 2. "LANTERN" 텍스트
        Text(
            text = "LANTERN",
            color = TextWhite.copy(alpha = textAlpha.value), // TextWhite를 기본으로 사용하고 알파값 적용
            fontSize = 48.sp, // 폰트 크기 (조절 가능)
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer { // 스케일 적용 (알파는 color에서 처리)
                    scaleX = textScale.value
                    scaleY = textScale.value
                },
            onTextLayout = { layoutResult ->
                if (textLayoutResultState == null) { // 초기 한 번만 설정하여 불필요한 재계산 방지
                    textLayoutResultState = layoutResult
                }
            }
        )
    }
}