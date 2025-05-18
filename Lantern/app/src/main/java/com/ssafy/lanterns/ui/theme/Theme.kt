package com.ssafy.lanterns.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 랜턴 그라디언트 정의
val lanternGradient = Brush.sweepGradient(
    colors = listOf(LanternTeal, LanternBlue, LanternViolet, LanternTeal)
)

// 앱의 기본 다크 테마 ColorScheme
private val AppDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,                 // com.ssafy.lanterns.ui.theme.PrimaryBlue
    onPrimary = TextPrimary,               // com.ssafy.lanterns.ui.theme.TextPrimary
    secondary = LanternYellow,             // 주요 보조 색상 (예: 랜턴 관련 강조)
    onSecondary = Color.Black,             // LanternYellow 위에 표시될 텍스트/아이콘 (가독성 고려)
    tertiary = BleAccent,                  // 세 번째 강조 색상 (예: BLE 관련)
    onTertiary = TextPrimary,
    background = AppBackground,            // com.ssafy.lanterns.ui.theme.AppBackground
    onBackground = TextPrimary,
    surface = SurfaceDark,                 // com.ssafy.lanterns.ui.theme.SurfaceDark
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,         // 표면의 변형 (예: 약간 다른 배경의 카드)
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,                      // com.ssafy.lanterns.ui.theme.ErrorRed
    onError = TextPrimary,
    // 추가적인 Material 3 색상들은 필요에 따라 Color.kt에서 가져오거나 직접 정의합니다.
    // 예: primaryContainer, onPrimaryContainer 등
)

// 앱의 기본 라이트 테마 ColorScheme 
private val AppLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,                 
    onPrimary = Color.White,               
    secondary = LanternYellow,             
    onSecondary = Color.Black,             
    tertiary = BleAccent,                  
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),        // 밝은 배경
    onBackground = Color(0xFF121212),      // 밝은 배경 위의 텍스트
    surface = Color.White,                 
    onSurface = Color(0xFF121212),         
    surfaceVariant = Color(0xFFE1E1E1),    // 약간 다른 표면 (카드 등)
    onSurfaceVariant = Color(0xFF424242),
    error = ErrorRed,                      
    onError = Color.White
)

// 테마 1 (옐로우): 다크
private val Theme1DarkColors = darkColorScheme(
    primary = LanternYellow,
    onPrimary = Color.Black,
    secondary = LanternYellow,
    onSecondary = Color.Black,
    background = AppBackground, // 원래의 푸른 계열 검은색 (0xFF0A0F2C)
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed,
    onError = TextPrimary
)

// 테마 1 (옐로우): 라이트
private val Theme1LightColors = lightColorScheme(
    primary = LanternYellow,
    onPrimary = Color.Black,
    secondary = LanternTeal,
    onSecondary = Color.Black,
    background = Color(0xFFFFFBE6),
    surface = Color.White,
    onBackground = Color(0xFF33280B),
    onSurface = Color(0xFF33280B),
    error = ErrorRed,
    onError = Color.White
)

// 테마 2 (블루): 다크
private val Theme2DarkColors = darkColorScheme(
    primary = LanternBlue,
    secondary = Color(0xFF90CAF9),
    background = Color(0xFF0A1A2E),
    surface = Color(0xFF0F2147),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed,
    onError = TextPrimary
)

// 테마 2 (블루): 라이트
private val Theme2LightColors = lightColorScheme(
    primary = LanternBlue,
    secondary = Color(0xFF90CAF9),
    background = Color(0xFFE3F2FD),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF0A1A2E),
    onSurface = Color(0xFF0A1A2E),
    error = ErrorRed,
    onError = Color.White
)

// 테마 3 (퍼플): 다크
private val Theme3DarkColors = darkColorScheme(
    primary = LanternViolet,
    secondary = LanternGlowOrange,
    background = Color(0xFF220022),
    surface = Color(0xFF331133),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed,
    onError = TextPrimary
)

// 테마 3 (퍼플): 라이트
private val Theme3LightColors = lightColorScheme(
    primary = LanternViolet,
    secondary = LanternGlowOrange,
    background = Color(0xFFF3E5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF2C0C2C),
    onSurface = Color(0xFF2C0C2C),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun LanternsTheme(
    themeNumber: Int = 1, // 1: 기본, 2: 블루, 3: 퍼플
    darkTheme: Boolean = isSystemInDarkTheme(), // 사용자 설정 또는 시스템 다크 모드
    dynamicColor: Boolean = false, // API 31+ 에서 Wallpaper 색상 기반 동적 테마 사용 여부
    content: @Composable () -> Unit
) {
    // 선택된 테마와 모드에 따라 적절한 ColorScheme 선택
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (themeNumber) {
            1 -> if (darkTheme) Theme1DarkColors else Theme1LightColors
            2 -> if (darkTheme) Theme2DarkColors else Theme2LightColors
            3 -> if (darkTheme) Theme3DarkColors else Theme3LightColors
            else -> if (darkTheme) AppDarkColorScheme else AppLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 상태바와 네비게이션바 색상을 테마 배경색과 일치시키거나, 특정 색으로 지정
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb() 

            // 다크 테마에 따라 상태바 아이콘 색상 조정
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            WindowCompat.setDecorFitsSystemWindows(window, false) // 전체 화면 사용
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


// MyPageScreen에서 테마 변경 기능을 위해 추가할 ColorScheme들 (예시)
// 실제 색상 값은 Color.kt에 정의된 것을 사용하거나 새롭게 조합합니다.
val Theme1Colors = darkColorScheme( // 예시: 현재 AppColorScheme과 유사하지만 약간 다름
    primary = LanternTeal,
    secondary = LanternBlue,
    background = Color(0xFF121212), // 더 어두운 배경
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed,
    onError = TextPrimary
)

val Theme2Colors = darkColorScheme(
    primary = LanternViolet,
    secondary = LanternGlowOrange,
    background = Color(0xFF220022), // 보라색 계열 배경
    surface = Color(0xFF331133),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed,
    onError = TextPrimary
)
// 필요에 따라 Theme3Colors 등 추가 정의