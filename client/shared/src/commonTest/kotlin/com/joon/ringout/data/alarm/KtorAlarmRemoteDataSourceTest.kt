package com.joon.ringout.data.alarm

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorAlarmRemoteDataSourceTest {
    @Test
    fun `서버의 빈 알람 목록을 빈 목록으로 반환한다`() = runTest {
        HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/alarms", request.url.encodedPath)
            assertEquals("Bearer $accessToken", request.headers[HttpHeaders.Authorization])
            respondJson(success("""{"alarms":[]}"""))
        }) { configureRingoutHttpClient() }.use { client ->
            val alarms = KtorAlarmRemoteDataSource(client, TokenStorage).fetchAll("7")

            assertTrue(alarms.isEmpty())
        }
    }

    @Test
    fun `문자열 서버 ID와 요일을 별도 기기 ID의 꺼진 알람으로 복원한다`() = runTest {
        val remote = sampleAlarm.copy(
            alarmId = "Case-Sensitive",
            dayOfWeek = listOf(RemoteAlarmDay.SUNDAY, RemoteAlarmDay.WEDNESDAY),
        )
        HttpClient(MockEngine {
            val alarm = ApiJson.encodeToString(RemoteAlarm.serializer(), remote)
            respondJson(success("""{"alarms":[$alarm]}"""))
        }) { configureRingoutHttpClient() }.use { client ->
            val fetched = KtorAlarmRemoteDataSource(client, TokenStorage).fetchAll("7").single()
            val restored = fetched.toAlarmWithRepeatDays(deviceAlarmId = "device-id", ownerAccountId = "7")

            assertEquals("Case-Sensitive", fetched.alarmId)
            assertEquals(0, fetched.repeatTime)
            assertEquals("device-id", restored.alarm.id)
            assertEquals("7", restored.alarm.ownerAccountId)
            assertEquals(1, restored.alarm.limitMinutes)
            assertEquals(1.2, restored.alarm.targetDistanceKm)
            assertTrue(restored.alarm.repeatEnabled)
            assertFalse(restored.alarm.enabled)
            assertNull(restored.alarm.alarmSoundUri)
            assertEquals(
                listOf(AlarmRepeatDayEntity("device-id", 3), AlarmRepeatDayEntity("device-id", 7)),
                restored.repeatDays,
            )
        }
    }

    @Test
    fun `기기 예약 정보 없이 알람 설정 전체를 PUT하고 버전이 높은 서버 설정을 반환한다`() = runTest {
        val id = "Case/Sensitive"
        val longLabel = "1234567890123456789😀"
        val serverAlarm = sampleAlarm.copy(alarmId = id, time = "06:00", alarmVersion = 9)
        HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v1/alarms/Case%2FSensitive", request.url.encodedPath)
            assertEquals("Bearer $accessToken", request.headers[HttpHeaders.Authorization])
            val body = ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(
                ApiJson.parseToJsonElement(
                    """
                    {
                        "time":"07:00",
                        "dayOfWeek":[],
                        "repeatTime":0,
                        "destinationName":"1234567890123456789",
                        "destinationLatitude":-90.0,
                        "destinationLongitude":180.0,
                        "alarmVersion":7
                    }
                    """.trimIndent(),
                ).jsonObject,
                body,
            )
            respondJson(success(ApiJson.encodeToString(RemoteAlarm.serializer(), serverAlarm)))
        }) { configureRingoutHttpClient() }.use { client ->
            val localAlarm = sampleAlarm.copy(alarmId = id, destinationName = " $longLabel ")
                .toAlarmWithRepeatDays(deviceAlarmId = "device-id", ownerAccountId = "7")
            val request = localAlarm.copy(
                repeatDays = listOf(AlarmRepeatDayEntity("device-id", 1)),
            ).toRemoteAlarm(alarmId = id, alarmVersion = 7, repeatTime = 0)

            val actual = KtorAlarmRemoteDataSource(client, TokenStorage).upsert("7", request)

            assertEquals(serverAlarm, actual)
            assertEquals(" $longLabel ", localAlarm.alarm.destinationLabel)
        }
    }

    @Test
    fun `같은 버전의 응답은 허용하고 낮은 버전과 다른 알람 ID 응답은 거절한다`() = runTest {
        val responseAlarms = listOf(
            sampleAlarm,
            sampleAlarm.copy(alarmVersion = sampleAlarm.alarmVersion - 1),
            sampleAlarm.copy(alarmId = "another-alarm"),
        ).iterator()
        HttpClient(MockEngine {
            respondJson(success(ApiJson.encodeToString(RemoteAlarm.serializer(), responseAlarms.next())))
        }) { configureRingoutHttpClient() }.use { client ->
            val api = KtorAlarmRemoteDataSource(client, TokenStorage)

            assertEquals(sampleAlarm, api.upsert("7", sampleAlarm))
            assertFailsWith<IllegalStateException> { api.upsert("7", sampleAlarm) }
            assertFailsWith<IllegalStateException> { api.upsert("7", sampleAlarm) }
        }
    }

    @Test
    fun `삭제에 버전과 본문을 보내지 않고 성공 응답의 null을 허용한다`() = runTest {
        HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/alarms/remote-id", request.url.encodedPath)
            assertIs<OutgoingContent.NoContent>(request.body)
            respondJson(success("null"))
        }) { configureRingoutHttpClient() }.use { client ->
            KtorAlarmRemoteDataSource(client, TokenStorage).delete("7", "remote-id")
        }
    }

    @Test
    fun `잘못된 설정과 인증 실패의 HTTP 상태 및 API 코드를 보존한다`() = runTest {
        val errors = listOf(
            HttpStatusCode.BadRequest to "ALARM400",
            HttpStatusCode.Unauthorized to "ALARM401",
        ).iterator()
        HttpClient(MockEngine {
            val (status, code) = errors.next()
            respondJson("""{"isSuccess":false,"code":"$code","message":"실패","result":null}""", status)
        }) { configureRingoutHttpClient() }.use { client ->
            val api = KtorAlarmRemoteDataSource(client, TokenStorage)

            val badRequest = assertFailsWith<ApiException> { api.upsert("7", sampleAlarm) }
            val unauthorized = assertFailsWith<ApiException> { api.fetchAll("7") }

            assertEquals(400, badRequest.statusCode)
            assertEquals("ALARM400", badRequest.code)
            assertEquals(401, unauthorized.statusCode)
            assertEquals("ALARM401", unauthorized.code)
        }
    }

    @Test
    fun `토큰의 계정과 동기화 계정이 다르면 API 요청을 보내지 않는다`() = runTest {
        HttpClient(MockEngine {
            error("계정이 일치하기 전에는 요청을 보내면 안 됩니다.")
        }) { configureRingoutHttpClient() }.use { client ->
            assertFailsWith<IllegalStateException> {
                KtorAlarmRemoteDataSource(client, TokenStorage).fetchAll("8")
            }.also { error ->
                assertEquals("현재 로그인 계정이 알람 계정과 일치하지 않아요.", error.message)
            }
        }
    }
}

private val sampleAlarm = RemoteAlarm(
    alarmId = "remote-id",
    time = "07:00",
    dayOfWeek = emptyList(),
    repeatTime = 0,
    destinationName = "목적지",
    destinationLatitude = -90.0,
    destinationLongitude = 180.0,
    alarmVersion = 7,
)

private fun success(result: String): String =
    """{"isSuccess":true,"code":"ALARM200","message":"성공","result":$result}"""

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private val accessToken: String = Base64.UrlSafe.encode(
    """{"tokenType":"ACCESS","userId":7,"sub":"7"}""".encodeToByteArray(),
).trimEnd('=').let { "eyJhbGciOiJub25lIn0.$it." }

private object TokenStorage : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) = Unit

    override suspend fun read(): AuthTokens = AuthTokens(
        accessToken = accessToken,
        refreshToken = "ringout-refresh",
        userId = 7,
    )

    override suspend fun clear() = Unit
}
