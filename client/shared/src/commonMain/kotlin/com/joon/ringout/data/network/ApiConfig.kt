package com.joon.ringout.data.network

object ApiConfig {
    const val BASE_URL = "http://3.36.50.38:8080"

    fun url(path: String): String = "$BASE_URL/${path.trimStart('/')}"
}
