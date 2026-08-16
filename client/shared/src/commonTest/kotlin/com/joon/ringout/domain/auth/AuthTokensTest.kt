package com.joon.ringout.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthTokensTest {
    @Test
    fun `액세스 토큰과 리프레시 토큰을 생성한다`() {
        val tokens = AuthTokens(
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )

        assertEquals("access-token", tokens.accessToken)
        assertEquals("refresh-token", tokens.refreshToken)
    }

    @Test
    fun `빈 액세스 토큰은 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            AuthTokens(
                accessToken = "",
                refreshToken = "refresh-token",
            )
        }
    }

    @Test
    fun `빈 리프레시 토큰은 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            AuthTokens(
                accessToken = "access-token",
                refreshToken = " ",
            )
        }
    }
}
