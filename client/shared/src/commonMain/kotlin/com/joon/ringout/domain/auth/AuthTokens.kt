package com.joon.ringout.domain.auth

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank." }
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
    }
}
