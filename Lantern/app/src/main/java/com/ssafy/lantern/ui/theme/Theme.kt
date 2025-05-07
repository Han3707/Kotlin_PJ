package com.ssafy.lantern.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 앱 전체에 적용되는 테마 설정
 */
@Composable
fun LanternTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = Color(0xFFFFD700),
            primaryVariant = Color(0xFFDAA520),
            secondary = Color(0xFFFFD700),
            background = Color.Black,
            surface = Color.Black,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}