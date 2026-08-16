package com.joon.ringout.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse<T>(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: T? = null,
)

@Serializable
data class ApiErrorResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: JsonElement? = null,
)
