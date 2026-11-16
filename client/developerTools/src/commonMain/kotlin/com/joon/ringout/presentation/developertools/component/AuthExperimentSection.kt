package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.auth.AuthProbeResult
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.developertools.AuthExperiment
import com.joon.ringout.presentation.developertools.AuthExperimentUiState

@Composable
internal fun AuthExperimentSection(
    state: AuthExperimentUiState,
    session: AuthSessionState,
    modifier: Modifier = Modifier,
    onRevoke: (Boolean) -> Unit = {},
    onRun: (AuthExperiment) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val enabled = session == AuthSessionState.Authenticated && !state.running
    val sessionLabel = when (session) {
        AuthSessionState.Authenticated -> "로그인됨"
        AuthSessionState.ReauthenticationRequired -> "재로그인 필요"
        AuthSessionState.Restoring -> "복원 중"
        AuthSessionState.Unauthenticated -> "로그아웃"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("인증 실험", style = MaterialTheme.typography.titleLarge)
        Text("세션 · $sessionLabel")
        OutlinedButton(onClick = { onRevoke(false) }, enabled = enabled) { Text("Access 토큰 폐기") }
        OutlinedButton(onClick = { onRevoke(true) }, enabled = enabled) { Text("Refresh 토큰 폐기") }
        state.revokeStatus?.let { Text(it) }
        if (state.running) Text("실험 진행 중")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        for (experiment in AuthExperiment.entries) {
            HorizontalDivider()
            Text(experiment.title, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onRun(experiment) }, enabled = enabled) { Text("실험 실행") }
            state.results[experiment]?.let { Text(it.summary()) }
            if (experiment == AuthExperiment.ConnectionFailure && state.results[experiment] != null) {
                OutlinedButton(onClick = onRetry, enabled = enabled) { Text("연결 오류 없이 다시 요청") }
                state.retry?.let { Text(it.summary()) }
            }
        }
    }
}

private fun AuthProbeResult.summary(): String =
    "401 응답 $expired · 갱신 시도 $reissues · 재시도 성공 $retriedSuccessfully · 요청 성공 $succeeded" +
        (failure?.let { "\n$it" } ?: "")

@Preview
@Composable
private fun AuthExperimentSectionPreview() {
    RingoutTheme(ThemeMode.Light) {
        AuthExperimentSection(
            state = AuthExperimentUiState(
                results = mapOf(AuthExperiment.Concurrent to AuthProbeResult(5, 1, 5, 5, null)),
            ),
            session = AuthSessionState.Authenticated,
        )
    }
}
