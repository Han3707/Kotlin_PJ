package com.ssafy.lantern.data.source.token

interface TokenManager {
    // JWT 토큰 저장 및 조회
    suspend fun saveAccessToken(token: String)
    suspend fun getAccessToken(): String?
    
    // 사용자 정보 저장 및 조회
    suspend fun saveUserInfo(userId: Long, email: String, nickname: String)
    suspend fun getUserId(): Long?
    suspend fun getEmail(): String?
    suspend fun getNickname(): String?
    
    // 로그인 상태 확인
    suspend fun isLoggedIn(): Boolean
    
    // 로그아웃
    suspend fun clearTokenAndUserInfo()
} 