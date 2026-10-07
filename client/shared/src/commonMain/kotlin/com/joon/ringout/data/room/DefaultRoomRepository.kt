package com.joon.ringout.data.room

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomManagementMember
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomRepository
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.domain.room.RoomUpdateInput
import com.joon.ringout.domain.room.RoomUpdateResult
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class DefaultRoomRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) : RoomRepository {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)

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

    override suspend fun getRoom(roomId: Long): RoomMembershipDetails {
        if (roomId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "COMMON400",
                message = "모임 ID를 확인해 주세요.",
            )
        }
        ensureGetAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.get(ApiConfig.url("/api/v1/rooms/$roomId")) {
                    bearerAuth(accessToken)
                }
            }
            val body = response.decodeOrThrow<JsonElement>()
            if (!body.isSuccess) {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message,
                )
            }
            check(response.status == HttpStatusCode.OK) { "모임 상세 조회 응답 상태가 올바르지 않아요." }
            check(body.code == "ROOM200") { body.message }
            val result = checkNotNull(body.result) { "모임 상세 응답이 비어 있어요." }
            val details = ApiJson.decodeFromJsonElement<RoomMembershipResponseEntity>(result)
                .toDomain(setOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER))
            check(details.room.id == roomId) { "조회한 모임 ID가 요청과 달라요." }
            return details
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun getMembersForManagement(roomId: Long): List<RoomManagementMember> {
        if (roomId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "COMMON400",
                message = "모임 ID를 확인해 주세요.",
            )
        }
        ensureGetAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.get(ApiConfig.url("/api/v1/rooms/$roomId/members")) {
                    bearerAuth(accessToken)
                }
            }
            val body = response.decodeOrThrow<JsonElement>()
            if (!body.isSuccess) {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message,
                )
            }
            if (response.status != HttpStatusCode.OK || body.code != "MEMBER200") {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message.ifBlank { "회원 목록 응답이 올바르지 않아요." },
                )
            }
            val result = body.result ?: throw invalidManagementResponse()
            return try {
                ApiJson.decodeFromJsonElement<RoomManagementMembersResponseEntity>(result).toDomain()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SerializationException) {
                throw invalidManagementResponse(error)
            } catch (error: IllegalArgumentException) {
                throw invalidManagementResponse(error)
            } catch (error: IllegalStateException) {
                throw invalidManagementResponse(error)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun getMemberMovements(roomId: Long): List<RoomMemberMovement> {
        if (roomId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "ROOM400",
                message = "모임 ID를 확인해 주세요.",
            )
        }
        ensureGetAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.get(ApiConfig.url("/api/v1/rooms/$roomId/members/movements")) {
                    bearerAuth(accessToken)
                }
            }
            val body = response.decodeOrThrow<JsonElement>()
            if (!body.isSuccess) {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message,
                )
            }
            if (response.status != HttpStatusCode.OK || body.code != "ROOM200") {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message.ifBlank { "모임 회원 이동 상태 응답이 올바르지 않아요." },
                )
            }
            val result = body.result ?: throw invalidMemberMovementsResponse()
            return try {
                ApiJson.decodeFromJsonElement<RoomMemberMovementsResponseEntity>(result).toDomain()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SerializationException) {
                throw invalidMemberMovementsResponse(error)
            } catch (error: IllegalArgumentException) {
                throw invalidMemberMovementsResponse(error)
            } catch (error: IllegalStateException) {
                throw invalidMemberMovementsResponse(error)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun kickMember(roomId: Long, userId: Long) {
        if (roomId <= 0L || userId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "COMMON400",
                message = "모임 또는 회원 ID를 확인해 주세요.",
            )
        }
        ensurePostAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.post(ApiConfig.url("/api/v1/rooms/$roomId/kick")) {
                    bearerAuth(accessToken)
                    setBody(RoomKickRequestEntity(userId))
                }
            }
            response.decodeRoomActionOrThrow()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }
    
    override suspend fun getRoomRecords(roomId: Long, date: MissionDate): RoomRecords {
        require(roomId > 0L) { "모임 ID를 확인해 주세요." }
        ensureGetAuthenticated()
        val identity = authSession.identity.value
        try {
            val response = authenticatedRequests.execute { accessToken ->
                check(authSession.identity.value === identity) { "로그인 상태가 바뀌었어요." }
                httpClient.get(ApiConfig.url("/api/v1/rooms/$roomId/records")) {
                    bearerAuth(accessToken)
                    parameter("date", date.iso8601)
                }
            }
            check(authSession.state.value == AuthSessionState.Authenticated && authSession.identity.value === identity) {
                "로그인 상태가 바뀌었어요."
            }
            val body = response.decodeOrThrow<JsonElement>()
            if (!body.isSuccess) throw RoomRepositoryException(response.status.value, body.code, body.message)
            check(response.status == HttpStatusCode.OK && body.code == "RECORD200") { body.message }
            val result = checkNotNull(body.result) { "모임 기록 응답이 비어 있어요." }
            return ApiJson.decodeFromJsonElement<RoomRecordsResponseEntity>(result).toDomain()
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails {
        ensurePostAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.post(ApiConfig.url("/api/v1/rooms")) {
                    bearerAuth(accessToken)
                    setBody(input.toEntity())
                }
            }
            return response.decodeMembershipOrThrow(
                emptyResultMessage = "모임 생성 응답이 비어 있어요.",
                allowedRoles = setOf(RoomMembershipRole.OWNER),
            )
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun joinRoom(roomId: Long): RoomMembershipDetails {
        require(roomId > 0L) { "모임 ID를 확인해 주세요." }
        ensurePostAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.post(ApiConfig.url("/api/v1/rooms/$roomId/members")) {
                    bearerAuth(accessToken)
                }
            }
            val details = response.decodeMembershipOrThrow(
                emptyResultMessage = "모임 가입 응답이 비어 있어요.",
                allowedRoles = setOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER),
            )
            check(details.room.id == roomId) { "가입한 모임 ID가 요청과 달라요." }
            return details
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    override suspend fun updateRoom(roomId: Long, input: RoomUpdateInput): RoomUpdateResult {
        if (roomId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "COMMON400",
                message = "모임 ID를 확인해 주세요.",
            )
        }
        if (!input.hasChanges) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "ROOM400",
                message = "수정할 내용을 입력해 주세요.",
            )
        }
        ensurePostAuthenticated()
        val requestIdentity = checkNotNull(authSession.identity.value) {
            "로그인이 필요한 기능이에요."
        }
        try {
            val response = authenticatedRequests.execute { accessToken ->
                check(authSession.identity.value === requestIdentity) { "로그인 상태가 바뀌었어요." }
                val multipart = input.toMultipartContent()
                httpClient.patch(ApiConfig.url("/api/v1/rooms/$roomId")) {
                    bearerAuth(accessToken)
                    contentType(multipart.contentType)
                    setBody(multipart)
                }
            }
            check(authSession.state.value == AuthSessionState.Authenticated && authSession.identity.value === requestIdentity) {
                "로그인 상태가 바뀌었어요."
            }
            val body = response.decodeOrThrow<JsonElement>()
            if (!body.isSuccess) {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message,
                )
            }
            if (response.status != HttpStatusCode.OK || body.code != "ROOM200") {
                throw RoomRepositoryException(
                    statusCode = response.status.value,
                    code = body.code,
                    message = body.message.ifBlank { "모임 수정 응답이 올바르지 않아요." },
                )
            }
            val result = checkNotNull(body.result) { "모임 수정 응답이 비어 있어요." }
            val updated = ApiJson.decodeFromJsonElement<RoomUpdateResponseEntity>(result).toDomain()
            check(updated.roomId == roomId) { "수정한 모임 ID가 요청과 달라요." }
            return updated
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }


    override suspend fun deleteRoom(roomId: Long) {
        performRoomDeleteAction(roomId, path = "/api/v1/rooms/$roomId")
    }

    override suspend fun leaveRoom(roomId: Long) {
        performRoomDeleteAction(roomId, path = "/api/v1/rooms/$roomId/members")
    }

    private suspend fun performRoomDeleteAction(roomId: Long, path: String) {
        if (roomId <= 0L) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.BadRequest.value,
                code = "COMMON400",
                message = "모임 ID를 확인해 주세요.",
            )
        }
        ensurePostAuthenticated()
        try {
            val response = authenticatedRequests.execute { accessToken ->
                httpClient.delete(ApiConfig.url(path)) {
                    bearerAuth(accessToken)
                }
            }
            response.decodeRoomActionOrThrow()
        } catch (error: ApiException) {
            throw error.toRoomRepositoryException()
        }
    }

    private suspend fun ensurePostAuthenticated() {
        val requestIdentity = authSession.identity.value
        val tokens = tokenStorage.read()
        if (
            authSession.state.value != AuthSessionState.Authenticated ||
            requestIdentity == null ||
            authSession.identity.value !== requestIdentity ||
            tokens?.accessToken.isNullOrBlank()
        ) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.Unauthorized.value,
                code = RoomUnauthorizedCode,
                message = "로그인이 필요한 기능이에요.",
            )
        }
    }

    private suspend fun ensureGetAuthenticated() {
        val requestIdentity = authSession.identity.value
        val tokens = tokenStorage.read()
        if (
            authSession.state.value != AuthSessionState.Authenticated ||
            requestIdentity == null ||
            authSession.identity.value !== requestIdentity ||
            tokens?.accessToken.isNullOrBlank()
        ) {
            throw RoomRepositoryException(
                statusCode = HttpStatusCode.Unauthorized.value,
                code = RoomUnauthorizedCode,
                message = "로그인이 필요한 기능이에요.",
            )
        }
    }
}

