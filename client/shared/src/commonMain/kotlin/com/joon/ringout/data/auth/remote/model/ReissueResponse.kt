package com.joon.ringout.data.auth.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ReissueResponse(
    val accessToken: String,
    val refreshToken: String,
)
