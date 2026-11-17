package com.joon.ringout.domain.auth

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    // 로그인 응답에서 받은 회원 ID. 이전 버전의 저장 데이터에는 없을 수 있다.
    val userId: Long? = null,
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank." }
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
    }
}
