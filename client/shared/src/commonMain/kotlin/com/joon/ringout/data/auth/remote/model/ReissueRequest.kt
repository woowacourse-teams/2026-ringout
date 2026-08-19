package com.joon.ringout.data.auth.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ReissueRequest(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
    }
}
