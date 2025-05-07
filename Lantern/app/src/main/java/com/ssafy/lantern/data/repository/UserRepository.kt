package com.ssafy.lantern.data.repository

import com.ssafy.lantern.data.model.User
import javax.inject.Inject

interface UserRepository {
    suspend fun saveUser(user: User)
    suspend fun getUser(): User?
    suspend fun clearUser()
    suspend fun getCurrentUser(): User?
    suspend fun updateUser(user: User)
}

// UserDao를 주입받아 UserRepository 구현
class UserRepositoryImpl @Inject constructor(private val userDao: UserDao) : UserRepository {
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

    override suspend fun updateUser(user: User) {
        saveUser(user)
    }
} 