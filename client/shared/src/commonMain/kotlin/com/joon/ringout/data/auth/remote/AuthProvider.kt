package com.joon.ringout.data.auth.remote

enum class AuthProvider(
    internal val pathValue: String,
) {
    Apple("apple"),
    Google("google"),
    Kakao("kakao"),
}
