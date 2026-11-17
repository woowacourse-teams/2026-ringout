package com.joon.ringout.alarm

import com.joon.ringout.domain.auth.AuthSessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AlarmRingingDismissalTest {
    @Test
    fun `인증을 복원한 회원만 기존 미션 시작 경로로 종료한다`() = runBlocking {
        val events = mutableListOf<String>()
        dismissRingingAlarmForSession(
            restoreSession = { events += "restore"; AuthSessionState.Authenticated },
            onAuthenticated = { events += "mission" },
            onStopOnly = { events += "stop-only" },
        )
        assertEquals(listOf("restore", "mission"), events)
    }

    @Test
    fun `미인증과 만료 세션은 복원 뒤 미션 없이 현재 울림만 종료한다`() = runBlocking {
        listOf(AuthSessionState.Unauthenticated, AuthSessionState.ReauthenticationRequired).forEach { state ->
            val events = mutableListOf<String>()
            dismissRingingAlarmForSession(
                restoreSession = { events += "restore"; state },
                onAuthenticated = { events += "mission" },
                onStopOnly = { events += "stop-only" },
            )
            assertEquals(listOf("restore", "stop-only"), events)
        }
    }

    @Test
    fun `인증 읽기 오류는 미인증으로 간주하거나 미션을 시작하지 않는다`() = runBlocking {
        val events = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            dismissRingingAlarmForSession(
                restoreSession = { error("read failed") },
                onAuthenticated = { events += "mission" },
                onStopOnly = { events += "stop-only" },
            )
        }
        assertEquals(emptyList(), events)
    }
}
