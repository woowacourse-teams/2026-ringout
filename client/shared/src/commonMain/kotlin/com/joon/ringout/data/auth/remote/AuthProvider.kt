package com.joon.ringout.data.auth.remote

enum class AuthProvider(
    internal val pathValue: String,
) {
    Google("google"),
    Kakao("kakao"),
}
