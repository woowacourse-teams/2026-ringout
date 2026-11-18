package com.joon.ringout.presentation.developertools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthDiagnostics
import com.joon.ringout.domain.auth.AuthProbeResult
import com.joon.ringout.domain.auth.AuthTokenStatuses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class AuthExperiment(val title: String) {
    Automatic("Access 만료 후 자동 갱신"),
    Concurrent("동시 요청에서 갱신 1회"),
    Relogin("Refresh 폐기 후 재로그인"),
    ConnectionFailure("갱신 중 연결 끊김"),
}

internal data class AuthExperimentUiState(
    val running: Boolean = false,
    val tokens: AuthTokenStatuses? = null,
    val revokeStatus: String? = null,
    val results: Map<AuthExperiment, AuthProbeResult> = emptyMap(),
    val retry: AuthProbeResult? = null,
    val error: String? = null,
)

internal class AuthExperimentViewModel(private val diagnostics: AuthDiagnostics) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthExperimentUiState())
    val state = mutableState.asStateFlow()

    suspend fun observeTokenStatus() {
        while (true) {
            refreshTokenStatus()
            delay(1_000)
        }
    }

    private suspend fun refreshTokenStatus() {
        val tokens = try {
            diagnostics.readTokenStatus()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        mutableState.update { it.copy(tokens = tokens) }
    }

    fun revoke(refresh: Boolean) = launch {
        diagnostics.revoke(refresh)
        mutableState.value = mutableState.value.copy(
            revokeStatus = if (refresh) "Refresh 토큰 폐기 완료" else "Access 토큰 폐기 완료",
        )
    }

    fun run(experiment: AuthExperiment) = launch {
        mutableState.value = mutableState.value.copy(
            results = mutableState.value.results - experiment,
            retry = if (experiment == AuthExperiment.ConnectionFailure) null else mutableState.value.retry,
        )
        val result = diagnostics.probe(
            requestCount = if (experiment == AuthExperiment.Concurrent) 5 else 1,
            failReissue = experiment == AuthExperiment.ConnectionFailure,
            revokeAccessFirst = true,
            restoreAfterReauthentication = experiment == AuthExperiment.Relogin,
        )
        mutableState.value = mutableState.value.copy(results = mutableState.value.results + (experiment to result))
    }

    fun retry() = launch {
        mutableState.value = mutableState.value.copy(retry = diagnostics.probe(requestCount = 1))
    }

    private fun launch(action: suspend () -> Unit) {
        if (mutableState.value.running) return
        mutableState.value = mutableState.value.copy(running = true, error = null, revokeStatus = null)
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(error = "실험 준비에 실패했어요. 로그인 상태와 서버 연결을 확인해 주세요.")
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
                refreshTokenStatus()
            }
        }
    }
}
