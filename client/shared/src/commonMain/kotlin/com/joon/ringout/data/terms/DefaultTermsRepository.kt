package com.joon.ringout.data.terms

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.terms.RequiredTermStatus
import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.domain.terms.TermsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

class DefaultTermsRepository(
    private val client: HttpClient,
    tokenStorage: SecureTokenStorage,
    private val session: AuthSession,
) : TermsRepository {
    private val requests = AuthenticatedRequestExecutor(client, tokenStorage, session)

    override suspend fun getStatus(): RequiredTermsStatus {
        val identity = session.identity.value
        val response = requests.execute { token ->
            ensureSession(identity)
            client.get(ApiConfig.url("/api/v1/terms/agreements")) { bearerAuth(token) }
        }
        val dto = checkNotNull(response.decode<AgreementsResponse>())
        ensureSession(identity)
        val status = RequiredTermsStatus(dto.agreements.mapNotNull { term ->
            val type = RequiredTermType.entries.firstOrNull { it.name == term.type } ?: return@mapNotNull null
            RequiredTermStatus(type, term.termsId, term.latestVersion, term.agreedVersion, term.needsReagreement)
        })
        check(dto.allAgreed == status.allAgreed) { "약관 동의 상태를 확인할 수 없어요." }
        return status
    }

    override suspend fun agreeRequiredTerms(agreedAt: String) {
        val identity = session.identity.value
        val response = requests.execute { token ->
            ensureSession(identity)
            client.post(ApiConfig.url("/api/v1/terms")) {
                bearerAuth(token)
                setBody(AgreeRequest(RequiredTermType.entries.map { it.name }, agreedAt))
            }
        }
        response.decode<JsonElement>()
        ensureSession(identity)
    }

    private fun ensureSession(identity: Any?) {
        if (identity == null || identity !== session.identity.value) throw CancellationException("계정이 변경되었습니다.")
    }
}

@Serializable
private data class AgreementsResponse(val allAgreed: Boolean, val agreements: List<AgreementResponse>)
@Serializable
private data class AgreementResponse(
    val type: String,
    val termsId: Long,
    val latestVersion: String,
    val agreedVersion: String? = null,
    val needsReagreement: Boolean,
)
@Serializable
private data class AgreeRequest(val termsTypes: List<String>, val agreedAt: String)

private suspend inline fun <reified T> HttpResponse.decode(): T? {
    val text = bodyAsText()
    if (!status.isSuccess()) {
        val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(text) }.getOrNull()
        throw ApiException(status.value, error?.code, error?.message ?: status.description)
    }
    val body = ApiJson.decodeFromString<ApiResponse<T>>(text)
    check(body.isSuccess) { body.message }
    return body.result
}
