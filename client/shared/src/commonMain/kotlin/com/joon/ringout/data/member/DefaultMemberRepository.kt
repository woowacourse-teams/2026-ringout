package com.joon.ringout.data.member

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.member.MemberRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class DefaultMemberRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : MemberRepository {
    override suspend fun updateNickname(nickname: String): String {
        val accessToken = checkNotNull(tokenStorage.read()?.accessToken) {
            "로그인이 필요한 기능이에요."
        }
        val response = httpClient.patch(ApiConfig.url("/api/v1/users/me/nickname")) {
            bearerAuth(accessToken)
            setBody(UpdateNicknameRequest(nickname))
        }
        val body = response.decodeOrThrow<UpdateNicknameResponse>()
        check(body.isSuccess) { body.message }
        return checkNotNull(body.result) { "닉네임 수정 응답이 비어 있어요." }.nickname
    }

    override suspend fun withdraw() {
        val accessToken = checkNotNull(tokenStorage.read()?.accessToken) {
            "로그인이 필요한 기능이에요."
        }
        val response = httpClient.delete(ApiConfig.url("/api/v1/users/me")) {
            bearerAuth(accessToken)
        }
        val body = response.decodeOrThrow<JsonElement>()
        check(body.isSuccess) { body.message }
    }
}

@Serializable
private data class UpdateNicknameRequest(
    val nickname: String,
)

@Serializable
private data class UpdateNicknameResponse(
    val nickname: String,
)

private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): ApiResponse<T> {
    val responseBody = bodyAsText()
    if (status.isSuccess()) {
        return ApiJson.decodeFromString(responseBody)
    }

    val errorResponse = runCatching {
        ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
    }.getOrNull()
    throw ApiException(
        statusCode = status.value,
        code = errorResponse?.code,
        apiMessage = errorResponse?.message ?: status.description,
        result = errorResponse?.result,
    )
}
