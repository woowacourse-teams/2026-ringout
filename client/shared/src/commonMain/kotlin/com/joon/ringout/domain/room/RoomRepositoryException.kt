package com.joon.ringout.domain.room

class RoomRepositoryException(
    val statusCode: Int,
    val code: String?,
    override val message: String,
    val result: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
