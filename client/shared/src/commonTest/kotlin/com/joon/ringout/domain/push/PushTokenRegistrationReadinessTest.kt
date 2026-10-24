package com.joon.ringout.domain.push

import com.joon.ringout.domain.auth.AuthSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushTokenRegistrationReadinessTest {
    @Test
    fun `토큰이 먼저 준비되어도 로그인 완료 후 등록 후보가 만들어진다`() {
        val readiness = PushTokenRegistrationReadiness(PushPlatform.Android)
        readiness.updateAuthSession(AuthSessionState.Restoring)
        readiness.updateFcmToken("fcm-token")

        assertNull(readiness.candidate.value)

        readiness.updateAuthSession(AuthSessionState.Authenticated)

        assertEquals(
            PushTokenRegistrationCandidate(PushPlatform.Android, "fcm-token"),
            readiness.candidate.value,
        )
    }

    @Test
    fun `로그인이 먼저 완료되어도 토큰 준비 후 등록 후보가 만들어진다`() {
        val readiness = PushTokenRegistrationReadiness(PushPlatform.Ios)
        readiness.updateAuthSession(AuthSessionState.Authenticated)

        assertNull(readiness.candidate.value)

        readiness.updateFcmToken("fcm-token")

        assertEquals(
            PushTokenRegistrationCandidate(PushPlatform.Ios, "fcm-token"),
            readiness.candidate.value,
        )
    }

    @Test
    fun `비로그인 중 받은 최신 토큰은 등록하지 않고 로그인 뒤 후보로 노출한다`() {
        val readiness = PushTokenRegistrationReadiness(PushPlatform.Android)
        readiness.updateAuthSession(AuthSessionState.Unauthenticated)
        readiness.updateFcmToken("old-token")
        readiness.updateFcmToken("latest-token")

        assertNull(readiness.candidate.value)

        readiness.updateAuthSession(AuthSessionState.Authenticated)

        assertEquals(
            PushTokenRegistrationCandidate(PushPlatform.Android, "latest-token"),
            readiness.candidate.value,
        )
    }

    @Test
    fun `로그아웃은 후보를 숨기고 다음 로그인에서 최신 토큰을 다시 노출한다`() {
        val readiness = PushTokenRegistrationReadiness(PushPlatform.Ios)
        readiness.updateAuthSession(AuthSessionState.Authenticated)
        readiness.updateFcmToken("first-token")

        readiness.updateAuthSession(AuthSessionState.Unauthenticated)
        assertNull(readiness.candidate.value)

        readiness.updateAuthSession(AuthSessionState.Authenticated)

        assertEquals(
            PushTokenRegistrationCandidate(PushPlatform.Ios, "first-token"),
            readiness.candidate.value,
        )
    }

    @Test
    fun `토큰 갱신은 로그인 상태에서 최신 등록 후보를 노출한다`() {
        val readiness = PushTokenRegistrationReadiness(PushPlatform.Android)
        readiness.updateAuthSession(AuthSessionState.Authenticated)
        readiness.updateFcmToken("old-token")

        readiness.updateFcmToken("new-token")

        assertEquals(
            PushTokenRegistrationCandidate(PushPlatform.Android, "new-token"),
            readiness.candidate.value,
        )
    }
}
