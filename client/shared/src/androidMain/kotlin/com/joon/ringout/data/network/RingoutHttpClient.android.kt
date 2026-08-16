package com.joon.ringout.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createRingoutHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureRingoutHttpClient()
}
