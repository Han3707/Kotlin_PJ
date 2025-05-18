package com.ssafy.lanterns.model

import kotlin.random.Random

/**
 * 채팅 메시지 모델 클래스 - BLE 및 채팅 화면에서 공통으로 사용
 * @param id 메시지 ID
 * @param sender 발신자 이름
 * @param text 메시지 내용
 * @param time 전송 시간 (밀리초 타임스탬프)
 * @param isMe 내가 보낸 메시지 여부
 * @param senderProfileId 발신자 프로필 ID
 * @param rssi 수신 신호 강도 (BLE)
 * @param isPublic 공개 채팅 여부
 */
data class ChatMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val time: Long, // 밀리초 타임스탬프
    val isMe: Boolean = false,
    val senderProfileId: Int? = null,
    val rssi: Int = -1,
    val isPublic: Boolean = false
)

/**
 * 직접 채팅 UI 상태를 위한 데이터 클래스
 */
data class DirectChatUiState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val participant: User? = null,
    val chatRoomId: Long? = null,
    val errorMessage: String? = null,
    val connectionStatus: String = "연결 안됨", // 연결 상태 (BLE 연결 상태를 표시)
    val isConnecting: Boolean = false,
    val isLoadingMore: Boolean = false, // 더 많은 메시지 로드 중인지 여부
    val hasMoreMessages: Boolean = true, // 더 로드할 메시지가 있는지 여부
    val signalStrength: Int = 0, // 신호 세기 (0-100)
    
    // BLE 관련 상태
    val requiredPermissionsGranted: Boolean = false, // BLE 권한 부여 여부
    val isBluetoothEnabled: Boolean = false, // 블루투스 활성화 여부
    val isScanning: Boolean = false, // BLE 스캔 중 여부
    val isAdvertising: Boolean = false, // BLE 광고 중 여부
    val scannedDevices: Map<String, String> = emptyMap() // 스캔된 BLE 기기 목록 (주소 -> 이름)
)

/**
 * 공개 채팅 UI 상태를 위한 데이터 클래스
 */
data class PublicChatUiState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val userCount: Int = 0,
    val errorMessage: String? = null,
    val connectionStatus: String = "연결 안됨",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isConnecting: Boolean = false
)

/**
 * 임시 User 클래스 참조 
 * 이미 다른 곳에 정의되어 있다면 이 부분은 제거해도 됩니다.
 */
data class User(
    val userId: Long,
    val nickname: String,
    val deviceId: String,
    val statusMessage: String? = null,
    val email: String? = null,
    val lanterns: Int = 0,
    val profileImage: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val isAuthenticated: Boolean = false,
    val createdAt: java.time.LocalDateTime? = null
) 