private fun RoomUpdateInput.toMultipartContent(): MultiPartFormDataContent = MultiPartFormDataContent(formData {
    name?.let { append("name", it) }
    description?.let { append("description", it) }
    append("removeImage", removeImage.toString())
    image?.let { upload ->
        append("image", upload.bytes, Headers.build {
            append(HttpHeaders.ContentType, upload.contentType)
            append(HttpHeaders.ContentDisposition, "filename=\"${upload.fileName}\"")
        })
    }
})

private const val RoomUnauthorizedCode = "ROOM401"

private fun ApiException.toRoomRepositoryException(): RoomRepositoryException = RoomRepositoryException(
    statusCode = statusCode,
    code = code,
    message = apiMessage,
    result = result?.toString(),
    cause = this,
)

private fun invalidManagementResponse(cause: Throwable? = null) = RoomRepositoryException(
    statusCode = HttpStatusCode.OK.value,
    code = "ROOM_MEMBER_RESPONSE_INVALID",
    message = "회원 정보를 불러오지 못했어요. 다시 시도해 주세요.",
    cause = cause,
)

private fun invalidMemberMovementsResponse(cause: Throwable? = null) = RoomRepositoryException(
    statusCode = HttpStatusCode.OK.value,
    code = "ROOM_MEMBER_MOVEMENTS_RESPONSE_INVALID",
    message = "회원 이동 상태를 불러오지 못했어요. 다시 시도해 주세요.",
    cause = cause,
)

private suspend fun HttpResponse.decodeRoomActionOrThrow() {
    val body = decodeOrThrow<JsonElement>()
    if (!body.isSuccess) {
        throw RoomRepositoryException(
            statusCode = status.value,
            code = body.code,
            message = body.message,
        )
    }
    check(status == HttpStatusCode.OK) { "모임 요청에 실패했어요." }
    check(body.code == "ROOM200") { body.message }
}

private suspend fun HttpResponse.decodeMembershipOrThrow(
    emptyResultMessage: String,
    allowedRoles: Set<RoomMembershipRole>,
): RoomMembershipDetails {
    val body = decodeOrThrow<RoomMembershipResponseEntity>()
    check(status == HttpStatusCode.Created) { "모임 요청에 실패했어요." }
    check(body.isSuccess) { body.message }
    check(body.code == "ROOM201") { body.message }
    val result = checkNotNull(body.result) { emptyResultMessage }
    return result.toDomain(allowedRoles)
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
