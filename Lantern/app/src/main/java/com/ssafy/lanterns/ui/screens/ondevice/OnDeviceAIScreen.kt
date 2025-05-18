package com.ssafy.lanterns.ui.screens.ondevice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.theme.LanternCoreYellow
import com.ssafy.lanterns.ui.theme.LanternGlowOrange
import com.ssafy.lanterns.ui.theme.LanternParticleColor
import com.ssafy.lanterns.ui.theme.LanternShadeDark
import com.ssafy.lanterns.ui.theme.LanternWarmWhite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Perlin Noise 구현 (이전에 사용했던 것과 유사)
class SimplePerlinNoise {
    private val permutation = IntArray(512)

    init {
        val p = IntArray(256) { it }
        p.shuffle(Random(System.currentTimeMillis()))
        for (i in 0 until 512) {
            permutation[i] = p[i and 255]
        }
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    private fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return ((h and 1).toFloat() * 2 - 1) * u + ((h and 2).toFloat() - 1) * v
    }

    fun noise2D(x: Float, y: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]

        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)

        return (lerp(x1, x2, v) + 1) / 2 // 결과 범위를 0.0 ~ 1.0으로 조정
    }
}

@Composable
fun VoiceModal(
    aiState: AiState,
    statusMessage: String,
    onDismiss: () -> Unit
) {
    // 닫기 버튼 가시성 (일부 상태에서는 닫기 버튼이 보이지 않음)
    val closeButtonVisible = aiState != AiState.PROCESSING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // 클릭 시 시각적 효과 제거
            ) { if (closeButtonVisible) onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // 백그라운드 오버레이 - 더 어둡게 조정
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LanternShadeDark.copy(alpha = 0.9f)) // 불투명도 증가
        )

        // 메인 콘텐츠 영역 - 카드 대신 전체 화면 레이아웃 사용
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // 상단 닫기 버튼
            if (closeButtonVisible) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 중앙 랜턴 효과
            LanternOrbEffect(
                aiState = aiState,
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.Center)
            )
            
            // 음성 인식 파동 효과 (LISTENING 상태일 때)
            if (aiState == AiState.LISTENING) {
                VoiceWaveEffect(
                    modifier = Modifier
                        .size(320.dp)
                        .align(Alignment.Center)
                )
            }

            // 상태 메시지 텍스트
            if (statusMessage.isNotEmpty()) {
                DynamicStatusText(
                    text = statusMessage,
                    aiState = aiState,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 160.dp) // 텍스트를 오브 아래에 배치
                )
            }
        }
    }
}

data class StarData(val offset: Offset, val radius: Float, val initialAlpha: Float)
data class NodeConnection(val startNodeIndex: Int, val endNodeIndex: Int)

@Composable
private fun VoiceWaveEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceWaveTransition")
    
    val waveProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing), // 속도 약간 느리게
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveProgress"
    ).value
    
    val rotationAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing), // 회전 속도 더 느리게
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    ).value
    
    val numWaves = 7 // 파형 수 더 감소 (10 -> 7)
    
    Canvas(modifier = modifier) {
        val center = center
        val minDimension = size.minDimension / 2f * 0.9f // 웨이브 전체 크기 약간 줄임
        
        // 정적 파형 (더 은은하게)
        for (i in 0 until numWaves) {
            val normalizedIndex = i / numWaves.toFloat()
            // val phase = normalizedIndex * 2 * PI.toFloat() + waveProgress * 2 * PI.toFloat()
            
            val radiusFactor = 1f - normalizedIndex * 0.6f // 반지름 감소폭 줄여서 덜 촘촘하게
            val radius = minDimension * radiusFactor * (0.8f + 0.2f * waveProgress) // waveProgress에 따라 전체적으로 커졌다 작아졌다
            
            val alpha = (1f - normalizedIndex) * 0.1f * (0.5f + 0.5f * waveProgress) // 알파값 더 낮춤, waveProgress 영향 추가
            
            drawCircle(
                color = Color.White.copy(alpha = alpha.coerceIn(0.02f, 0.1f)), // 최소/최대 알파 제한
                radius = radius,
                center = center,
                style = Stroke(
                    width = (0.8.dp + (0.7.dp * (1f - normalizedIndex))).toPx() // 선 두께 약간 더 얇게
                )
            )
        }
        
        // 회전하는 파형 (더 단순하고 부드럽게)
        val rotationRad = rotationAngle * (PI / 180f).toFloat()
        val numRotatingWaves = 4 // 회전 파형 수 감소 (6 -> 4)
        
        for (i in 0 until numRotatingWaves) {
            val angleStep = 360f / numRotatingWaves
            val currentAngle = rotationRad + angleStep * i * (PI / 180f).toFloat()
            
            // 파동의 진폭과 반지름을 waveProgress에 따라 변화시켜 동적인 느낌 강화
            val waveAmplitude = minDimension * 0.1f * (0.3f + 0.7f * sin(waveProgress * PI.toFloat() * 2f + i * PI.toFloat() / 2f))
            val baseRadius = minDimension * 0.75f
            
            val x1 = center.x + cos(currentAngle) * (baseRadius - waveAmplitude)
            val y1 = center.y + sin(currentAngle) * (baseRadius - waveAmplitude)
            val x2 = center.x + cos(currentAngle) * (baseRadius + waveAmplitude)
            val y2 = center.y + sin(currentAngle) * (baseRadius + waveAmplitude)
            
            val alpha = (0.1f - 0.05f * abs(cos(waveProgress * PI.toFloat() * 2f + i * PI.toFloat() / 2f))) * 0.8f
            
            drawLine(
                color = Color.White.copy(alpha = alpha.coerceIn(0.01f, 0.08f)),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 0.8.dp.toPx()
            )
        }
    }
}

