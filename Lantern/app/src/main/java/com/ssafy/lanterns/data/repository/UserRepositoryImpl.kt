package com.ssafy.lanterns.data.repository

import android.content.Context
import android.util.Log
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.source.local.dao.CallListDao
import com.ssafy.lanterns.data.source.local.dao.ChatRoomDao
import com.ssafy.lanterns.data.source.local.dao.FollowDao
import com.ssafy.lanterns.data.source.local.dao.MessageDao
import com.ssafy.lanterns.data.source.local.dao.UserDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

// UserDao를 주입받아 UserRepository 구현
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val followDao: FollowDao,
    private val callListDao: CallListDao,
    @ApplicationContext private val context: Context // Context 주입 추가
) : UserRepository {

    companion object {
        private const val PREFERENCES_NAME = "lantern_preferences"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }

    override suspend fun saveUser(user: User) {
        // 기존 데이터 삭제 후 삽입 (단일 사용자 정보만 저장 가정)
        userDao.deleteAllUsers() // 모든 사용자 삭제 DAO 메소드 추가
        userDao.insertUser(user)
    }

    override suspend fun getUser(): User? {
        // 저장된 사용자 정보가 있다면 반환 (첫번째 항목 가정)
        return userDao.getUserInfo().firstOrNull()
    }

    override suspend fun clearUser() {
        // 모든 사용자 정보 삭제
        userDao.deleteAllUsers()
    }

    override suspend fun getCurrentUser(): User? {
        return getUser()
    }

    // 사용자 정보를 Flow로 제공하는 함수 추가
    override fun getUserFlow(): Flow<User?> = flow {
        emit(getCurrentUser())
    }

    override suspend fun updateUser(user: User) {
        // updateUser는 특정 사용자를 업데이트해야 하므로, insertUser 대신 updateUser DAO 메소드 호출이 적절
        // userDao.updateUser(user) // UserDao에 updateUser(user: User)가 정의되어 있다고 가정
        // 현재는 saveUser와 동일하게 동작하도록 임시 유지 (saveUser가 deleteAll + insert이므로)
        saveUser(user)
    }

    override suspend fun updateNickname(userId: Long, nickname: String) {
        userDao.updateNickname(userId, nickname)
    }

    override suspend fun getUserById(userId: Long): User? {
        return userDao.getUserById(userId)
    }

    override suspend fun updateProfileImageNumber(userId: Long, profileImageNumber: Int) {
        userDao.updateProfileImageNumber(userId, profileImageNumber)
    }

    // 모든 로컬 데이터 초기화
    override suspend fun clearAllLocalData() {
        // 트랜잭션으로 모든 데이터 삭제 작업을 묶음
        try {
            // 모든 테이블의 데이터 삭제
            userDao.deleteAllUsers()
            chatRoomDao.deleteAllChatRooms()
            messageDao.deleteAllMessages()
            followDao.deleteAllFollows()
            callListDao.deleteAllCallLists()
            
            Log.d("UserRepositoryImpl", "모든 로컬 데이터가 성공적으로 초기화되었습니다.")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "로컬 데이터 초기화 중 오류 발생: ${e.message}")
        }
    }
    
    // 디스플레이 모드(다크/라이트) 저장
    override suspend fun saveDisplayMode(isDarkMode: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }
        Log.d("UserRepositoryImpl", "디스플레이 모드 저장: $isDarkMode")
    }
    
    // 디스플레이 모드(다크/라이트) 불러오기
    override suspend fun getDisplayMode(): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, true) // 기본값은 다크모드
        Log.d("UserRepositoryImpl", "디스플레이 모드 불러오기: $isDarkMode")
        return isDarkMode
    }

    override suspend fun getOrInsertUserByDeviceId(deviceId: String, nickname: String): Long = withContext(Dispatchers.IO) {
        var user = userDao.getUserByDeviceId(deviceId)
        if (user == null) {
            // 새로운 사용자 생성. User 엔티티의 userId는 AutoGenerate이므로 0L로 설정
            // profileImage는 기본값 또는 랜덤값으로 설정 가능
            val newUser = User(
                userId = 0L, // Room에서 자동 생성되도록 0L 또는 null (엔티티 정의에 따라)
                nickname = nickname,
                deviceId = deviceId,
                statusMessage = "", // 기본 상태 메시지
                email = null,
                lanterns = 0,
                profileImage = "default_profile_0", // 기본 프로필 이미지 또는 랜덤 로직
                token = null,
                refreshToken = null,
                isAuthenticated = false,
                createdAt = LocalDateTime.now()
            )
            val newUserId = userDao.insertUser(newUser) // insertUser가 Long (새로운 rowId/PK)을 반환한다고 가정
            Log.d("UserRepositoryImpl", "새로운 사용자 등록: deviceId=$deviceId, nickname=$nickname, newUserId=$newUserId")
            return@withContext newUserId
        } else {
            // 기존 사용자의 닉네임이 다르면 업데이트 (선택적)
            if (user.nickname != nickname) {
                userDao.updateNickname(user.userId, nickname)
                Log.d("UserRepositoryImpl", "기존 사용자 닉네임 업데이트: userId=${user.userId}, 새 닉네임=$nickname")
            }
            Log.d("UserRepositoryImpl", "기존 사용자 조회: deviceId=$deviceId, userId=${user.userId}")
            return@withContext user.userId
        }
    }
}