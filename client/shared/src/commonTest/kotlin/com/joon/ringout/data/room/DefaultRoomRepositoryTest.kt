package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultRoomRepositoryTest {
    @Test
    fun `로그인 상태면 Bearer 토큰으로 전체 목록을 조회하고 서버 순서를 보존한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/rooms", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertTrue(request.body !is TextContent)
            respond(
                content = successBody(listOf(roomJson(2, false), roomJson(1, true))),
                headers = jsonHeaders,
            )
        }) { configureRingoutHttpClient() }
        val repository = DefaultRoomRepository(client, TestTokenStorage(AuthTokens("access", "refresh")), session(AuthSessionState.Authenticated))

        val rooms = repository.getRooms()

        assertEquals(listOf(2L, 1L), rooms.map { it.id })
        assertEquals(listOf(false, true), rooms.map { it.isJoined })
        client.close()
    }

    @Test
    fun `빈 서버 목록은 빈 Domain 목록으로 성공한다`() = runTest {
        val client = clientFor { respond(successBody(emptyList()), headers = jsonHeaders) }

        assertTrue(repository(client).getRooms().isEmpty())

        client.close()
    }

    @Test
    fun `비로그인 성공 응답의 미참여 상태를 반영하고 앱 세션은 변경하지 않는다`() = runTest {
        val session = session(AuthSessionState.Authenticated)
        val tokens = AuthTokens("invalid-access", "refresh")
        val storage = TestTokenStorage(tokens)
        val client = clientFor {
            respond(successBody(listOf(roomJson(1, false))), headers = jsonHeaders)
        }
        val repository = DefaultRoomRepository(client, storage, session)

        val room = repository.getRooms().single()

        assertFalse(room.isJoined)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        assertEquals(tokens, storage.read())
        client.close()
    }

    @Test
    fun `인증 상태와 토큰 유무에 따라 선택적으로 인증 헤더를 보낸다`() = runTest {
        val cases = listOf(
            AuthCase(AuthSessionState.Authenticated, AuthTokens("access", "refresh"), "Bearer access"),
            AuthCase(AuthSessionState.Authenticated, null, null),
            AuthCase(AuthSessionState.Unauthenticated, AuthTokens("access", "refresh"), null),
            AuthCase(AuthSessionState.ReauthenticationRequired, AuthTokens("access", "refresh"), null),
            AuthCase(AuthSessionState.Restoring, AuthTokens("access", "refresh"), null),
        )

        cases.forEach { case ->
            val client = HttpClient(MockEngine { request ->
                assertEquals(case.authorization, request.headers[HttpHeaders.Authorization])
                assertTrue(request.url.parameters.isEmpty())
                respond(successBody(emptyList()), headers = jsonHeaders)
            }) { configureRingoutHttpClient() }

            repository(client, case.tokens, case.state).getRooms()
            client.close()
        }
    }

    @Test
    fun `HTTP 오류는 서버 오류 정보를 담은 ApiException으로 전달한다`() = runTest {
        val client = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM500","message":"목록 조회 실패","result":"detail"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }

        val error = assertFailsWith<ApiException> { repository(client).getRooms() }

        assertEquals(500, error.statusCode)
        assertEquals("ROOM500", error.code)
        assertEquals("목록 조회 실패", error.apiMessage)
        assertEquals("detail", error.result?.toString()?.trim('"'))
        client.close()
    }

    @Test
    fun `업무 실패 응답을 조회 성공으로 처리하지 않는다`() = runTest {
        val client = clientFor {
            respond(
                """{"isSuccess":false,"code":"ROOM_ERROR","message":"조회 실패","result":null}""",
                headers = jsonHeaders,
            )
        }

        assertFailsWith<IllegalStateException> { repository(client).getRooms() }

        client.close()
    }

    @Test
    fun `결과가 null인 성공 응답은 조회 실패로 처리한다`() = runTest {
        val client = clientFor {
            respond("""{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""", headers = jsonHeaders)
        }

        assertFailsWith<IllegalStateException> { repository(client).getRooms() }

        client.close()
    }

    @Test
    fun `필수 목록 필드가 없거나 응답 JSON이 잘못되면 파싱 오류를 전달한다`() = runTest {
        val missingRoomsClient = clientFor {
            respond("""{"isSuccess":true,"code":"ROOM200","message":"성공","result":{}}""", headers = jsonHeaders)
        }
        val missingRoomFieldClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"ROOM200","message":"성공","result":{"rooms":[{"roomId":1}]}}""",
                headers = jsonHeaders,
            )
        }
        val invalidJsonClient = clientFor { respond("not json", headers = jsonHeaders) }

        assertFailsWith<SerializationException> { repository(missingRoomsClient).getRooms() }
        assertFailsWith<SerializationException> { repository(missingRoomFieldClient).getRooms() }
        assertFailsWith<SerializationException> { repository(invalidJsonClient).getRooms() }

        missingRoomsClient.close()
        missingRoomFieldClient.close()
        invalidJsonClient.close()
    }

    @Test
    fun `취소 예외를 빈 목록이나 일반 조회 실패로 바꾸지 않는다`() = runTest {
        val client = clientFor { throw CancellationException("요청 취소") }

        assertFailsWith<CancellationException> { repository(client).getRooms() }

        client.close()
    }
}

private data class AuthCase(
    val state: AuthSessionState,
    val tokens: AuthTokens?,
    val authorization: String?,
)

private fun clientFor(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    HttpClient(MockEngine(handler)) { configureRingoutHttpClient() }

private fun repository(
    client: HttpClient,
    tokens: AuthTokens? = null,
    state: AuthSessionState = AuthSessionState.Unauthenticated,
) = DefaultRoomRepository(client, TestTokenStorage(tokens), session(state))

private fun session(state: AuthSessionState) = AuthSession().apply {
    when (state) {
        AuthSessionState.Restoring -> Unit
        AuthSessionState.Unauthenticated -> clear()
        AuthSessionState.Authenticated -> markAuthenticated()
        AuthSessionState.ReauthenticationRequired -> requireReauthentication()
    }
}

private class TestTokenStorage(private val tokens: AuthTokens?) : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) = Unit
    override suspend fun read(): AuthTokens? = tokens
    override suspend fun clear() = Unit
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun successBody(rooms: List<String>) =
    """{"isSuccess":true,"code":"ROOM200","message":"성공","result":{"rooms":[${rooms.joinToString()}]}}"""

private fun roomJson(id: Long, isJoined: Boolean) =
    """{"roomId":$id,"name":"모임 $id","description":null,"imageUrl":"/images/default-room.png","activityDays":["MONDAY"],"activityTime":"08:00","memberCount":12,"isJoined":$isJoined,"createdAt":"2026-09-20T10:30:00"}"""
