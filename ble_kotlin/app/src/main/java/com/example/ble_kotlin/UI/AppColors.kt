package com.example.ble_kotlin.UI

import androidx.compose.ui.graphics.Color

/**
 * 앱 전반에서 사용되는 색상 상수들을 정의하는 객체
 */
object AppColors {
    // 기본 색상
    val White = Color(0xFFFFFFFF)
    val LightGray = Color(0xFFF2F2F2)
    val DarkGray = Color(0xFF333333)
    val MidGray = Color(0xFFAAAAAA)
    
    // 액센트 색상
    val LanternAccent = Color(0xFFFFC300)
    
    // 상태 표시 색상
    val GreenStatus = Color(0xFF4CAF50)  // 활성 상태
    val RedStatus = Color(0xFFF44336)    // 오류 상태
    val AmberStatus = Color(0xFFFFC107)  // 대기 상태
    
    // 배경 색상
    val Background = White
    val BackgroundSecondary = LightGray
    
    // 텍스트 색상
    val TextPrimary = DarkGray
    val TextSecondary = MidGray
    
    // 버튼 색상
    val ActionButtonBackground = LanternAccent
} 