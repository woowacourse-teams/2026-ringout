package com.joon.ringout.data.network

object ApiConfig {
    const val BASE_URL = ApiBuildConfig.BASE_URL

    fun url(path: String): String = "$BASE_URL/${path.trimStart('/')}"
}
