package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.room.RoomRepository
import com.joon.ringout.domain.room.RoomSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class DefaultRoomRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) : RoomRepository {
    override suspend fun getRooms(): List<RoomSummary> {
        val accessToken = if (authSession.state.value == AuthSessionState.Authenticated) {
            tokenStorage.read()?.accessToken?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val response = httpClient.get(ApiConfig.url("/api/v1/rooms")) {
            accessToken?.let { bearerAuth(it) }
        }
        val body = response.decodeOrThrow<RoomListResponseEntity>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "모임 목록 응답이 비어 있어요." }
        return result.rooms.map(RoomEntity::toDomain)
    }
}

private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): ApiResponse<T> {
    val responseBody = bodyAsText()
    if (!status.isSuccess()) {
        val errorResponse = try {
            ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        throw ApiException(
            statusCode = status.value,
            code = errorResponse?.code,
            apiMessage = errorResponse?.message ?: status.description,
            result = errorResponse?.result,
        )
    }
    return ApiJson.decodeFromString(responseBody)
}
