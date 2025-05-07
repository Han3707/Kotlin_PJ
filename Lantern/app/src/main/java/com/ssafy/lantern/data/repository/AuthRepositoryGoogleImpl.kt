package com.ssafy.lantern.data.repository

import com.ssafy.lantern.data.model.GoogleAuthRequest
import com.ssafy.lantern.data.model.User
import com.ssafy.lantern.data.source.remote.AuthService
import com.ssafy.lantern.data.source.token.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryGoogleImpl @Inject constructor(
    private val authService: AuthService,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun googleLogin(idToken: String): AuthResult<User> {
        return try {
            val googleAuthRequest = GoogleAuthRequest(idToken = idToken)
            val response = authService.googleLogin(googleAuthRequest)
            
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                
                // JWT 토큰 및 사용자 정보 저장
                tokenManager.saveAccessToken(authResponse.jwt)
                tokenManager.saveUserInfo(
                    userId = authResponse.userId,
                    email = authResponse.email,
                    nickname = authResponse.nickname
                )
                
                // User 객체 생성 및 반환
                val user = User(
                    userId = authResponse.userId,
                    nickname = authResponse.nickname,
                    deviceId = "" // 구글 로그인에서는 deviceId가 필요 없음, 빈 문자열로 설정
                )
                
                AuthResult.Success(user)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "구글 로그인 실패"
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "알 수 없는 오류가 발생했습니다")
        }
    }

    override suspend fun logout(): AuthResult<Unit> {
        return try {
            tokenManager.clearTokenAndUserInfo()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "로그아웃 중 오류가 발생했습니다")
        }
    }
    
    // 기존 호환성을 위해 logout을 호출하는 signOut 메서드
    override suspend fun signOut(): AuthResult<Unit> {
        return logout()
    }

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    override suspend fun getCurrentUserId(): Long? {
        return tokenManager.getUserId()
    }

    override suspend fun getCurrentUserEmail(): String? {
        return tokenManager.getEmail()
    }

    override suspend fun getCurrentUserNickname(): String? {
        return tokenManager.getNickname()
    }
} 