@Composable
private fun LanternOrbEffect(
    modifier: Modifier = Modifier,
    aiState: AiState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")
    
    val pulseProgress = infiniteTransition.animateFloat(
        initialValue = 0.95f, // 펄스 범위 약간 줄임
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing), // 속도 약간 느리게
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAnimation"
    ).value
    
    val rotationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing), // 회전 속도 더 느리게
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAnimation"
    ).value
    
    // 디자인 수정: 기본 오브 색상 및 투명도 (차분하게)
    val baseOrbColor = when (aiState) {
        AiState.LISTENING -> LanternWarmWhite.copy(alpha = 0.6f)
        AiState.COMMAND_RECOGNIZED -> LanternGlowOrange.copy(alpha = 0.7f)
        AiState.SPEAKING -> LanternCoreYellow.copy(alpha = 0.7f)
        AiState.PROCESSING -> LanternWarmWhite.copy(alpha = 0.5f)
        else -> LanternWarmWhite.copy(alpha = 0.4f)
    }
    
    val orbColor = baseOrbColor.copy(alpha = baseOrbColor.alpha * pulseProgress) 

    val glowColor = orbColor.copy(alpha = orbColor.alpha * 0.4f) // 글로우 강도 줄임
    
    val particleCount = 15 // 파티클 수 더 감소 (20 -> 15)
    
    Canvas(modifier = modifier) {
        val center = center
        val radius = size.minDimension / 2f * 0.55f // 오브 크기 약간 줄임
        
        // 메인 orb 그리기
        drawCircle(
            color = orbColor,
            radius = radius * pulseProgress,
            center = center
        )
        
        // glow 효과 그리기
        drawCircle(
            color = glowColor,
            radius = radius * (1.1f + 0.2f * pulseProgress), // 글로우 범위 약간 줄임
            center = center
        )
        
        if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED || aiState == AiState.PROCESSING) {
            for (i in 0 until particleCount) {
                val angleRad = ((rotationProgress * (if (i % 2 == 0) 1f else -0.8f) + i * 360f / particleCount) % 360f) * (PI / 180f).toFloat()
                val distance = radius * (1.2f + 0.15f * sin(i * 0.5f + pulseProgress * PI.toFloat()))
                
                val particleX = center.x + distance * cos(angleRad)
                val particleY = center.y + distance * sin(angleRad)
                
                val particleAlpha = 0.5f * (0.4f + 0.6f * sin(i * 0.3f + pulseProgress * PI.toFloat()))
                // 파티클 색상 단일화 또는 매우 유사하게
                val particleColorVariation = Color.White.copy(alpha = particleAlpha.coerceIn(0.1f, 0.5f))
                
                val particleRadius = 0.8.dp.toPx() * (1f + (i % 2) * 0.5f) // 크기 단순화 및 축소
                
                drawCircle(
                    color = particleColorVariation,
                    radius = particleRadius,
                    center = Offset(particleX, particleY)
                )
            }
        }
    }
}

