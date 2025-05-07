package com.ssafy.lantern.data.source.remote

import com.ssafy.lantern.data.model.AuthResponse
import com.ssafy.lantern.data.model.GoogleAuthRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/login/google")
    suspend fun googleLogin(@Body googleAuthRequest: GoogleAuthRequest): Response<AuthResponse>
} 