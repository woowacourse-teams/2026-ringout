package com.joon.ringout.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createRingoutHttpClient(): HttpClient = HttpClient(Darwin) {
    configureRingoutHttpClient()
}
