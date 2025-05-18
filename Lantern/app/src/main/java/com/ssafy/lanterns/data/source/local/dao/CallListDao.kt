package com.ssafy.lanterns.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
// CallList 모델 임포트 (경로 가정)
import com.ssafy.lanterns.data.model.CallList

@Dao
interface CallListDao {
    // 통화 내역 추가
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallList(callList: CallList): Long

    // 전화 목록 전체 가져오기
    @Query("SELECT * FROM call_list ORDER BY date DESC")
    suspend fun getAllCallLists(): List<CallList>

    // 전화 내역 id로 지우기
    @Query("DELETE FROM call_list WHERE call_id = :callId")
    suspend fun deleteByCallId(callId: Long): Int

    // 모든 통화 목록 정보 삭제
    @Query("DELETE FROM call_list")
    suspend fun deleteAllCallLists()
} 