@Composable
fun DynamicStatusText(
    text: String,
    aiState: AiState,
    modifier: Modifier = Modifier
) {
    // 시간에 따라 변화하는 효과를 위한 무한 트랜지션
    val infiniteTransition = rememberInfiniteTransition(label = "TextEffectTransition")
    
    // 텍스트 색상 애니메이션 (더 화려한 색상 효과)
    val animatedTextColor by animateColorAsState(
        targetValue = when (aiState) {
            AiState.LISTENING -> LanternWarmWhite
            AiState.COMMAND_RECOGNIZED -> LanternGlowOrange 
            AiState.PROCESSING -> Color(0xFF64B5F6) // 가벼운 블루
            AiState.SPEAKING -> LanternCoreYellow
            AiState.ERROR -> Color(0xFFE57373) // 가벼운 레드
            else -> Color.White
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "Text Color Animation"
    )
    
    // 텍스트 주변 글로우 효과 (색상 펄스 효과)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED) 0.3f else 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlphaAnimation"
    )

    // 텍스트 상태에 따른 애니메이션 효과
    val scale by animateFloatAsState(
        targetValue = if (aiState == AiState.SPEAKING) 1.08f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "TextScale"
    )
    
    // 텍스트 위아래로 움직이는 애니메이션
    val floatOffset by animateFloatAsState(
        targetValue = when (aiState) {
            AiState.LISTENING -> -4f
            AiState.SPEAKING -> 4f
            else -> 0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatingAnimation"
    )
    
    // 텍스트 깜빡임 애니메이션 (처리중일 때만)
    val alpha by animateFloatAsState(
        targetValue = if (aiState == AiState.PROCESSING) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkingAnimation"
    )
    
    // SPEAKING 상태의 추가 효과 - 글자가 펄스처럼 움직이는 효과
    val letterSpacingMultiplier by animateFloatAsState(
        targetValue = if (aiState == AiState.SPEAKING) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LetterSpacingAnimation"
    )
    
    // 텍스트 수평 흔들림 애니메이션 (ERROR 상태일 때)
    val horizontalShake by animateFloatAsState(
        targetValue = if (aiState == AiState.ERROR) 2f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShakeAnimation"
    )
    
    // 시리 스타일의 텍스트 스타일
    val baseTextStyle = TextStyle(
        fontSize = 28.sp, // 더 크게
        fontWeight = FontWeight.Medium, // 약간 더 두껍게
        fontFamily = FontFamily.SansSerif,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center,
        letterSpacing = if (aiState == AiState.SPEAKING) 0.2.sp * letterSpacingMultiplier else 0.2.sp // 말할 때 글자 간격 변화
    )

    // 간결한 텍스트 표시 로직
    val displayText = when (aiState) {
        AiState.LISTENING -> "무엇을 도와드릴까요?"
        AiState.COMMAND_RECOGNIZED -> "명령 인식"
        AiState.PROCESSING -> "처리 중..."
        AiState.SPEAKING -> text
        AiState.ERROR -> "다시 말씀해주세요"
        else -> text
    }
    
    // 박스 컨테이너 - TexStatusGlowEffect와 TextContentWithShadow 분리
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 글로우 효과
        TextStatusGlowEffect(aiState, animatedTextColor, glowAlpha)
        
        // 텍스트 내용
        TextContentWithShadow(
            displayText = displayText,
            baseTextStyle = baseTextStyle,
            animatedTextColor = animatedTextColor,
            aiState = aiState,
            alpha = alpha,
            scale = scale,
            floatOffset = floatOffset,
            horizontalShake = horizontalShake
        )
    }
}

@Composable
private fun TextStatusGlowEffect(
    aiState: AiState,
    animatedTextColor: Color,
    glowAlpha: Float
) {
    // 특별 효과를 위한 캔버스 (전체 화면 영역)
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 말하는 상태 또는 명령 인식 상태에서 특별한 배경 효과
        if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedTextColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    radius = this.size.minDimension * 0.4f
                ),
                radius = this.size.minDimension * 0.4f,
                center = center.copy(y = center.y + size.height * 0.1f) // 글자 아래에 위치하도록 조정
            )
        }
    }
}

