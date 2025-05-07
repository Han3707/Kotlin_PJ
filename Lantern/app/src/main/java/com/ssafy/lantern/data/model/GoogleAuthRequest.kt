package com.ssafy.lantern.data.model

import com.google.gson.annotations.SerializedName

data class GoogleAuthRequest(
    @SerializedName("idToken")
    val idToken: String
) 