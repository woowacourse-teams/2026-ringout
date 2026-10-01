package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class AlarmOccurrenceAccountTest {
    @Test
    fun `서버 ACCESS 토큰의 일치하는 회원 ID만 소유 계정으로 사용한다`() {
        assertEquals("1", alarmOccurrenceTokenOwner(jwt(1)))
        listOf("opaque", "a.b.c", jwt(0), jwt(1, "2"), jwt(1, type = "REFRESH")).forEach {
            assertNull(alarmOccurrenceTokenOwner(it))
        }
    }

    @Test
    fun `로그아웃 및 복원 중에는 전송 계정을 확보하지 않는다`() = runTest {
        val session = AuthSession()
        val storage = AccountTokens()
        assertNull(captureAlarmOccurrenceAccount(storage, session))
        session.startNewSession()
        assertEquals("1", captureAlarmOccurrenceAccount(storage, session)?.ownerAccountId)
        session.clear()
        assertNull(captureAlarmOccurrenceAccount(storage, session))
    }

    @Test
    fun `최초 요청 전에 토큰의 계정이 바뀌면 HTTP를 호출하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = AccountTokens()
        val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
        storage.tokens = AuthTokens(jwt(2), "refresh")
        var count = 0
        val client = HttpClient(MockEngine { count++; error("전송되면 안 됩니다.") }) { configureRingoutHttpClient() }
        assertFailsWith<AlarmOccurrenceAccountChanged> {
            DefaultAlarmOccurrenceRepository(client, storage, session, account).start(Start)
        }
        assertEquals(0, count)
        client.close()
    }

    @Test
    fun `토큰 갱신 후에도 같은 계정이면 재전송하고 다른 계정이면 차단한다`() = runTest {
        for (refreshedOwner in listOf(1, 2)) {
            val session = AuthSession().apply { startNewSession() }
            val storage = AccountTokens()
            val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
            var posts = 0
            val client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath.endsWith("/reissue")) {
                    respond("""{"isSuccess":true,"code":"OK","message":"OK","result":{"accessToken":"${jwt(refreshedOwner)}","refreshToken":"new-refresh"}}""", headers = JsonHeaders)
                } else {
                    posts++
                    if (posts == 1) respond("""{"isSuccess":false,"code":"AUTH401","message":"expired"}""", HttpStatusCode.Unauthorized, JsonHeaders)
                    else respond(Success, headers = JsonHeaders)
                }
            }) { configureRingoutHttpClient() }
            val repository = DefaultAlarmOccurrenceRepository(client, storage, session, account)
            if (refreshedOwner == 1) {
                repository.start(Start)
                assertEquals(2, posts)
            } else {
                assertFailsWith<AlarmOccurrenceAccountChanged> { repository.start(Start) }
                assertEquals(1, posts)
            }
            client.close()
        }
    }

    @Test
    fun `같은 계정으로 다시 로그인해도 이전 세션에 묶인 요청은 차단한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val storage = AccountTokens()
        val account = assertNotNull(captureAlarmOccurrenceAccount(storage, session))
        session.startNewSession()
        val client = HttpClient(MockEngine { error("이전 세션의 요청입니다.") }) { configureRingoutHttpClient() }
        assertFailsWith<AlarmOccurrenceAccountChanged> {
            DefaultAlarmOccurrenceRepository(client, storage, session, account).start(Start)
        }
        client.close()
    }
}

private class AccountTokens : SecureTokenStorage {
    var tokens: AuthTokens? = AuthTokens(jwt(1), "refresh")
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
private fun jwt(id: Int, sub: String = id.toString(), type: String = "ACCESS") = "header." +
    Base64.UrlSafe.encode("""{"sub":"$sub","userId":$id,"tokenType":"$type"}""".encodeToByteArray()).trimEnd('=') + ".signature"
private val Start = AlarmOccurrenceStart("alarm", Instant.fromEpochMilliseconds(1_000), Instant.fromEpochMilliseconds(1_100))
private val JsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val Success = """{"isSuccess":true,"code":"OK","message":"OK","result":{"alarmOccurrenceId":"5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f","startedAt":"2026-10-01T07:00:00+09:00","ringings":[]}}"""
