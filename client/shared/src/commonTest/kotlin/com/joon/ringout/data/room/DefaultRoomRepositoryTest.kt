package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomImageUpload
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomUpdateInput
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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
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
    fun `모임 수정은 변경된 텍스트와 원본 이미지 파일 그리고 removeImage false를 멀티파트로 전송한다`() = runTest {
        val fixture = RoomUpdateFixture()

        val result = fixture.repository.updateRoom(
            roomId = 7,
            input = RoomUpdateInput(
                name = "새 모임",
                description = "",
                image = RoomImageUpload("original-image-bytes".encodeToByteArray(), "image/png", "room.png"),
            ),
        )

        assertEquals("/api/v1/rooms/7", fixture.patchPaths.single())
        assertEquals("Bearer access", fixture.patchAuthorizations.single())
        assertTrue(fixture.patchContentTypes.single().startsWith("multipart/form-data; boundary="))
        val body = fixture.patchBodies.single()
        assertMultipartPart(body, "name", "새 모임")
        assertMultipartPart(body, "description", "")
        assertMultipartPart(body, "removeImage", "false")
        assertTrue(body.contains("name=\"image\""))
        assertTrue(body.contains("filename=\"room.png\""))
        assertTrue(body.contains("Content-Type: image/png", ignoreCase = true))
        assertTrue(body.contains("original-image-bytes"))
        assertEquals(7L, result.roomId)
        assertEquals("서버 수정 이름", result.name)
        assertEquals(null, result.description)
        assertEquals("${ApiConfig.BASE_URL}/images/rooms/updated.png", result.imageUrl)
        fixture.client.close()
    }

    @Test
    fun `모임 수정은 생략한 텍스트 필드를 넣지 않고 빈 소개와 removeImage false를 전송한다`() = runTest {
        val fixture = RoomUpdateFixture()

        fixture.repository.updateRoom(7, RoomUpdateInput(description = ""))

        val body = fixture.patchBodies.single()
        assertMultipartPart(body, "description", "")
        assertMultipartPart(body, "removeImage", "false")
        assertFalse(body.contains("name=\"name\""))
        assertFalse(body.contains("name=\"image\""))
        fixture.client.close()
    }

    @Test
    fun `모임 이름만 수정해도 removeImage false를 한 번 전송한다`() = runTest {
        val fixture = RoomUpdateFixture()

        fixture.repository.updateRoom(7, RoomUpdateInput(name = "새 모임"))

        val body = fixture.patchBodies.single()
        assertMultipartPart(body, "name", "새 모임")
        assertMultipartPart(body, "removeImage", "false")
        assertFalse(body.contains("name=\"description\""))
        assertFalse(body.contains("name=\"image\""))
        fixture.client.close()
    }

    @Test
    fun `모임 소개만 수정해도 removeImage false를 한 번 전송한다`() = runTest {
        val fixture = RoomUpdateFixture()

        fixture.repository.updateRoom(7, RoomUpdateInput(description = "새 소개"))

        val body = fixture.patchBodies.single()
        assertMultipartPart(body, "description", "새 소개")
        assertMultipartPart(body, "removeImage", "false")
        assertFalse(body.contains("name=\"name\""))
        assertFalse(body.contains("name=\"image\""))
        fixture.client.close()
    }

    @Test
    fun `이미지만 교체해도 removeImage false와 이미지 정보를 전송한다`() = runTest {
        val fixture = RoomUpdateFixture()
        val upload = RoomImageUpload("replacement-image".encodeToByteArray(), "image/jpeg", "replacement.jpg")

        fixture.repository.updateRoom(7, RoomUpdateInput(image = upload))

        val body = fixture.patchBodies.single()
        assertMultipartPart(body, "removeImage", "false")
        assertTrue(body.contains("name=\"image\""))
        assertTrue(body.contains("filename=\"replacement.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg", ignoreCase = true))
        assertTrue(body.contains("replacement-image"))
        assertFalse(body.contains("name=\"name\""))
        assertFalse(body.contains("name=\"description\""))
        fixture.client.close()
    }

    @Test
    fun `모임 수정은 비로그인 토큰 없음 잘못된 입력에서 요청하지 않는다`() = runTest {
        var calls = 0
        val client = clientFor { calls += 1; error("요청하면 안 됨") }
        val authenticated = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
        val unauthenticated = repository(client)
        val missingToken = repository(client, state = AuthSessionState.Authenticated)

        assertFalse(RoomUpdateInput().hasChanges)
        assertFailsWith<RoomRepositoryException> { authenticated.updateRoom(0, RoomUpdateInput(name = "새 모임")) }
        assertFailsWith<RoomRepositoryException> { authenticated.updateRoom(7, RoomUpdateInput()) }
        assertFailsWith<RoomRepositoryException> { unauthenticated.updateRoom(7, RoomUpdateInput(name = "새 모임")) }
        assertFailsWith<RoomRepositoryException> { missingToken.updateRoom(7, RoomUpdateInput(name = "새 모임")) }
        assertEquals(0, calls)
        client.close()
    }

    @Test
    fun `모임 수정은 성공 응답의 상태 코드 성공 코드 result와 roomId를 엄격하게 검증한다`() = runTest {
        val invalidResponses = listOf(
            Triple(HttpStatusCode.Created, "ROOM200", updateRoomResultJson()),
            Triple(HttpStatusCode.OK, "ROOM201", updateRoomResultJson()),
            Triple(HttpStatusCode.OK, "ROOM200", "null"),
            Triple(HttpStatusCode.OK, "ROOM200", updateRoomResultJson(roomId = 8)),
        )

        invalidResponses.forEach { (status, code, resultJson) ->
            val client = clientFor {
                respond(
                    """{"isSuccess":true,"code":"$code","message":"성공","result":$resultJson}""",
                    status = status,
                    headers = jsonHeaders,
                )
            }

            assertFailsWith<Exception> {
                repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                    .updateRoom(7, RoomUpdateInput(name = "새 모임"))
            }
            client.close()
        }
    }

    @Test
    fun `모임 수정의 HTTP 오류와 업무 실패는 RoomRepositoryException으로 전달한다`() = runTest {
        val businessClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"ROOM403","message":"방장 권한이 필요합니다.","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val httpClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"ROOM404","message":"모임을 찾을 수 없습니다.","result":null}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }

        val businessError = assertFailsWith<RoomRepositoryException> {
            repository(businessClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                .updateRoom(7, RoomUpdateInput(name = "새 모임"))
        }
        val httpError = assertFailsWith<RoomRepositoryException> {
            repository(httpClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                .updateRoom(7, RoomUpdateInput(name = "새 모임"))
        }

        assertEquals(200, businessError.statusCode)
        assertEquals("ROOM403", businessError.code)
        assertEquals(404, httpError.statusCode)
        assertEquals("ROOM404", httpError.code)
        businessClient.close()
        httpClient.close()
    }

    @Test
    fun `모임 수정은 토큰 재발급 후 같은 멀티파트 내용을 다시 전송한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = TestTokenStorage(AuthTokens("old-access", "refresh"))
        val bodies = mutableListOf<String>()
        var patchRequestCount = 0
        var reissueRequestCount = 0
        val client = clientFor { request ->
            when (request.url.encodedPath) {
                "/api/v1/rooms/7" -> {
                    patchRequestCount += 1
                    bodies += request.readBodyText()
                    when (request.headers[HttpHeaders.Authorization]) {
                        "Bearer old-access" -> respond(
                            """{"isSuccess":false,"code":"AUTH401","message":"액세스 토큰이 만료되었습니다.","result":null}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                        "Bearer new-access" -> respond(
                            """{"isSuccess":true,"code":"ROOM200","message":"성공","result":${updateRoomResultJson()}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                        else -> error("예상하지 않은 Authorization 헤더입니다: ${request.headers[HttpHeaders.Authorization]}")
                    }
                }
                "/api/v1/auth/reissue" -> {
                    reissueRequestCount += 1
                    respond(
                        """{"isSuccess":true,"code":"COMMON200","message":"성공","result":{"accessToken":"new-access","refreshToken":"new-refresh"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                else -> error("예상하지 않은 요청입니다: ${request.url.encodedPath}")
            }
        }

        DefaultRoomRepository(client, storage, session).updateRoom(
            7,
            RoomUpdateInput(
                name = "새 모임",
                image = RoomImageUpload("retry-image".encodeToByteArray(), "image/jpeg", "retry.jpg"),
            ),
        )

        assertEquals(2, patchRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(AuthTokens("new-access", "new-refresh"), storage.read())
        assertTrue(bodies.all { it.contains("새 모임") && it.contains("retry-image") })
        bodies.forEach { assertMultipartPart(it, "removeImage", "false") }
        client.close()
    }

    @Test
    fun `모임 수정 응답 전에 계정이 바뀌면 결과를 반환하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = TestTokenStorage(AuthTokens("access", "refresh"))
        val client = clientFor {
            session.startNewSession()
            respond(
                """{"isSuccess":true,"code":"ROOM200","message":"성공","result":${updateRoomResultJson()}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        assertFailsWith<IllegalStateException> {
            DefaultRoomRepository(client, storage, session)
                .updateRoom(7, RoomUpdateInput(name = "새 모임"))
        }
        client.close()
    }

    @Test
    fun `모임 기록은 인증 헤더와 선택 날짜로 조회하고 여섯 이벤트와 빈 회원을 변환한다`() = runTest {
        val events = RoomRecordEvent.entries.map { event ->
            """{"event":"${event.name}","occurredAt":"2026-10-01T08:00:00+09:00","count":${if (event == RoomRecordEvent.ALARM_RINGING) 1 else "null"}}"""
        }
        val client = clientFor { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/rooms/7/records", request.url.encodedPath)
            assertEquals("2026-10-01", request.url.parameters["date"])
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            respond(
                """{"isSuccess":true,"code":"RECORD200","message":"성공","result":{"memberRecords":[
                  {"userId":1,"nickname":"회원","profileImageUrl":"https://example.com/avatar","records":[${events.joinToString()}]},
                  {"userId":2,"nickname":"빈 회원","records":[]}]}}""",
                headers = jsonHeaders,
            )
        }
        val result = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .getRoomRecords(7, MissionDate.parse("2026-10-01"))
        assertEquals(RoomRecordEvent.entries.toList(), result.members.first().records.map { it.event })
        assertEquals(1, result.members.first().records[1].repeatCount)
        assertEquals("https://example.com/avatar", result.members.first().profileImageUrl)
        assertTrue(result.members.last().records.isEmpty())
        assertEquals(null, result.members.last().profileImageUrl)
        client.close()
    }

    @Test
    fun `기록 조회는 비로그인 토큰 없음 잘못된 모임 ID에서 요청하지 않는다`() = runTest {
        var calls = 0
        val client = clientFor { calls++; error("요청하면 안 됨") }
        val date = MissionDate.parse("2026-10-01")
        assertFailsWith<RoomRepositoryException> { repository(client).getRoomRecords(7, date) }
        assertFailsWith<RoomRepositoryException> {
            repository(client, state = AuthSessionState.Authenticated).getRoomRecords(7, date)
        }
        assertFailsWith<IllegalArgumentException> { repository(client).getRoomRecords(0, date) }
        assertEquals(0, calls)
        client.close()
    }

    @Test
    fun `기록 API의 날짜 인증 권한 삭제 서버 오류는 상태와 코드를 보존한다`() = runTest {
        for ((status, code) in listOf(400 to "RECORD400", 401 to "RECORD401", 403 to "RECORD403", 404 to "ROOM404", 500 to "COMMON500")) {
            val client = clientFor {
                respond("""{"isSuccess":false,"code":"$code","message":"실패"}""", HttpStatusCode.fromValue(status), jsonHeaders)
            }
            val error = assertFailsWith<RoomRepositoryException> {
                repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                    .getRoomRecords(7, MissionDate.parse("2026-10-01"))
            }
            assertEquals(status, error.statusCode)
            assertEquals(code, error.code)
            client.close()
        }
    }

    @Test
    fun `기록 응답의 빈 결과와 잘못된 이벤트 시간은 정상 빈 기록으로 숨기지 않는다`() = runTest {
        val invalidResults = listOf(
            "null",
            """{"memberRecords":[{"userId":1,"nickname":"회원","records":[{"event":"UNKNOWN","occurredAt":"2026-10-01T00:00:00Z"}]}]}""",
            """{"memberRecords":[{"userId":1,"nickname":"회원","records":[{"event":"ARRIVED","occurredAt":"잘못된 시간"}]}]}""",
            """{"memberRecords":[{"userId":1,"nickname":"회원","records":[{"event":"ALARM_RINGING","occurredAt":"2026-10-01T00:00:00Z","count":0}]}]}""",
        )
        for (result in invalidResults) {
            val client = clientFor {
                respond("""{"isSuccess":true,"code":"RECORD200","message":"성공","result":$result}""", headers = jsonHeaders)
            }
            assertFailsWith<Exception> {
                repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                    .getRoomRecords(7, MissionDate.parse("2026-10-01"))
            }
            client.close()
        }
    }

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
    fun `회원 관리 조회는 MEMBER200 응답과 서버 순서를 그대로 반환한다`() = runTest {
        var requestCount = 0
        val client = clientFor { request ->
            requestCount += 1
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/rooms/7/members", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertTrue(request.body !is TextContent)
            respond(
                content = """{"isSuccess":true,"code":"MEMBER200","message":"성공","result":{"members":[{"userId":11,"nickname":"방장","profileImageUrl":"/images/owner.png","joinedAt":"2026-09-20T10:30:00","membershipRole":"OWNER"},{"userId":10,"nickname":"회원","profileImageUrl":null,"joinedAt":"2026-09-21T14:20:00","membershipRole":"MEMBER"}]}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val members = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .getMembersForManagement(7)

        assertEquals(1, requestCount)
        assertEquals(listOf(11L, 10L), members.map { it.userId })
        assertEquals(listOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER), members.map { it.membershipRole })
        assertEquals("${ApiConfig.BASE_URL}/images/owner.png", members.first().profileImageUrl)
        assertEquals("2026-09-20T10:30:00", members.first().joinedAt)
        client.close()
    }

    @Test
    fun `회원 관리 조회의 빈 목록은 성공하고 null result와 잘못된 코드는 실패한다`() = runTest {
        val emptyClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"MEMBER200","message":"성공","result":{"members":[]}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val nullResultClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"MEMBER200","message":"성공","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val wrongCodeClient = clientFor {
            respond(
                """{"isSuccess":true,"code":"ROOM200","message":"성공","result":{"members":[]}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        assertTrue(repository(emptyClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .getMembersForManagement(7).isEmpty())
        assertFailsWith<RoomRepositoryException> {
            repository(nullResultClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                .getMembersForManagement(7)
        }
        assertFailsWith<RoomRepositoryException> {
            repository(wrongCodeClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
                .getMembersForManagement(7)
        }
        emptyClient.close()
        nullResultClient.close()
        wrongCodeClient.close()
    }

    @Test
    fun `회원 추방은 정확한 userId를 전송하고 null result를 성공으로 처리한다`() = runTest {
        val client = clientFor { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/rooms/7/kick", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            val json = ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("10", json["userId"]?.jsonPrimitive?.content)
            respond(
                """{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)
            .kickMember(roomId = 7, userId = 10)

        client.close()
    }

    @Test
    fun `관리 조회와 추방의 HTTP 오류와 업무 실패를 RoomRepositoryException으로 전달한다`() = runTest {
        val businessGetClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"MEMBER403","message":"방장 권한이 필요합니다.","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val httpGetClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"MEMBER403","message":"방장 권한이 필요합니다.","result":null}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders,
            )
        }
        val businessKickClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"ROOM400","message":"현재 참여자가 아닙니다.","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val httpKickClient = clientFor {
            respond(
                """{"isSuccess":false,"code":"ROOM400","message":"현재 참여자가 아닙니다.","result":null}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }
        val tokens = AuthTokens("access", "refresh")

        val businessGetError = assertFailsWith<RoomRepositoryException> {
            repository(businessGetClient, tokens, AuthSessionState.Authenticated).getMembersForManagement(7)
        }
        val httpGetError = assertFailsWith<RoomRepositoryException> {
            repository(httpGetClient, tokens, AuthSessionState.Authenticated).getMembersForManagement(7)
        }
        val businessKickError = assertFailsWith<RoomRepositoryException> {
            repository(businessKickClient, tokens, AuthSessionState.Authenticated).kickMember(7, 10)
        }
        val httpKickError = assertFailsWith<RoomRepositoryException> {
            repository(httpKickClient, tokens, AuthSessionState.Authenticated).kickMember(7, 10)
        }

        assertEquals("MEMBER403", businessGetError.code)
        assertEquals(200, businessGetError.statusCode)
        assertEquals("MEMBER403", httpGetError.code)
        assertEquals(403, httpGetError.statusCode)
        assertEquals("ROOM400", businessKickError.code)
        assertEquals(200, businessKickError.statusCode)
        assertEquals("ROOM400", httpKickError.code)
        assertEquals(400, httpKickError.statusCode)
        listOf(businessGetClient, httpGetClient, businessKickClient, httpKickClient).forEach { it.close() }
    }

    @Test
    fun `회원 관리 API는 비양수 ID를 거부하고 취소를 보존한다`() = runTest {
        val unusedClient = clientFor { error("잘못된 식별자로 요청을 보내면 안 됩니다.") }
        val authenticatedRepository = repository(
            unusedClient,
            AuthTokens("access", "refresh"),
            AuthSessionState.Authenticated,
        )
        assertFailsWith<RoomRepositoryException> { authenticatedRepository.getMembersForManagement(0) }
        assertFailsWith<RoomRepositoryException> { authenticatedRepository.kickMember(7, 0) }
        unusedClient.close()

        val cancelledClient = clientFor { throw CancellationException("요청 취소") }
        val cancelledRepository = repository(
            cancelledClient,
            AuthTokens("access", "refresh"),
            AuthSessionState.Authenticated,
        )
        assertFailsWith<CancellationException> { cancelledRepository.getMembersForManagement(7) }
        assertFailsWith<CancellationException> { cancelledRepository.kickMember(7, 10) }
        cancelledClient.close()
    }

    @Test
    fun `추방 요청은 AUTH401이면 토큰을 갱신하고 한 번 재시도한다`() = runTest {
        var kickRequestCount = 0
        var reissueRequestCount = 0
        val storage = TestTokenStorage(AuthTokens("old-access", "old-refresh"))
        val authSession = session(AuthSessionState.Authenticated)
        val client = clientFor { request ->
            when (request.url.encodedPath) {
                "/api/v1/rooms/7/kick" -> {
                    kickRequestCount += 1
                    when (request.headers[HttpHeaders.Authorization]) {
                        "Bearer old-access" -> respond(
                            """{"isSuccess":false,"code":"AUTH401","message":"토큰 만료","result":null}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                        "Bearer new-access" -> respond(
                            """{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                        else -> error("예상하지 않은 Authorization 헤더입니다.")
                    }
                }
                "/api/v1/auth/reissue" -> {
                    reissueRequestCount += 1
                    respond(
                        """{"isSuccess":true,"code":"COMMON200","message":"성공","result":{"accessToken":"new-access","refreshToken":"new-refresh"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                else -> error("예상하지 않은 요청입니다: ${request.url.encodedPath}")
            }
        }

        DefaultRoomRepository(client, storage, authSession).kickMember(7, 10)

        assertEquals(2, kickRequestCount)
        assertEquals(1, reissueRequestCount)
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
    fun `모임 상세는 Bearer GET으로 조회하고 서버 회원 순서와 프로필 이미지를 반환한다`() = runTest {
        val client = clientFor { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/rooms/7", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertTrue(request.body !is TextContent)
            respond(
                content = detailSuccessBody(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).getRoom(7)

        assertEquals(7L, result.room.id)
        assertEquals("상세 응답 이름", result.room.name)
        assertEquals(null, result.room.description)
        assertEquals(3, result.room.memberCount)
        assertEquals("2026-10-01T08:30:00", result.room.createdAt)
        assertEquals(RoomMembershipRole.MEMBER, result.membershipRole)
        assertEquals(listOf(11L, 10L), result.members.map { it.userId })
        assertEquals(listOf("두 번째", "첫 번째"), result.members.map { it.nickname })
        assertEquals(
            "${ApiConfig.BASE_URL}/images/profile/member-11.png",
            result.members.first().profileImageUrl,
        )
        assertEquals(null, result.members.last().profileImageUrl)
        client.close()
    }

    @Test
    fun `잘못된 모임 ID와 인증 또는 토큰이 없으면 상세 HTTP 요청을 보내지 않는다`() = runTest {
        var requestCount = 0
        val client = clientFor {
            requestCount += 1
            respond(detailSuccessBody(), headers = jsonHeaders)
        }
        val authenticatedWithoutToken = repository(client, null, AuthSessionState.Authenticated)
        val unauthenticated = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Unauthenticated)

        assertFailsWith<RoomRepositoryException> { authenticatedWithoutToken.getRoom(1) }
        assertFailsWith<RoomRepositoryException> { unauthenticated.getRoom(1) }
        assertFailsWith<RoomRepositoryException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).getRoom(0)
        }

        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun `상세 HTTP 오류는 서버 오류 정보를 Domain 예외에 보존한다`() = runTest {
        val client = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM403","message":"참여하지 않은 방입니다.","result":null}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders,
            )
        }

        val error = assertFailsWith<RoomRepositoryException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).getRoom(7)
        }

        assertEquals(403, error.statusCode)
        assertEquals("ROOM403", error.code)
        assertEquals("참여하지 않은 방입니다.", error.message)
        client.close()
    }

    @Test
    fun `상세 API 업무 실패 응답도 성공 결과로 처리하지 않는다`() = runTest {
        val client = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM404","message":"삭제된 방입니다.","result":null}""",
                headers = jsonHeaders,
            )
        }

        val error = assertFailsWith<RoomRepositoryException> {
            repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).getRoom(7)
        }

        assertEquals("ROOM404", error.code)
        assertEquals(200, error.statusCode)
        client.close()
    }

    @Test
    fun `상세 성공 응답의 상태 코드와 API 코드와 ID와 참여 역할을 엄격히 검증한다`() = runTest {
        val invalidCodeClient = clientFor {
            respond(detailSuccessBody(code = "ROOM201"), headers = jsonHeaders)
        }
        val nullResultClient = clientFor {
            respond("""{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""", headers = jsonHeaders)
        }
        val mismatchedIdClient = clientFor {
            respond(detailSuccessBody(roomId = 8), headers = jsonHeaders)
        }
        val invalidRoleClient = clientFor {
            respond(detailSuccessBody(role = "MANAGER"), headers = jsonHeaders)
        }
        val wrongStatusClient = clientFor {
            respond(detailSuccessBody(), status = HttpStatusCode.Created, headers = jsonHeaders)
        }
        val tokens = AuthTokens("access", "refresh")

        listOf(invalidCodeClient, nullResultClient, mismatchedIdClient, invalidRoleClient, wrongStatusClient).forEach { client ->
            assertFailsWith<IllegalStateException> {
                repository(client, tokens, AuthSessionState.Authenticated).getRoom(7)
            }
        }

        invalidCodeClient.close()
        nullResultClient.close()
        mismatchedIdClient.close()
        invalidRoleClient.close()
        wrongStatusClient.close()
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

    @Test
    fun `모임 삭제와 탈퇴는 DELETE 요청에 Bearer 토큰과 경로만 담고 null result 성공을 허용한다`() = runTest {
        val cases = listOf(
            RoomActionCase("delete", "/api/v1/rooms/7"),
            RoomActionCase("leave", "/api/v1/rooms/7/members"),
        )

        cases.forEach { case ->
            var requestCount = 0
            val client = clientFor { request ->
                requestCount += 1
                assertEquals(HttpMethod.Delete, request.method)
                assertEquals(case.path, request.url.encodedPath)
                assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                assertTrue(request.url.parameters.isEmpty())
                assertTrue(request.body !is TextContent)
                respond(
                    content = """{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
            val repository = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)

            when (case.name) {
                "delete" -> repository.deleteRoom(7)
                "leave" -> repository.leaveRoom(7)
            }

            assertEquals(1, requestCount)
            client.close()
        }
    }

    @Test
    fun `잘못된 모임 ID와 인증 또는 토큰이 없으면 삭제와 탈퇴 HTTP 요청을 보내지 않는다`() = runTest {
        var requestCount = 0
        val client = clientFor {
            requestCount += 1
            respond("""{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""", headers = jsonHeaders)
        }
        val authenticatedWithoutToken = repository(client, null, AuthSessionState.Authenticated)
        val unauthenticated = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Unauthenticated)
        val authenticated = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)

        assertFailsWith<RoomRepositoryException> { authenticatedWithoutToken.deleteRoom(1) }
        assertFailsWith<RoomRepositoryException> { authenticatedWithoutToken.leaveRoom(1) }
        assertFailsWith<RoomRepositoryException> { unauthenticated.deleteRoom(1) }
        assertFailsWith<RoomRepositoryException> { unauthenticated.leaveRoom(1) }
        assertFailsWith<RoomRepositoryException> { authenticated.deleteRoom(0) }
        assertFailsWith<RoomRepositoryException> { authenticated.leaveRoom(-1) }

        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun `삭제와 탈퇴 HTTP 오류는 서버 오류 정보를 Domain 예외에 보존한다`() = runTest {
        val cases = listOf(
            RoomActionCase("delete", "/api/v1/rooms/7"),
            RoomActionCase("leave", "/api/v1/rooms/7/members"),
        )

        cases.forEach { case ->
            val client = clientFor { request ->
                assertEquals(case.path, request.url.encodedPath)
                respond(
                    content = """{"isSuccess":false,"code":"ROOM403","message":"권한 없음","result":null}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            }
            val repository = repository(client, AuthTokens("access", "refresh"), AuthSessionState.Authenticated)

            val error = assertFailsWith<RoomRepositoryException> {
                when (case.name) {
                    "delete" -> repository.deleteRoom(7)
                    else -> repository.leaveRoom(7)
                }
            }

            assertEquals(403, error.statusCode)
            assertEquals("ROOM403", error.code)
            assertEquals("권한 없음", error.message)
            client.close()
        }
    }

    @Test
    fun `삭제와 탈퇴 API 업무 실패 응답과 잘못된 성공 코드를 성공으로 처리하지 않는다`() = runTest {
        val businessFailureClient = clientFor {
            respond(
                content = """{"isSuccess":false,"code":"ROOM409","message":"참여 상태가 변경됐어요.","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val wrongCodeClient = clientFor {
            respond(
                content = """{"isSuccess":true,"code":"ROOM201","message":"성공","result":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val wrongStatusClient = clientFor {
            respond(
                content = """{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        val businessError = assertFailsWith<RoomRepositoryException> {
            repository(businessFailureClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).leaveRoom(7)
        }
        assertEquals(200, businessError.statusCode)
        assertEquals("ROOM409", businessError.code)
        assertFailsWith<IllegalStateException> {
            repository(wrongCodeClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).deleteRoom(7)
        }
        assertFailsWith<IllegalStateException> {
            repository(wrongStatusClient, AuthTokens("access", "refresh"), AuthSessionState.Authenticated).deleteRoom(7)
        }

        businessFailureClient.close()
        wrongCodeClient.close()
        wrongStatusClient.close()
    }

    @Test
    fun `삭제는 AUTH401이면 토큰을 재발급하고 새 access token으로 한 번 재시도한다`() = runTest {
        var deleteRequestCount = 0
        var reissueRequestCount = 0
        val storage = TestTokenStorage(AuthTokens("old-access", "old-refresh"))
        val session = session(AuthSessionState.Authenticated)
        val client = clientFor { request ->
            when (request.url.encodedPath) {
                "/api/v1/rooms/7" -> {
                    deleteRequestCount += 1
                    when (request.headers[HttpHeaders.Authorization]) {
                        "Bearer old-access" -> respond(
                            content = """{"isSuccess":false,"code":"AUTH401","message":"액세스 토큰이 만료되었습니다.","result":null}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                        "Bearer new-access" -> respond(
                            content = """{"isSuccess":true,"code":"ROOM200","message":"성공","result":null}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                        else -> error("예상하지 않은 Authorization 헤더입니다: ${request.headers[HttpHeaders.Authorization]}")
                    }
                }
                "/api/v1/auth/reissue" -> {
                    reissueRequestCount += 1
                    respond(
                        content = """{"isSuccess":true,"code":"COMMON200","message":"성공","result":{"accessToken":"new-access","refreshToken":"new-refresh"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                else -> error("예상하지 않은 요청입니다: ${request.url.encodedPath}")
            }
        }

        DefaultRoomRepository(client, storage, session).deleteRoom(7)

        assertEquals(2, deleteRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(AuthTokens("new-access", "new-refresh"), storage.read())
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

}

private data class RoomActionCase(
    val name: String,
    val path: String,
)

private data class AuthCase(
    val state: AuthSessionState,
    val tokens: AuthTokens?,
    val authorization: String?,
)

private class RoomUpdateFixture {
    val patchPaths = mutableListOf<String>()
    val patchAuthorizations = mutableListOf<String?>()
    val patchContentTypes = mutableListOf<String>()
    val patchBodies = mutableListOf<String>()
    val client = clientFor { request ->
        assertEquals(HttpMethod.Patch, request.method)
        patchPaths += request.url.encodedPath
        patchAuthorizations += request.headers[HttpHeaders.Authorization]
        patchContentTypes += request.body.contentType.toString()
        patchBodies += request.readBodyText()
        respond(
            """{"isSuccess":true,"code":"ROOM200","message":"성공","result":${updateRoomResultJson()}}""",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }
    val repository = DefaultRoomRepository(
        client,
        TestTokenStorage(AuthTokens("access", "refresh")),
        session(AuthSessionState.Authenticated),
    )
}

private suspend fun HttpRequestData.readBodyText(): String = coroutineScope {
    val channel = ByteChannel()
    val writer = launch {
        (body as OutgoingContent.WriteChannelContent).writeTo(channel)
        channel.close()
    }
    val bytes = channel.readRemaining().readByteArray()
    writer.join()
    bytes.decodeToString()
}

private fun assertMultipartPart(body: String, name: String, value: String) {
    val marker = "name=\"$name\""
    val nameIndex = body.indexOf(marker)
    assertTrue(nameIndex >= 0, "멀티파트에 $name 파트가 있어야 합니다.")
    assertEquals(nameIndex, body.lastIndexOf(marker), "멀티파트에 $name 파트는 한 번만 있어야 합니다.")
    val valueStart = body.indexOf("\r\n\r\n", startIndex = nameIndex) + 4
    assertTrue(valueStart >= 4, "멀티파트 $name 파트에 값 구분자가 있어야 합니다.")
    val valueEnd = body.indexOf("\r\n--", startIndex = valueStart)
    assertTrue(valueEnd >= 0, "멀티파트 $name 파트 끝을 찾을 수 있어야 합니다.")
    assertEquals(value, body.substring(valueStart, valueEnd), "멀티파트 $name 파트 값이 일치해야 합니다.")
}

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

private fun detailSuccessBody(
    roomId: Long = 7,
    role: String = "MEMBER",
    code: String = "ROOM200",
) = """{"isSuccess":true,"code":"$code","message":"성공","result":{"roomId":$roomId,"name":"상세 응답 이름","description":null,"imageUrl":"/images/rooms/detail.png","activityDays":["MONDAY","WEDNESDAY"],"activityTime":"08:15","memberCount":3,"membershipRole":"$role","createdAt":"2026-10-01T08:30:00","members":[{"userId":11,"nickname":"두 번째","profileImageUrl":"/images/profile/member-11.png"},{"userId":10,"nickname":"첫 번째","profileImageUrl":null}]}}"""

private fun updateRoomResultJson(roomId: Long = 7) =
    """{"roomId":$roomId,"name":"서버 수정 이름","description":null,"imageUrl":"/images/rooms/updated.png"}"""
