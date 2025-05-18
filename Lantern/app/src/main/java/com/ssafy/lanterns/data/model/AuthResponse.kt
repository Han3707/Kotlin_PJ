package com.ssafy.lanterns.data.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("jwt")
    val jwt: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("nickName")
    val nickname: String,
    
    @SerializedName("userId")
    val userId: Long
) 