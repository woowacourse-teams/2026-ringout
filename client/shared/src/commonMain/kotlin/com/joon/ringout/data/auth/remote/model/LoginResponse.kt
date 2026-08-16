package com.joon.ringout.data.auth.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val signupToken: String? = null,
    val isNewUser: Boolean,
)
