package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    @Test
    fun `모임 생성은 필수 Bearer 토큰과 JSON 본문으로 요청하고 OWNER 응답을 반환한다`() = runTest {
        var requestCount = 0
        val client = clientFor { request ->
            requestCount += 1
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/rooms", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            val json = ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("아침운동모임", json["name"]?.jsonPrimitive?.content)
            assertEquals("함께 운동해요", json["description"]?.jsonPrimitive?.content)
            assertEquals(
                listOf("MONDAY", "WEDNESDAY", "FRIDAY"),
                json["activityDays"]?.jsonArray?.map { it.jsonPrimitive.content },
            )
            assertEquals("08:00", json["activityTime"]?.jsonPrimitive?.content)
            respond(
                content = membershipSuccessBody(role = "OWNER"),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        val result = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .createRoom(createInput())

        assertEquals(1, requestCount)
        assertEquals(1L, result.room.id)
        assertTrue(result.room.isJoined)
        assertEquals(RoomMembershipRole.OWNER, result.membershipRole)
        assertEquals(listOf(10L), result.members.map { it.userId })
        assertEquals(listOf("방장"), result.members.map { it.nickname })
        client.close()
    }

    @Test
    fun `모임 가입은 요청 ID를 경로에만 담고 MEMBER 응답을 반환한다`() = runTest {
        val client = clientFor { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/rooms/7/members", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertTrue(request.body !is TextContent)
            respond(
                content = membershipSuccessBody(roomId = 7, role = "MEMBER", memberCount = 3),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        val result = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .joinRoom(7)

        assertEquals(7L, result.room.id)
        assertEquals(3, result.room.memberCount)
        assertEquals(RoomMembershipRole.MEMBER, result.membershipRole)
        client.close()
    }

    @Test
    fun `가입 응답 ID가 요청 ID와 다르면 실패한다`() = runTest {
        val client = clientFor {
            respond(
                content = membershipSuccessBody(roomId = 8, role = "MEMBER"),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        assertFailsWith<IllegalStateException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(7)
        }

        client.close()
    }

    @Test
    fun `모임 생성과 가입은 인증 상태가 아니거나 토큰이 없으면 HTTP 요청을 보내지 않는다`() = runTest {
        var requestCount = 0
        val client = clientFor {
            requestCount += 1
            respond(membershipSuccessBody(), status = HttpStatusCode.Created, headers = jsonHeaders)
        }
        val unauthenticatedSession = session(AuthSessionState.Unauthenticated)
        val unauthenticatedRepository = DefaultRoomRepository(
            client,
            TestTokenStorage(AuthTokens("access", "refresh")),
            unauthenticatedSession,
        )
        val missingTokenRepository = repository(client, null, AuthSessionState.Authenticated)
        val restoringRepository = DefaultRoomRepository(
            client,
            TestTokenStorage(AuthTokens("access", "refresh")),
            session(AuthSessionState.Restoring),
        )

        assertFailsWith<RoomRepositoryException> { unauthenticatedRepository.createRoom(createInput()) }
        assertFailsWith<RoomRepositoryException> { unauthenticatedRepository.joinRoom(1) }
        assertFailsWith<RoomRepositoryException> { missingTokenRepository.createRoom(createInput()) }
        assertFailsWith<RoomRepositoryException> { missingTokenRepository.joinRoom(1) }
        assertFailsWith<RoomRepositoryException> { restoringRepository.createRoom(createInput()) }
        assertFailsWith<RoomRepositoryException> { restoringRepository.joinRoom(1) }

        assertEquals(0, requestCount)
        assertEquals(AuthSessionState.Unauthenticated, unauthenticatedSession.state.value)
        client.close()
    }

    @Test
    fun `유효하지 않은 가입 roomId는 HTTP 요청 전에 실패한다`() = runTest {
        var requestCount = 0
        val client = clientFor {
            requestCount += 1
            respond(membershipSuccessBody(), status = HttpStatusCode.Created, headers = jsonHeaders)
        }

        assertFailsWith<IllegalArgumentException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(0)
        }

        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun `POST HTTP 오류는 서버 오류 정보를 담은 Domain 예외로 전달한다`() = runTest {
        val client = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM403","message":"가입할 수 없는 모임입니다.","result":null}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders,
            )
        }

        val error = assertFailsWith<RoomRepositoryException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(1)
        }

        assertEquals(403, error.statusCode)
        assertEquals("ROOM403", error.code)
        assertEquals("가입할 수 없는 모임입니다.", error.message)
        client.close()
    }

    @Test
    fun `POST의 400 404 409 500 응답은 각각의 서버 코드를 보존한다`() = runTest {
        val failures = listOf(
            HttpStatusCode.BadRequest to "ROOM400",
            HttpStatusCode.NotFound to "ROOM404",
            HttpStatusCode.Conflict to "ROOM409",
            HttpStatusCode.InternalServerError to "ROOM500",
        )

        failures.forEach { (status, code) ->
            val client = clientFor {
                respond(
                    content = """{"isSuccess":false,"code":"$code","message":"요청 실패","result":null}""",
                    status = status,
                    headers = jsonHeaders,
                )
            }

            val error = assertFailsWith<RoomRepositoryException> {
                repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(1)
            }

            assertEquals(status.value, error.statusCode)
            assertEquals(code, error.code)
            client.close()
        }
    }

    @Test
    fun `ROOM401은 같은 로그인 세션을 재인증 필요 상태로 바꾼다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = TestTokenStorage(AuthTokens("access", "refresh"))
        val client = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM401","message":"다시 로그인해 주세요.","result":null}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }

        assertFailsWith<RoomRepositoryException> {
            DefaultRoomRepository(client, storage, session).joinRoom(1)
        }

        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        assertEquals(null, session.identity.value)
        assertEquals(null, storage.read())
        client.close()
    }

    @Test
    fun `ROOM401이 늦게 도착해 로그인 계정이 바뀌었으면 새 세션을 만료하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = TestTokenStorage(AuthTokens("access", "refresh"))
        val client = clientFor {
            session.startNewSession()
            respond(
                content = """{"isSuccess":false,"code":"ROOM401","message":"다시 로그인해 주세요.","result":null}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }

        assertFailsWith<RoomRepositoryException> {
            DefaultRoomRepository(client, storage, session).joinRoom(1)
        }

        assertEquals(AuthSessionState.Authenticated, session.state.value)
        assertTrue(session.identity.value != null)
        assertEquals(AuthTokens("access", "refresh"), storage.read())
        client.close()
    }

    @Test
    fun `POST 성공 응답의 code와 result와 role을 엄격하게 검증한다`() = runTest {
        val invalidCodeClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"ROOM200","message":"성공","result":${membershipResultJson()}}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }
        val nullResultClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"ROOM201","message":"성공","result":null}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }
        val invalidRoleClient = clientFor {
            respond(
                membershipSuccessBody(role = "MANAGER"),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }
        val memberCreateClient = clientFor {
            respond(
                membershipSuccessBody(role = "MEMBER"),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }
        val okBodyWrongStatusClient = clientFor {
            respond(
                membershipSuccessBody(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        assertFailsWith<IllegalStateException> {
            repository(invalidCodeClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).createRoom(createInput())
        }
        assertFailsWith<IllegalStateException> {
            repository(nullResultClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).createRoom(createInput())
        }
        assertFailsWith<IllegalStateException> {
            repository(invalidRoleClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(1)
        }
        assertFailsWith<IllegalStateException> {
            repository(memberCreateClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).createRoom(createInput())
        }
        assertFailsWith<IllegalStateException> {
            repository(okBodyWrongStatusClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).joinRoom(1)
        }

        invalidCodeClient.close()
        nullResultClient.close()
        invalidRoleClient.close()
        memberCreateClient.close()
        okBodyWrongStatusClient.close()
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

private class TestTokenStorage(tokens: AuthTokens?) : SecureTokenStorage {
    private var currentTokens: AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { currentTokens = tokens }
    override suspend fun read(): AuthTokens? = currentTokens
    override suspend fun clear() { currentTokens = null }
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun successBody(rooms: List<String>) =
    """{"isSuccess":true,"code":"ROOM200","message":"성공","result":{"rooms":[${rooms.joinToString()}]}}"""

private fun roomJson(id: Long, isJoined: Boolean) =
    """{"roomId":$id,"name":"모임 $id","description":null,"imageUrl":"/images/default-room.png","activityDays":["MONDAY"],"activityTime":"08:00","memberCount":12,"isJoined":$isJoined,"createdAt":"2026-09-20T10:30:00"}"""

private fun createInput() = RoomCreateInput(
    name = "아침운동모임",
    description = "함께 운동해요",
    activityDays = listOf("MONDAY", "WEDNESDAY", "FRIDAY"),
    activityTime = "08:00",
)

private fun membershipSuccessBody(
    roomId: Long = 1,
    role: String = "OWNER",
    memberCount: Int = 1,
) = """{"isSuccess":true,"code":"ROOM201","message":"성공","result":${membershipResultJson(roomId, role, memberCount)}}"""

private fun membershipResultJson(
    roomId: Long = 1,
    role: String = "OWNER",
    memberCount: Int = 1,
) = """{"roomId":$roomId,"name":"아침운동모임","description":"함께 운동해요","imageUrl":"/images/default-room.png","activityDays":["MONDAY","WEDNESDAY","FRIDAY"],"activityTime":"08:00","memberCount":$memberCount,"membershipRole":"$role","createdAt":"2026-10-01T08:30:00","members":[{"userId":10,"nickname":"방장"}]}"""
