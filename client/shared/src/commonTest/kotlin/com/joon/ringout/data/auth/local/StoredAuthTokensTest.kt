package com.joon.ringout.data.auth.local

import com.joon.ringout.domain.auth.AuthTokens
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredAuthTokensTest {
    @Test
    fun `회원 ID가 없는 이전 저장 데이터도 로그인 상태를 복원한다`() {
        val stored = Json.decodeFromString<StoredAuthTokens>(
            """{"accessToken":"access","refreshToken":"refresh","reauthenticationRequired":false}""",
        )

        assertEquals(AuthTokens("access", "refresh", userId = null), stored.toAuthTokens())
    }

    @Test
    fun `저장된 회원 ID는 직렬화 후 다시 읽어도 유지된다`() {
        val stored = StoredAuthTokens("opaque-access", "refresh", userId = Long.MAX_VALUE)
        val restored = Json.decodeFromString<StoredAuthTokens>(Json.encodeToString(stored))

        assertEquals(AuthTokens("opaque-access", "refresh", userId = Long.MAX_VALUE), restored.toAuthTokens())
    }

    @Test
    fun `만료 표시는 회원 ID 없이 보존하고 토큰은 복원하지 않는다`() {
        val expired = StoredAuthTokens(reauthenticationRequired = true)
        val restored = Json.decodeFromString<StoredAuthTokens>(Json.encodeToString(expired))

        assertTrue(restored.reauthenticationRequired)
        assertNull(restored.userId)
        assertNull(restored.toAuthTokens())
    }

    @Test
    fun `잘못된 저장 회원 ID는 비워 두고 토큰은 복원한다`() {
        for (userId in listOf(0L, -1L)) {
            assertEquals(
                AuthTokens("access", "refresh", userId = null),
                StoredAuthTokens("access", "refresh", userId = userId).toAuthTokens(),
            )
        }
    }
}
