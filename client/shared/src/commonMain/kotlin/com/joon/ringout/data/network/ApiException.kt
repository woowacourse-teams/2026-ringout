package com.joon.ringout.data.network

import kotlinx.serialization.json.JsonElement

class ApiException(
    val statusCode: Int,
    val code: String?,
    val apiMessage: String,
    val result: JsonElement? = null,
) : Exception(apiMessage)
