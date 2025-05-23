package com.ssafy.lanterns.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
// Follow 모델 임포트 (경로 가정)
import com.ssafy.lanterns.data.model.Follow

@Dao
interface FollowDao {
    // 친구 추가
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFollow(follow: Follow): Long

    // 친구 삭제
    @Delete
    suspend fun deleteFollow(follow: Follow): Int

    // 친구 목록 전체 불러오기
    @Query("SELECT * FROM follow ORDER BY follow_id DESC")
    suspend fun getFollowList(): List<Follow>

    // 이름으로 친구 검색
    @Query("""
        SELECT * 
        FROM follow
        WHERE follow_nickname LIKE '%' || :nickname || '%'
        ORDER BY follow_id DESC
    """)
    suspend fun searchFollowsByNickname(nickname: String): List<Follow>

    // 모든 팔로우 정보 삭제
    @Query("DELETE FROM follow")
    suspend fun deleteAllFollows()
} 