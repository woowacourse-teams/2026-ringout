package com.joon.ringout.data.auth.remote

import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.LoginResponse
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.SignupResponse
import com.joon.ringout.data.network.ApiResponse

interface AuthApi {
    suspend fun login(
        provider: AuthProvider,
        request: LoginRequest,
    ): ApiResponse<LoginResponse>

    suspend fun signup(
        signupToken: String,
        request: SignupRequest,
    ): ApiResponse<SignupResponse>
}
