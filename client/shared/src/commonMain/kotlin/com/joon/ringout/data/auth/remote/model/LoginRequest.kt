package com.joon.ringout.data.auth.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val socialAccessToken: String,
)
