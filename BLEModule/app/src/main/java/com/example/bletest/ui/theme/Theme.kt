package com.example.bletest.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 밝은 테마 색상
private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1), // 짙은 파란색
    onPrimary = Color.White,
    secondary = Color(0xFF2196F3), // 밝은 파란색
    onSecondary = Color.White,
    tertiary = Color(0xFF4CAF50), // 초록색
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5), // 연한 회색
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
)

// 어두운 테마 색상
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9), // 연한 파란색
    onPrimary = Color(0xFF0D47A1),
    secondary = Color(0xFF2196F3), // 밝은 파란색
    onSecondary = Color.Black,
    tertiary = Color(0xFF81C784), // 연한 초록색
    onTertiary = Color.Black,
    background = Color(0xFF121212), // 짙은 회색
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E), // 다크 그레이
    onSurface = Color.White,
)

@Composable
fun BleMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
} 