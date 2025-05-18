package com.ssafy.lanterns.ui.screens.main.components

/**
 * 주변 사람 데이터 모델
 */
data class NearbyPerson(
    val bleId: String, // BLE에서 받은 고유 ID (예: "123")
    val userId: Long,  // 로컬 DB에서 발급받은 사용자 PK
    val name: String,
    val distance: Float,
    val angle: Float,
    val signalStrength: Float,
    val avatarSeed: Int = bleId.hashCode(), // 아바타 시드를 bleId 기반으로 변경
    val rssi: Int,
    val signalLevel: Int = 1 // 신호 강도 레벨 (1: 약함, 2: 중간, 3: 강함)
)

/**
 * 애니메이션 값을 담는 데이터 클래스
 */
data class AnimationValues(
    val buttonScale: Float,
    val buttonGlowAlpha: Float,
    val radarAngle: Float,
    val dotPulseScale: Float,
    val dotGlowAlpha: Float
)

/**
 * 리플 애니메이션 상태
 */
data class RippleState(
    val visible: Boolean,
    val animationValue: Float
) 