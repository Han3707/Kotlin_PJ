package com.ssafy.lantern.data.repository

import androidx.room.Dao
import androidx.room.Query
import com.ssafy.lantern.data.model.CallList

@Dao
interface CallListDao {

    // 전화 목록 전체 가져오기
    @Query("SELECT * FROM call_list ORDER BY date DESC")
    suspend fun getAllCallLists(): List<CallList>

    // 전화 내역 id로 지우기
    @Query("DELETE FROM call_list WHERE call_id = :callId")
    suspend fun deleteByCallId(callId: Long): Int


}