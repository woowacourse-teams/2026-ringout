package com.joon.ringout.data.network

object ApiConfig {
//    const val BASE_URL = "https://api.ringout.my"
    const val BASE_URL = "https://dev-api.ringout.my"

    fun url(path: String): String = "$BASE_URL/${path.trimStart('/')}"
}