// 텍스트 내용과 그림자를 표시하는 컴포넌트
@Composable
private fun TextContentWithShadow(
    displayText: String,
    baseTextStyle: TextStyle,
    animatedTextColor: Color,
    aiState: AiState,
    alpha: Float,
    scale: Float,
    floatOffset: Float,
    horizontalShake: Float
) {
    if (displayText.isNotEmpty()) {
        // 그림자 효과 (오프셋된 텍스트로 구현)
        Text(
            text = displayText,
            style = baseTextStyle,
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier
                .offset(
                    x = (2.dp + if (aiState == AiState.ERROR) horizontalShake.dp else 0.dp), 
                    y = 2.dp + floatOffset.dp
                )
                .alpha(alpha)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        
        // 메인 텍스트
        Text(
            text = displayText,
            style = baseTextStyle,
            color = animatedTextColor,
            modifier = Modifier
                .offset(
                    x = if (aiState == AiState.ERROR) horizontalShake.dp else 0.dp,
                    y = floatOffset.dp
                )
                .alpha(alpha)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    
                    // 추가 효과 (타이핑 애니메이션이나 다른 특별 효과를 추가할 수 있음)
                    if (aiState == AiState.COMMAND_RECOGNIZED) {
                        rotationX = 2f * sin(System.currentTimeMillis() / 300f)
                    }
                }
        )
        
        // SPEAKING 상태에서 추가 글로우 효과
        if (aiState == AiState.SPEAKING) {
            // 시간에 따른 글로우 알파값 계산
            val currentTime = System.currentTimeMillis()
            val glowFactor = (1f + sin(currentTime / 500f)) / 2f  // 0.0 ~ 1.0
            
            Text(
                text = displayText,
                style = baseTextStyle,
                color = animatedTextColor.copy(alpha = 0.3f * glowFactor),
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .graphicsLayer(
                        scaleX = scale * 1.05f,
                        scaleY = scale * 1.05f,
                        alpha = 0.7f // alpha 값을 인자로 전달
                    )
            )
        }
    }
}

