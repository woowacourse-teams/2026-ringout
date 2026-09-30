package com.joon.ringout.data.terms

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.terms.RequiredTermType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class DefaultTermsRepositoryTest {
    @Test
    fun `조회 요청에 인증 헤더를 붙이고 약관별 재동의 필요 상태를 반환한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/terms/agreements", request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            respond(body(false, listOf(term("SERVICE", null, true), term("PRIVACY", "2026-09-30", false))),
                headers = jsonHeaders)
        }) { configureRingoutHttpClient() }
        val status = DefaultTermsRepository(client, Tokens(), session()).getStatus()
        assertFalse(status.allAgreed)
        assertEquals(listOf(RequiredTermType.SERVICE), status.pending.map { it.type })
        client.close()
    }
    @Test
    fun `필수 항목이 누락된 성공 응답을 동의 완료로 인정하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            respond(body(true, listOf(term("SERVICE", "2026-09-30", false))), headers = jsonHeaders)
        }) { configureRingoutHttpClient() }
        assertFailsWith<IllegalArgumentException> { DefaultTermsRepository(client, Tokens(), session()).getStatus() }
        client.close()
    }
    @Test
    fun `전체 완료 플래그와 약관별 버전이 모순이면 오류를 반환한다`() = runTest {
        val client = HttpClient(MockEngine {
            respond(body(true, listOf(term("SERVICE", null, false), term("PRIVACY", "2026-09-30", false))), headers = jsonHeaders)
        }) { configureRingoutHttpClient() }
        assertFailsWith<IllegalStateException> { DefaultTermsRepository(client, Tokens(), session()).getStatus() }
        client.close()
    }
    @Test
    fun `서버 계약에 따라 필수 두 종류와 날짜만 제출하고 선택 약관은 포함하지 않는다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/terms", request.url.encodedPath)
            val json = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("SERVICE", "PRIVACY"), json.getValue("termsTypes").jsonArray.map { it.jsonPrimitive.content }.toSet())
            assertEquals("2026-09-30", json.getValue("agreedAt").jsonPrimitive.content)
            assertEquals(setOf("termsTypes", "agreedAt"), json.keys)
            respond("""{"isSuccess":true,"code":"OK","message":"성공","result":null}""", headers = jsonHeaders)
        }) { configureRingoutHttpClient() }
        DefaultTermsRepository(client, Tokens(), session()).agreeRequiredTerms("2026-09-30")
        client.close()
    }
    @Test
    fun `서버 오류를 동의 성공으로 처리하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"isSuccess":false,"code":"TERMS500","message":"약관 없음"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }) { configureRingoutHttpClient() }
        val error = assertFailsWith<ApiException> { DefaultTermsRepository(client, Tokens(), session()).getStatus() }
        assertEquals("TERMS500", error.code)
        client.close()
    }
    @Test
    fun `응답 수신 전에 계정이 바뀌면 이전 계정의 결과를 반환하지 않는다`() = runTest {
        val session = session()
        val client = HttpClient(MockEngine {
            session.startNewSession()
            respond(body(true, listOf(term("SERVICE", "2026-09-30", false), term("PRIVACY", "2026-09-30", false))), headers = jsonHeaders)
        }) { configureRingoutHttpClient() }
        assertFailsWith<CancellationException> { DefaultTermsRepository(client, Tokens(), session).getStatus() }
        client.close()
    }
}
private fun session() = AuthSession().apply { markAuthenticated() }
private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private fun term(type: String, agreed: String?, needs: Boolean) =
    """{"type":"$type","termsId":${if (type == "SERVICE") 1 else 2},"latestVersion":"2026-09-30","agreedVersion":${agreed?.let { "\"$it\"" } ?: "null"},"needsReagreement":$needs}"""
private fun body(all: Boolean, terms: List<String>) =
    """{"isSuccess":true,"code":"OK","message":"성공","result":{"allAgreed":$all,"agreements":[${terms.joinToString()}]}}"""
private class Tokens : SecureTokenStorage {
    override suspend fun read() = AuthTokens("access", "refresh")
    override suspend fun save(tokens: AuthTokens) = Unit
    override suspend fun clear() = Unit
}
