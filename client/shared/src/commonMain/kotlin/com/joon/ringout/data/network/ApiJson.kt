package com.joon.ringout.data.network

import kotlinx.serialization.json.Json

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