@Composable
fun OnDeviceAIScreen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    viewModel: OnDeviceAIViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = view.context as? Activity
    val window = activity?.window

    DisposableEffect(key1 = window) { 
        if (window == null) return@DisposableEffect onDispose {}

        val controller = WindowCompat.getInsetsController(window, view)
        
        // Dialog Window 자체를 Edge-to-Edge로 설정하기 위한 준비
        // DialogProperties에서 decorFitsSystemWindows = false로 설정하는 것이 더 적절할 수 있음
        // 여기서는 Dialog의 Window를 직접 제어한다고 가정
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        controller.isAppearanceLightStatusBars = false // AI 화면은 어두운 배경에 밝은 글씨로 가정
        controller.isAppearanceLightNavigationBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // 화면 계속 켜기 (Dialog가 떠 있는 동안)
        // 이 로직은 Dialog Window가 아닌 Activity의 Window에 적용되어야 할 수도 있음.
        // DialogProperties로 제어할 수 없다면, Dialog 표시/숨김 시 Activity 레벨에서 관리 필요.
        // 현재 코드는 Dialog 자체 Window에 적용 시도.
        val originalKeepScreenOn = window.attributes.flags.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (!originalKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            // Dialog가 닫히면 Dialog Window는 OS에 의해 정리됨.
            // setDecorFitsSystemWindows(true) 등을 여기서 호출할 필요는 일반적으로 없음.
            // Activity의 Window는 Dialog 생성/소멸에 영향을 받지 않아야 함.
            
            // 화면 계속 켜기 플래그만 원래대로 (Dialog Window에 설정된 경우)
            if (window.attributes.flags.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0 && !originalKeepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.activateAI()
        } else {
            viewModel.showErrorAndPrepareToClose("음성 인식을 위해 마이크 권한이 필요합니다") // 수정된 코드
            Toast.makeText(context, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()

        }
    }

    val previousAiState = remember { mutableStateOf(uiState.currentAiState) }
    
    LaunchedEffect(key1 = Unit) {
        checkPermissionAndActivateAI(context, viewModel, permissionLauncher)
    }

    LaunchedEffect(uiState.currentAiState) {
        Log.d("OnDeviceAIScreen", "LaunchedEffect 실행. 현재 상태: ${uiState.currentAiState}, 이전 상태: ${previousAiState.value}")
        // 이전 상태가 LISTENING 또는 다른 활성 상태였고, 현재 상태가 IDLE로 "변경"되었을 때만 onDismiss 호출
        if (previousAiState.value != AiState.IDLE && uiState.currentAiState == AiState.IDLE) {
            Log.d("OnDeviceAIScreen", "AI 상태가 IDLE로 '변경'됨. 화면 닫기 요청. 이전 상태: ${previousAiState.value}")
            onDismiss()
        }
        // 현재 상태를 이전 상태로 업데이트
        previousAiState.value = uiState.currentAiState
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundTransition")
    val time = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f, 
        animationSpec = infiniteRepeatable(
            animation = tween(1000 * 200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimeAnimation"
    ).value
    
    val backgroundColor by animateColorAsState(
        targetValue = when (uiState.currentAiState) {
            AiState.LISTENING -> Color(0xFF0A192F) // 더 어둡고 차분한 네이비
            AiState.COMMAND_RECOGNIZED -> Color(0xFF2C1D00) // 어두운 주황 계열
            AiState.PROCESSING -> Color(0xFF001B2E) // 차분한 다크 블루
            AiState.SPEAKING -> Color(0xFF1E1A00) // 어두운 골드 계열
            AiState.ERROR -> Color(0xFF3B0000) // 어두운 레드
            else -> Color(0xFF010A13) // 매우 어두운 기본색
        },
        animationSpec = tween(800), // 색상 변경 속도 약간 느리게
        label = "BackgroundColorAnimation"
    )
    
    val gradientColor by animateColorAsState(
        targetValue = when (uiState.currentAiState) {
            AiState.LISTENING -> Color(0xFF173A5E) // 네이비 계열 그라데이션
            AiState.COMMAND_RECOGNIZED -> Color(0xFF6F4200) // 주황 계열 그라데이션
            AiState.PROCESSING -> Color(0xFF003052) // 다크 블루 그라데이션
            AiState.SPEAKING -> Color(0xFF4A3F00) // 골드 계열 그라데이션
            AiState.ERROR -> Color(0xFF6B0000) // 레드 계열 그라데이션
            else -> Color(0xFF0A192F) // 어두운 기본 그라데이션
        },
        animationSpec = tween(800),
        label = "GradientColorAnimation"
    )
    
    // 디자인 수정: 파티클 밀도 및 속성 조정 (덜 동화같도록)
    val (particleDensity, particleSpeed, particleSize) = when (uiState.currentAiState) {
        AiState.LISTENING -> Triple(25, 0.005f, 2.0f) // 밀도 감소, 크기 및 속도 조정
        AiState.COMMAND_RECOGNIZED -> Triple(35, 0.008f, 2.5f) 
        AiState.PROCESSING -> Triple(30, 0.006f, 1.8f) 
        AiState.SPEAKING -> Triple(30, 0.006f, 2.2f) 
        else -> Triple(20, 0.004f, 1.8f)
    }
    
    // 디자인 수정: 파티클 색상 조정
    val primaryParticleColor = when (uiState.currentAiState) {
        AiState.LISTENING -> Color.White.copy(alpha = 0.4f)
        AiState.COMMAND_RECOGNIZED -> LanternGlowOrange.copy(alpha = 0.5f)
        AiState.SPEAKING -> LanternCoreYellow.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.3f)
    }
    
    val secondaryParticleColor = when (uiState.currentAiState) {
        AiState.COMMAND_RECOGNIZED -> LanternCoreYellow.copy(alpha = 0.3f)
        AiState.SPEAKING -> LanternWarmWhite.copy(alpha = 0.3f)
        else -> LanternGlowOrange.copy(alpha = 0.2f)
    }

    // Siri 스타일 전체 화면 적용: 최상위 Box의 systemBarsPadding 제거
    Box(
        modifier = modifier // modifier를 외부에서 받을 수 있도록 수정
            .fillMaxSize()
            // 배경색은 내부 BackgroundEffects에서 처리하므로 여기서는 제거하거나 투명으로 설정
            .background(Color.Transparent) // 또는 Color.Black.copy(alpha = 0.6f) 유지 가능
        ,
        contentAlignment = Alignment.Center
    ) {
        BackgroundEffects(
            time = time,
            backgroundColor = backgroundColor, // 알파값은 BackgroundEffects 내부에서 조정
            gradientColor = gradientColor,
            aiState = uiState.currentAiState,
            particleDensity = particleDensity, 
            particleSpeed = particleSpeed,
            particleSize = particleSize,
            primaryParticleColor = primaryParticleColor,
            secondaryParticleColor = secondaryParticleColor
        )
        
        // 닫기 버튼: statusBarsPadding() 추가하여 상태바 영역 피하도록 함
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding() // 상태바 영역 패딩
                .padding(top = 4.dp, end = 16.dp) // 상단 패딩 수정 (8.dp -> 4.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape) // 배경 약간 더 투명하게
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.currentAiState == AiState.LISTENING) {
                VoiceWaveEffect(
                    modifier = Modifier.fillMaxSize(0.85f) // 크기 약간 조정
                )
            }
            LanternOrbEffect(
                modifier = Modifier.fillMaxSize(0.65f), // 크기 약간 조정
                aiState = uiState.currentAiState
            )
        }
        
        val statusMessage = when (uiState.currentAiState) {
            AiState.LISTENING -> uiState.listeningMessage
            AiState.COMMAND_RECOGNIZED -> uiState.commandRecognizedMessage
            AiState.PROCESSING -> uiState.processingMessage
            AiState.SPEAKING -> uiState.responseMessage
            AiState.ERROR -> uiState.errorMessage
            else -> ""
        }
        
        // 하단 상태 메시지: navigationBarsPadding().imePadding() 추가
        DynamicStatusText(
            text = statusMessage,
            aiState = uiState.currentAiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // 네비게이션 바 영역 패딩
                .imePadding() // 키보드 올라올 때 패딩
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp) // 추가적인 내부 패딩
        )
    }
}

