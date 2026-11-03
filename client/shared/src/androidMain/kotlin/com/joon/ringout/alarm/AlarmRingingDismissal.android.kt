package com.joon.ringout.alarm

import com.joon.ringout.domain.auth.AuthSessionState

internal suspend fun dismissRingingAlarmForSession(
    restoreSession: suspend () -> AuthSessionState,
    onAuthenticated: () -> Unit,
    onStopOnly: () -> Unit,
) {
    when (restoreSession()) {
        AuthSessionState.Authenticated -> onAuthenticated()
        AuthSessionState.Unauthenticated,
        AuthSessionState.ReauthenticationRequired,
        -> onStopOnly()
        AuthSessionState.Restoring -> error("인증 복원을 완료하지 못했습니다.")
    }
}
