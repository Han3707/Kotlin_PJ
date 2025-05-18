package com.ssafy.lanterns.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follow")
data class Follow(
    @PrimaryKey
    @ColumnInfo(name = "follow_id")
    val followId : Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "follow_nickname")
    val followNickname: String
)
