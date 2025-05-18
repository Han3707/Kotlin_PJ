package com.ssafy.lanterns.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,

    val nickname: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "selected_profile_image_number", defaultValue = "1")
    val selectedProfileImageNumber: Int = 1,
    
    // MyPageScreen에서 사용하는 추가 필드들
    @ColumnInfo(name = "status_message")
    val statusMessage: String? = null,
    
    @ColumnInfo(name = "email")
    val email: String? = null,
    
    @ColumnInfo(name = "lanterns", defaultValue = "0")
    val lanterns: Int = 0,
    
    @ColumnInfo(name = "profile_image")
    val profileImage: String? = null,
    
    @ColumnInfo(name = "token")
    val token: String? = null,
    
    @ColumnInfo(name = "refresh_token")
    val refreshToken: String? = null,
    
    @ColumnInfo(name = "is_authenticated", defaultValue = "0")
    val isAuthenticated: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime? = null
)
