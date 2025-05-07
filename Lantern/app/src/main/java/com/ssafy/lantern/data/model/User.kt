package com.ssafy.lantern.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,

    val nickname: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String
)
