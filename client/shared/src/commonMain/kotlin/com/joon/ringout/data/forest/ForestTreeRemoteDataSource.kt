package com.joon.ringout.data.forest

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import com.joon.ringout.data.alarmoccurrence.checkSession
import com.joon.ringout.data.alarmoccurrence.checkToken
import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal class ForestTreeRemoteDataSource(
    private val client: HttpClient,
    tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) {
    private val requests = AuthenticatedRequestExecutor(client, tokenStorage, authSession)

    suspend fun plant(account: AlarmOccurrenceAccount, delivery: ForestTreePlantingDelivery): ForestTree {
        require(delivery.growthCycle in 1..16 && delivery.plantedAtEpochMillis >= 0)
        ForestTreeKind.fromPersistedValue(delivery.kind)
        val body = PlantTreeRequest(delivery.kind, Instant.fromEpochMilliseconds(delivery.plantedAtEpochMillis).toString())
        val response = requests.execute { token ->
            account.checkToken(authSession, token)
            client.put(ApiConfig.url("/api/v1/forest/trees/${delivery.growthCycle}")) {
                bearerAuth(token)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        currentCoroutineContext().ensureActive()
        account.checkSession(authSession)
        if (response.status.value !in setOf(200, 201)) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(text) }.getOrNull()
            throw ApiException(response.status.value, error?.code, error?.message ?: response.status.description)
        }
        val result = ApiJson.decodeFromString<ApiResponse<ForestTreeResponse>>(text)
        check(result.isSuccess) { result.message }
        return checkNotNull(result.result).toDomain().also { tree ->
            // An existing server tree may have a different kind, position, and acquisition time.
            require(tree.growthCycle == delivery.growthCycle) { "나무 저장 응답의 회차가 일치하지 않습니다." }
        }
    }

    suspend fun acknowledge(account: AlarmOccurrenceAccount, delivery: ForestTreeAcknowledgementDelivery): ForestTree {
        require(delivery.growthCycle in 1..16 && delivery.acknowledgedAtEpochMillis >= 0)
        val body = AcknowledgeTreeRequest(Instant.fromEpochMilliseconds(delivery.acknowledgedAtEpochMillis).toString())
        val response = requests.execute { token ->
            account.checkToken(authSession, token)
            client.patch(ApiConfig.url("/api/v1/forest/trees/${delivery.growthCycle}")) {
                bearerAuth(token)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        currentCoroutineContext().ensureActive()
        account.checkSession(authSession)
        if (response.status.value != 200) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(text) }.getOrNull()
            throw ApiException(response.status.value, error?.code, error?.message ?: response.status.description)
        }
        val result = ApiJson.decodeFromString<ApiResponse<ForestTreeResponse>>(text)
        check(result.isSuccess) { result.message }
        val tree = checkNotNull(result.result).toDomain()
        require(delivery.matches(tree) && tree.acknowledgedAtEpochMillis != null) {
            "나무 확인 응답이 요청한 나무와 일치하지 않습니다."
        }
        return tree
    }
}

@Serializable
private data class AcknowledgeTreeRequest(val acknowledgedAt: String)

@Serializable
private data class PlantTreeRequest(val kind: String, val plantedAt: String)

@Serializable
internal data class ForestTreeResponse(
    val growthCycle: Int,
    val kind: String,
    val slotIndex: Int,
    val plantedAt: String,
    val acknowledgedAt: String?,
) {
    fun toDomain(): ForestTree {
        val planted = Instant.parse(plantedAt).toEpochMilliseconds()
        val acknowledged = acknowledgedAt?.let { Instant.parse(it).toEpochMilliseconds() }
        require(planted >= 0 && (acknowledged == null || acknowledged >= 0))
        return ForestTree(growthCycle, ForestTreeKind.fromPersistedValue(kind), slotIndex, planted, acknowledged)
    }
}
