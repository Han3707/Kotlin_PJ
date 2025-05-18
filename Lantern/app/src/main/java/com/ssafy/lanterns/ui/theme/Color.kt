package com.ssafy.lanterns.ui.theme

import androidx.compose.ui.graphics.Color

// Default Material Theme Colors (Legacy)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// --- Base App Theme Colors ---
val AppBackground = Color(0xFF0A0F2C)      // Primary app background
val PrimaryBlue = Color(0xFF0082FC)       // Primary interaction color (e.g., buttons)
val TextPrimary = Color(0xFFFFFFFF)       // Primary text color
val TextSecondary = Color(0xB3FFFFFF)     // Secondary text color (70% alpha white)
val TextGray = Color(0xFF9E9E9E)          // Gray text color for specific cases
val SurfaceDark = Color(0xFF0D1D3A)       // Surface color for cards, inputs, etc.
val SurfaceLight = Color(0xFF1A2234)      // Lighter surface for specific components (e.g., CallHistory item)
val ErrorRed = Color(0xFFFF5252)          // Error indication
val DisabledState = Color(0xFF666666)     // For disabled or inactive elements

// --- Lantern Specific Colors ---
val LanternYellow = Color(0xFFFFC107)     // Main lantern yellow, used across multiple screens
val LanternYellowDark = Color(0xFFFF9800) // Darker shade of lantern yellow
val LanternCoreYellow = Color(0xFFFFE082) // Lantern core bright yellow
val LanternGlowOrange = Color(0xFFFFB74D) // Lantern glow orange
val LanternOrange = Color(0xFFFF9800)     // Orange color for lantern components
val LanternWarmWhite = Color(0xFFFFF9C4)  // Lantern highlight warm white
val LanternShadeDark = Color(0xFF212121)  // Dark background for lantern or contrast
val LanternParticleColor = Color(0xFFFFE57F) // Light particle color
val LanternTeal = Color(0xFF4DB6AC)       // Theme option for lantern
val LanternBlue = Color(0xFF42A5F5)       // Theme option for lantern (also MyPage button)
val LanternViolet = Color(0xFF7E57C2)     // Theme option for lantern (also MyPage button)

// --- Component/Screen Specific Colors ---

// Navigation
val BottomNavBackground = Color(0xFF0A1128) // BottomNavigationBar background
val BottomNavIndicator = Color(0xFF0A1128)  // BottomNavigationBar indicator (can be same as background or accent)

// OnDevice AI Screen
val AiStateDefaultBg = Color(0xFF010A13)
val AiStateDefaultFg = Color(0xFF0A192F) // Also gradient end
val AiStateListeningBg = Color(0xFF0A192F)
val AiStateListeningFg = Color(0xFF173A5E)
val AiStateCommandRecognizedBg = Color(0xFF2C1D00)
val AiStateCommandRecognizedFg = Color(0xFF6F4200)
val AiStateProcessingBg = Color(0xFF001B2E)
val AiStateProcessingFg = Color(0xFF003052)
val AiStateSpeakingBg = Color(0xFF1E1A00)
val AiStateSpeakingFg = Color(0xFF4A3F00)
val AiStateErrorBg = Color(0xFF3B0000)
val AiStateErrorFg = Color(0xFF6B0000)
// Light status colors for AI (used in different part of AI screen)
val AiStateProcessingLight = Color(0xFF64B5F6)
val AiStateErrorLight = Color(0xFFE57373)

// Common Components
val CommonComponentBackground = Color(0xFF232323) // FriendListScreen, etc.
val DarkModalBackground = Color(0xFF0F1D3A)      // e.g., NearbyPersonListModal container
val DarkerListBackground = Color(0xFF051225)    // e.g., FriendList background

// Profile Screen & Connection Strength
val ProfileDistanceIndicatorBg = Color(0xFFFFD700) // Yellow background for distance
val ConnectionNear = Color(0xFF21AA73)    // Green for near (0-100m)
val ConnectionMedium = Color(0xFFFFD700)  // Yellow for medium (100-300m)
val ConnectionFar = ErrorRed              // Red for far, using ErrorRed for consistency

// Call History
val CallHistoryItemBackground = Color(0xFF1A2234) // Defined as SurfaceLight above, can be aliased if preferred
val CallHistoryItemShadow = Color(0xFF1A2639) // with alpha 0.7f
val CallHistoryMissingCall = Color(0xFFE57373) // Light red for missed calls

// Chat UI (already well-defined, kept for clarity)
val ChatBubbleMine = Color(0xFF1A2C51)
val ChatBubbleOthers = Color(0xFF2A3F6D)
val ChatInputBackground = SurfaceDark

// Main Screen Specific (some may be aliased from Lantern colors)
val MainScreenCardBg = Color(0xFF1E293B) // with alpha 0.6f
val DeepOrange = Color(0xFFE65100)       // Added for MainContent.kt specific use

// Misc (Colors from grep that were less categorized initially)
val ModalGradientStart = Color(0xFF1A3468) // with alpha
val ModalGradientEnd = Color(0xFF0D6166)   // with alpha
val ModalGradientMid = Color(0xFF384C6D)    // For NearbyPersonListModal gradient
val ModalGradientDark1 = Color(0xFF1A2643)  // For NearbyPersonListModal gradient
val ModalGradientDark2 = Color(0xFF262640)  // For NearbyPersonListModal gradient


// --- Legacy Aliases (for smoother transition, eventually to be phased out if possible) ---
// It's better to directly use the new names (e.g., AppBackground instead of NavyTop)
// These are kept for now to minimize breaking changes during initial refactor
val DarkBackground = LanternShadeDark
val DarkCardBackground = SurfaceDark
val Primary = PrimaryBlue
val Error = ErrorRed

val BleBlue1 = Color(0xFF0057FF)
val BleBlue2 = Color(0xFF00C6FF)
val BleAccentBright = Color(0xFF4DFFB4)
val BleAccent = Color(0xFF21AA73)     // Same as ConnectionNear
val BleDarkBlue = Color(0xFF003380)
val BleGlow = PrimaryBlue.copy(alpha = 0.5f) // Equivalent to Color(0x800082FC)

val ConnectionStrong = ConnectionNear
val ConnectionWeak = ConnectionFar

// Consider removing these if direct usage of new names is adopted quickly
val NavyTop = AppBackground
val NavyBottom = AppBackground
val NavyMedium = SurfaceDark
val ButtonBlue = PrimaryBlue
val TextWhite = TextPrimary
val TextWhite70 = TextSecondary

// 레이더 관련 색상
val RadarGradientStart = Color(0x50FFFFFF) // 더 투명한 흰색 (투명도 조정: 0x88 -> 0x50)
val RadarGradientMiddle = Color(0x20FFFFFF) // 더 투명한 흰색 (투명도 조정: 0x33 -> 0x20)
val RadarGradientEnd = Color(0x00FFFFFF) // 완전 투명
val RadarEdgeColor = Color(0x60FFFFFF) // 레이더 테두리 색상 (투명도 조정: 0.8f -> 0.38f)
val RadarLineColor = Color(0x80FFFFFF) // 레이더 선 색상 (투명도: 0.5f)

// 블루투스 관련 색상
val BluetoothColor = Color(0xFF2979FF) // 블루투스 아이콘 색상
val BluetoothGlowColor = Color(0x668CABE6) // 블루투스 발광 효과 색상 