@Composable
private fun BackgroundEffects(
    time: Float,
    backgroundColor: Color,
    gradientColor: Color,
    aiState: AiState,
    particleDensity: Int,
    particleSpeed: Float,
    particleSize: Float,
    primaryParticleColor: Color,
    secondaryParticleColor: Color
) {
    val perlinNoise = remember(aiState) { SimplePerlinNoise() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 그라데이션 배경 (알파값 여기서 최종 결정 - 반투명 효과)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    gradientColor.copy(alpha = 0.5f), // 더 반투명 효과 (기존 0.6f)
                    backgroundColor.copy(alpha = 0.7f)  // 더 반투명 효과 (기존 0.8f)
                ),
                center = center,
                radius = size.maxDimension * 1.2f // 그라데이션 범위 확장
            ),
            size = size
        )
        
        drawParticleEffects(
            aiState = aiState,
            time = time,
            particleDensity = particleDensity,
            particleSpeed = particleSpeed,
            particleSize = particleSize,
            primaryParticleColor = primaryParticleColor,
            secondaryParticleColor = secondaryParticleColor,
            perlinNoise = perlinNoise
        )
    }
}

private fun DrawScope.drawParticleEffects(
    aiState: AiState,
    time: Float,
    particleDensity: Int,
    particleSpeed: Float,
    particleSize: Float,
    primaryParticleColor: Color,
    secondaryParticleColor: Color,
    perlinNoise: SimplePerlinNoise
) {
    val numParticles = particleDensity
    
    for (i in 0 until numParticles) {
        // 화면 가장자리까지 파티클이 분포하도록 수정 (0.0 ~ 1.0 범위 사용)
        val particleX = size.width * ((sin(i * 0.05f + time * particleSpeed * 0.7f + i * 0.1f) + 1f) / 2f)
        val particleY = size.height * ((cos(i * 0.05f + time * particleSpeed * 0.5f + i * 0.15f) + 1f) / 2f)
        
        val noiseValue = if (i % 4 == 0) { 
            perlinNoise.noise2D(time * 0.003f + i * 0.02f, i * 0.1f)
        } else {
            0.6f 
        }
        
        val sizeFactor = (particleSize - 0.5f) * noiseValue + 0.5f // 크기 변화폭 줄임
        val particleSizePx = sizeFactor * 0.8.dp.toPx() // 전체적인 크기 감소
        
        // 밝기 변화 거의 없도록 수정 (은은하게)
        val brightness = when (aiState) {
            AiState.COMMAND_RECOGNIZED -> 0.8f
            AiState.SPEAKING -> 0.7f
            else -> 0.6f
        }
        
        val particleColor = when {
            i % 6 == 0 -> primaryParticleColor.copy(alpha = primaryParticleColor.alpha * brightness * 0.8f)
            i % 4 == 0 -> secondaryParticleColor.copy(alpha = secondaryParticleColor.alpha * brightness * 0.7f)
            else -> Color.White.copy(alpha = 0.2f * brightness) // 기본 파티클은 더 어둡게
        }
        
        drawCircle(
            color = particleColor,
            radius = particleSizePx,
            center = Offset(particleX, particleY)
        )
        
        // 특수 효과 (꼬리, 글로우)는 더욱 단순화하거나, 빈도 줄임
        if (aiState == AiState.COMMAND_RECOGNIZED && i % 15 == 0) { // 빈도 더 줄임
            drawParticleTrail(
                particleX = particleX,
                particleY = particleY,
                particleSizePx = particleSizePx * 0.8f, // 꼬리 크기 줄임
                particleColor = particleColor.copy(alpha = particleColor.alpha * 0.5f), // 꼬리 더 투명하게
                particleSpeed = particleSpeed * 0.8f,
                time = time,
                index = i,
                perlinNoise = perlinNoise
            )
        }
        
        if (aiState == AiState.SPEAKING && i % 20 == 0) { // 빈도 더 줄임
            drawCircle(
                color = particleColor.copy(alpha = particleColor.alpha * 0.15f), // 글로우 더 약하게
                radius = particleSizePx * 2f, 
                center = Offset(particleX, particleY)
            )
        }
    }
}

