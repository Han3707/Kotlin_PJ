package com.ssafy.lanterns.data.model

import com.google.gson.annotations.SerializedName

data class GoogleAuthRequest(
    @SerializedName("idToken")
    val idToken: String
) 