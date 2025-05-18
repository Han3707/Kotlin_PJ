package com.ssafy.lanterns.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 둥근 폰트 스타일을 위한 SansSerif 폰트 패밀리
val RoundedFontFamily = FontFamily.SansSerif

// Material 3 Typography 설정
val Typography = Typography(
    // AI 대화 인터페이스에 사용되는 바디 텍스트 (크게 설정)
    bodyLarge = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp, // 더 크게 조정
        lineHeight = 30.sp, // 간격도 조정
        letterSpacing = 0.25.sp // 약간 더 좁게
    ),
    // 제목 스타일
    titleLarge = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp, // 더 크게
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // 중간 크기 제목
    titleMedium = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp, // 약간 더 크게
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp
    ),
    // 작은 라벨
    labelMedium = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp, // 약간 더 크게
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    // AI 응답에 사용될 수 있는 큰 텍스트
    displayMedium = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.0.sp
    ),
    // 작은 바디 텍스트
    bodySmall = TextStyle(
        fontFamily = RoundedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
) 