private fun DrawScope.drawParticleTrail(
    particleX: Float,
    particleY: Float,
    particleSizePx: Float,
    particleColor: Color,
    particleSpeed: Float,
    time: Float,
    index: Int,
    perlinNoise: SimplePerlinNoise
) {
    val trailPoints = 3 // 꼬리 포인트 수 유지 (필요시 조정)
    for (t in 0 until trailPoints) {
        // 꼬리 위치 계산 시 노이즈나 시간 변화에 따른 미세 조정 추가 가능
        val trailOffsetFactor = t * 0.1f * (1f + 0.2f * sin(time * 0.1f + index * 0.3f)) // 시간에 따라 약간씩 변하도록
        val trailX = particleX - (sin(index * 0.1f + time * particleSpeed * 1.2f) * trailOffsetFactor * 50f) // 속도 영향 약간 더
        val trailY = particleY - (cos(index * 0.1f + time * particleSpeed * 1.2f) * trailOffsetFactor * 50f)
        
        val trailAlpha = 0.5f * (1f - (t.toFloat() / trailPoints)) * (0.8f + 0.2f * perlinNoise.noise2D(time*0.01f + index*0.05f + t*0.1f, particleX*0.01f)) // 노이즈로 알파값 미세조정
        
        val particleColorVariation = when (t) {
            0 -> particleColor.copy(alpha = particleColor.alpha * trailAlpha.coerceIn(0.1f, 0.8f)) // 첫 포인트는 기본색 + 알파
            else -> LanternGlowOrange.copy(alpha = trailAlpha.coerceIn(0.05f, 0.5f)) // 나머지 포인트는 글로우 색 + 알파
        }
        
        val trailSize = particleSizePx * (1f - (t.toFloat() / (trailPoints + 1))) * (0.9f + 0.2f * cos(time * 0.05f + index * 0.2f + t*0.15f)) // 크기에도 미세한 변화

        drawCircle(
            color = particleColorVariation, // 알파는 이미 적용됨
            radius = trailSize.coerceAtLeast(0.5.dp.toPx()), // 최소 크기 보장
            center = Offset(trailX, trailY)
        )
    }
}

// 권한 체크 및 AI 활성화를 위한 헬퍼 함수
private fun checkPermissionAndActivateAI(
    context: Context, 
    viewModel: OnDeviceAIViewModel,
    permissionLauncher: ActivityResultLauncher<String>
) {
    when {
        ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED -> {
            // 이미 권한이 있으면 AI 활성화
            viewModel.activateAI()
        }
        else -> {
            // 권한이 없는 경우 권한 요청
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

// 추가: OnDeviceAIScreen을 Dialog로 표시하는 새로운 Composable
@Composable
fun OnDeviceAIDialog(
    onDismiss: () -> Unit,
    viewModel: OnDeviceAIViewModel = hiltViewModel()
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 전체 화면 너비 사용
            decorFitsSystemWindows = false,  // Dialog의 Window를 Edge-to-Edge로 설정
            dismissOnBackPress = true,       // 뒤로가기 버튼으로 닫을 수 있도록 설정
            dismissOnClickOutside = false    // 바깥 클릭으로는 닫히지 않도록 설정
        )
    ) {
        // Dialog의 내용물로 OnDeviceAIScreen 사용
        OnDeviceAIScreen(
            modifier = Modifier.fillMaxSize(),
            onDismiss = onDismiss,
            viewModel = viewModel
        )
    }
} 