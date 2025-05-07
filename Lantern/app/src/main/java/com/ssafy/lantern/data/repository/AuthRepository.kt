package com.ssafy.lantern.data.repository

import com.ssafy.lantern.data.model.User

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
}

interface AuthRepository {
    // 구글 ID 토큰으로 로그인
    suspend fun googleLogin(idToken: String): AuthResult<User>
    
    // 로그아웃
    suspend fun logout(): AuthResult<Unit>
    
    // 로그아웃 (기존 호환성 유지를 위한 메서드)
    suspend fun signOut(): AuthResult<Unit>
    
    // 로그인 상태 확인
    suspend fun isLoggedIn(): Boolean
    
    // 현재 로그인된 사용자 정보 조회
    suspend fun getCurrentUserId(): Long?
    suspend fun getCurrentUserEmail(): String?
    suspend fun getCurrentUserNickname(): String?
} 