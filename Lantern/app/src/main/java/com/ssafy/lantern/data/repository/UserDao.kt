package com.ssafy.lantern.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ssafy.lantern.data.model.User

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

    // 모든 사용자 정보 삭제
    @Query("DELETE FROM `user`")
    suspend fun deleteAllUsers()
}