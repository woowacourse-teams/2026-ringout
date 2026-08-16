package com.joon.ringout.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

internal fun HttpClientConfig<*>.configureRingoutHttpClient() {
    expectSuccess = false

    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = HTTP_TIMEOUT_MILLIS
        requestTimeoutMillis = HTTP_TIMEOUT_MILLIS
        socketTimeoutMillis = HTTP_TIMEOUT_MILLIS
    }
    defaultRequest {
        accept(ContentType.Application.Json)
        contentType(ContentType.Application.Json)
    }
}

internal expect fun createRingoutHttpClient(): HttpClient

private const val HTTP_TIMEOUT_MILLIS = 20_000L
