package com.joon.ringout.data.auth

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessTokenUserIdTest {
    @Test
    fun `서버 ACCESS 토큰에서 양수 회원 ID만 반환한다`() {
        assertEquals("42", accessTokenUserId(jwt("""{"sub":"42","userId":42,"tokenType":"ACCESS"}""")))
        assertEquals(Long.MAX_VALUE.toString(), accessTokenUserId(jwt("""{"sub":"${Long.MAX_VALUE}","userId":${Long.MAX_VALUE},"tokenType":"ACCESS"}""")))
    }

    @Test
    fun `회원 식별 조건에 맞지 않는 토큰에서는 ID를 반환하지 않는다`() {
        val invalidClaims = listOf(
            """{"sub":"42","userId":42,"tokenType":"REFRESH"}""",
            """{"sub":"43","userId":42,"tokenType":"ACCESS"}""",
            """{"sub":"0","userId":0,"tokenType":"ACCESS"}""",
            """{"sub":"-1","userId":-1,"tokenType":"ACCESS"}""",
            """{"sub":"42","tokenType":"ACCESS"}""",
            """{"userId":42,"tokenType":"ACCESS"}""",
            """{"sub":"42","userId":42}""",
            """{"sub":"member@example.com","userId":"member@example.com","tokenType":"ACCESS"}""",
            """{"sub":"42","userId":{},"tokenType":"ACCESS"}""",
            "[]",
            "null",
            "invalid json",
        )
        invalidClaims.forEach { assertNull(accessTokenUserId(jwt(it)), it) }
        listOf("", "opaque-token", "a.b.c", "a.b.c.d").forEach { assertNull(accessTokenUserId(it), it) }
    }
}

private fun jwt(claims: String): String =
    "header." + Base64.UrlSafe.encode(claims.encodeToByteArray()).trimEnd('=') + ".signature"
