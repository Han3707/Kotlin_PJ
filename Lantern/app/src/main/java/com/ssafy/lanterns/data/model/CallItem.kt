package com.ssafy.lanterns.data.model

import androidx.annotation.DrawableRes

/**
 * 통화 및 친구 정보를 담는 데이터 클래스
 */
data class FriendCallItem(
    val id: Int,
    val name: String,
    @DrawableRes val profileImage: Int, // Resource ID for profile image
    val callType: String, // "발신전화" or "부재중전화" or "수신전화"
    val timestamp: String, // Time of call like "10:25 am"
    val isRecent: Boolean, // Whether the call is recent or not
    val phoneNumber: String = "",
    val callDuration: String = ""
)