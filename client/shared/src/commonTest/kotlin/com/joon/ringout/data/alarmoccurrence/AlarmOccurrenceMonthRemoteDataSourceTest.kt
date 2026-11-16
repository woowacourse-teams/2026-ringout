package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.alarmoccurrence.currentAlarmOccurrenceMonth
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AlarmOccurrenceMonthRemoteDataSourceTest {
    @Test
    fun `인증된 월 조회는 자정 이후 울림과 종료를 최초 날짜에 보존한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = MonthlyTokenStorage()
        val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/alarm-occurrences", request.url.encodedPath)
            assertEquals("2026-12", request.url.parameters["month"])
            assertEquals("Bearer ${monthlyJwt(1)}", request.headers[HttpHeaders.Authorization])
            respondMonth(monthResponse(occurrences = DecemberOccurrence))
        }) { configureRingoutHttpClient() }

        try {
            val snapshot = AlarmOccurrenceMonthRemoteDataSource(client, storage, session).fetch(account, December)
            val occurrence = snapshot.occurrences.single()
            assertEquals("2026-12-31", occurrence.date)
            assertEquals("22:30", occurrence.alarmTime)
            assertEquals("ARRIVED", occurrence.endType)
            assertEquals(Instant.parse("2026-12-31T15:20:00Z").toEpochMilliseconds(), occurrence.endedAtEpochMillis)
            assertEquals(listOf("INITIAL", "REPEAT:repeat-1"), snapshot.ringings.map { it.ringingKey })
            assertEquals(Instant.parse("2026-12-31T15:05:00Z").toEpochMilliseconds(), snapshot.ringings.last().ringingAtEpochMillis)
        } finally {
            client.close()
        }
    }

    @Test
    fun `빈 월은 유효하고 누락되거나 중복된 날짜와 잘못된 이벤트는 거절한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = MonthlyTokenStorage()
        val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
        var body = monthResponse()
        val client = HttpClient(MockEngine { respondMonth(body) }) { configureRingoutHttpClient() }
        try {
            val source = AlarmOccurrenceMonthRemoteDataSource(client, storage, session)
            assertTrue(source.fetch(account, December).occurrences.isEmpty())
            val invalidResponses = listOf(
                monthResponse().replace("\"month\":\"2026-12\"", "\"month\":\"2027-01\""),
                monthResponse().replace("\"date\":\"2026-12-30\"", "\"date\":\"2026-12-29\""),
                monthResponse().replace("""{"date":"2026-12-31","occurrences":[]}""", "").replace(",]", "]"),
                monthResponse(occurrences = "$DecemberOccurrence,$DecemberOccurrence"),
                monthResponse(occurrences = DecemberOccurrence.replace("repeat-1", "")),
                monthResponse(occurrences = DecemberOccurrence.replace("2027-01-01T00:05:00+09:00", "bad-time")),
                monthResponse(occurrences = DecemberOccurrence.replace("\"ARRIVED\"", "null")),
            )
            for (invalid in invalidResponses) {
                body = invalid
                assertFailsWith<IllegalArgumentException> { source.fetch(account, December) }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `HTTP 실패 및 실패 봉투는 월 조회에 실패로 전달한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = MonthlyTokenStorage()
        val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
        var status = HttpStatusCode.BadRequest
        val client = HttpClient(MockEngine {
            respondMonth("""{"isSuccess":false,"code":"ERROR","message":"실패"}""", status)
        }) { configureRingoutHttpClient() }
        try {
            val source = AlarmOccurrenceMonthRemoteDataSource(client, storage, session)
            assertEquals(400, assertFailsWith<ApiException> { source.fetch(account, December) }.statusCode)
            status = HttpStatusCode.OK
            assertEquals("ERROR", assertFailsWith<ApiException> { source.fetch(account, December) }.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun `토큰 재발급 뒤에는 같은 계정의 토큰으로만 같은 월을 다시 요청한다`() = runTest {
        for (refreshedOwner in listOf(1, 2)) {
            val session = AuthSession().apply { startNewSession() }
            val storage = MonthlyTokenStorage()
            val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
            val authorizationHeaders = mutableListOf<String?>()
            val refreshedAccessToken = monthlyJwt(refreshedOwner) + "-renewed"
            var refreshCount = 0
            val client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath == "/api/v1/auth/reissue") {
                    refreshCount++
                    respondMonth(
                        """{"isSuccess":true,"code":"OK","message":"OK","result":{"accessToken":"$refreshedAccessToken","refreshToken":"new-refresh"}}""",
                    )
                } else {
                    assertEquals("2026-12", request.url.parameters["month"])
                    authorizationHeaders += request.headers[HttpHeaders.Authorization]
                    if (authorizationHeaders.size == 1) {
                        respondMonth(
                            """{"isSuccess":false,"code":"AUTH401","message":"expired"}""",
                            HttpStatusCode.Unauthorized,
                        )
                    } else {
                        respondMonth(monthResponse())
                    }
                }
            }) { configureRingoutHttpClient() }

            try {
                val source = AlarmOccurrenceMonthRemoteDataSource(client, storage, session)
                if (refreshedOwner == 1) {
                    source.fetch(account, December)
                    assertEquals(listOf<String?>("Bearer ${monthlyJwt(1)}", "Bearer $refreshedAccessToken"), authorizationHeaders)
                } else {
                    assertFailsWith<AlarmOccurrenceAccountChanged> { source.fetch(account, December) }
                    assertEquals(listOf<String?>("Bearer ${monthlyJwt(1)}"), authorizationHeaders)
                }
                assertEquals(1, refreshCount)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `서울 자정이 지나면 서버의 이번 달을 연말에서 새해로 바꾼다`() {
        assertEquals(December, currentAlarmOccurrenceMonth(Instant.parse("2026-12-31T14:59:59Z")))
        assertEquals(MissionYearMonth(2027, 1), currentAlarmOccurrenceMonth(Instant.parse("2026-12-31T15:00:00Z")))
    }
}

internal val December = MissionYearMonth(2026, 12)

internal fun monthResponse(
    month: MissionYearMonth = December,
    occurrences: String = "",
): String {
    val monthKey = month.toCacheKey()
    val days = (1..month.dayCount).joinToString(",") { day ->
        val date = "$monthKey-${day.toString().padStart(2, '0')}"
        val items = if (day == month.dayCount) occurrences else ""
        """{"date":"$date","occurrences":[$items]}"""
    }
    return """{"isSuccess":true,"code":"OK","message":"OK","result":{"month":"$monthKey","days":[$days]}}"""
}

internal val DecemberOccurrence = """
    {
        "alarmId":"alarm-1",
        "alarmOccurrenceId":"occurrence-1",
        "alarmTime":"22:30",
        "ringings":[
            {"type":"INITIAL","eventId":null,"ringingAt":"2026-12-31T23:55:00+09:00","dismissedAt":null},
            {"type":"REPEAT","eventId":"repeat-1","ringingAt":"2027-01-01T00:05:00+09:00","dismissedAt":"2027-01-01T00:06:00+09:00"}
        ],
        "endType":"ARRIVED",
        "endedAt":"2027-01-01T00:20:00+09:00"
    }
""".trimIndent()

internal fun MockRequestHandleScope.respondMonth(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal class MonthlyTokenStorage : SecureTokenStorage {
    var tokens: AuthTokens? = AuthTokens(monthlyJwt(1), "refresh")
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

private fun monthlyJwt(id: Int): String {
    val claims = """{"sub":"$id","userId":$id,"tokenType":"ACCESS"}"""
    return "header.${Base64.UrlSafe.encode(claims.encodeToByteArray()).trimEnd('=')}.signature"
}
