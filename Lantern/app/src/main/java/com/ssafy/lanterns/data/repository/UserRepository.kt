package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 사용자 정보를 관리하는 Repository 인터페이스
 */
interface UserRepository {
    /**
     * 현재 로그인된 사용자 정보 조회
     * @return 현재 사용자 정보 또는 null(로그인 안된 경우)
     */
    suspend fun getCurrentUser(): User?
    
    /**
     * 사용자 정보 흐름 조회
     * @return 사용자 정보 Flow (로그인 상태 변경 시 자동 업데이트)
     */
    fun getUserFlow(): Flow<User?>
    
    /**
     * 사용자 정보 업데이트
     * @param user 업데이트할 사용자 정보
     */
    suspend fun updateUser(user: User)

    suspend fun saveUser(user: User)
    suspend fun getUser(): User?
    suspend fun clearUser()
    suspend fun updateNickname(userId: Long, nickname: String)
    suspend fun getUserById(userId: Long): User?
    suspend fun updateProfileImageNumber(userId: Long, profileImageNumber: Int)
    suspend fun clearAllLocalData()
    suspend fun saveDisplayMode(isDarkMode: Boolean)
    suspend fun getDisplayMode(): Boolean

    /**
     * deviceId(BLE ID)를 사용하여 사용자를 조회하거나, 없으면 새로 생성하고 반환합니다.
     * 새로운 사용자는 주어진 nickname으로 생성됩니다.
     * @param deviceId BLE 스캔으로 받은 고유 ID. User 엔티티의 deviceId 필드에 해당합니다.
     * @param nickname 해당 사용자의 닉네임.
     * @return 해당 사용자의 로컬 DB userId (PK).
     */
    suspend fun getOrInsertUserByDeviceId(deviceId: String, nickname: String): Long
}

/* // 중복 정의된 User 클래스 주석 처리 - com.ssafy.lanterns.data.model.user.User를 사용합니다.
/**
 * 사용자 정보 모델 클래스
 */
data class User(
    val id: String,         // 사용자 고유 ID
    val nickname: String,   // 사용자 닉네임
    val profileImage: String? = null, // 프로필 이미지 URL
    val status: String? = null        // 사용자 상태 메시지
)
*/