package com.ssafy.lanterns.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ssafy.lanterns.data.model.User

@Dao
interface UserDao {
    // 회원가입(사용자 정보 저장)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    // 사용자 정보 불러오기
    @Query("SELECT * FROM `user`")
    suspend fun getUserInfo(): List<User>

    // 회원 탈퇴
    @Delete
    suspend fun deleteUser(user: User): Int

    // 닉네임 변경
    @Query("UPDATE `user` SET nickname = :nickname WHERE user_id = :userId")
    suspend fun updateNickname(userId: Long, nickname: String): Int

    // 프로필 이미지 번호 변경
    @Query("UPDATE `user` SET profile_image = :profileImageUrl WHERE user_id = :userId")
    suspend fun updateProfileImage(userId: Long, profileImageUrl: String): Int

    // 모든 사용자 정보 삭제
    @Query("DELETE FROM `user`")
    suspend fun deleteAllUsers()

    // 특정 ID의 사용자 정보 조회
    @Query("SELECT * FROM `user` WHERE user_id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    // deviceId로 사용자 정보 조회 (새로 추가)
    @Query("SELECT * FROM `user` WHERE device_id = :deviceId LIMIT 1")
    suspend fun getUserByDeviceId(deviceId: String): User?

    // selected_profile_image_number 컬럼을 업데이트하는 함수 추가
    @Query("UPDATE `user` SET selected_profile_image_number = :profileImageNumber WHERE user_id = :userId")
    suspend fun updateProfileImageNumber(userId: Long, profileImageNumber: Int): Int
} 