package com.joon.ringout.data.member

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.getAuthSession
import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.MemberRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class DefaultMemberRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession = getAuthSession(),
    coroutineScope: CoroutineScope? = null,
) : MemberRepository {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)
    private val cacheScope = coroutineScope ?: CoroutineScope(
        httpClient.coroutineContext + SupervisorJob(),
    ).also { scope ->
        // 장기 구독이 HttpClient.close()의 완료를 막지 않도록 독립 Job을 사용한다.
        httpClient.coroutineContext[Job]?.invokeOnCompletion { scope.cancel() }
    }
    private val profileCache = SessionMemberProfileCache(
        session = authSession,
        scope = cacheScope,
    )

    override fun getCachedProfile(): MemberProfile? = profileCache.peek()

    override suspend fun getProfile(): MemberProfile = profileCache.get {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.get(ApiConfig.url("/api/v1/users/me")) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<GetMemberResponse>()
        check(body.isSuccess) { body.message }
        val member = checkNotNull(body.result) { "회원 조회 응답이 비어 있어요." }
        MemberProfile(
            nickname = member.nickname,
            email = member.email,
        )
    }

    override suspend fun updateNickname(nickname: String): String {
        val identity = authSession.identity.value
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.patch(ApiConfig.url("/api/v1/users/me/nickname")) {
                bearerAuth(accessToken)
                setBody(UpdateNicknameRequest(nickname))
            }
        }
        val body = response.decodeOrThrow<UpdateNicknameResponse>()
        check(body.isSuccess) { body.message }
        val updatedNickname = checkNotNull(body.result) { "닉네임 수정 응답이 비어 있어요." }.nickname
        profileCache.updateNickname(identity, updatedNickname)
        return updatedNickname
    }

    override suspend fun withdraw() {
        val identity = authSession.identity.value
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.delete(ApiConfig.url("/api/v1/users/me")) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<JsonElement>()
        check(body.isSuccess) { body.message }
        profileCache.onWithdrawn(identity)
    }
}

@Serializable
private data class GetMemberResponse(
    val nickname: String,
    val email: String? = null,
)

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
