package com.joon.ringout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.di.AppContainer
import com.joon.ringout.presentation.developertools.DeveloperToolsHost
import com.joon.ringout.presentation.developertools.DeveloperToolsRoute
import com.joon.ringout.presentation.developertools.RemoteConfigUiState
import com.joon.ringout.remoteconfig.RemoteConfigFetchStatus
import com.joon.ringout.remoteconfig.RemoteConfigState
import com.joon.ringout.remoteconfig.RemoteConfigValueSource
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PlatformAppHost(
    appContainer: AppContainer,
    content: @Composable (extraContent: (@Composable (ThemeMode) -> Unit)?) -> Unit,
) {
    val repository = (LocalContext.current.applicationContext as RingoutApplication).remoteConfigRepository
    DeveloperToolsHost(
        toolsContent = { onBack ->
            val remoteConfigState by repository.state.collectAsState()
            DeveloperToolsRoute(
                buildInfo = currentAppBuildInfo(),
                pushTokenRegistrationReadiness = appContainer.pushTokenRegistrationReadiness,
                onBack = onBack,
                remoteConfig = remoteConfigState.toUiState(),
                onRefreshRemoteConfig = { repository.refresh() },
            )
        },
        content = { extraContent -> content(extraContent) },
    )
}

private fun RemoteConfigState.toUiState(): RemoteConfigUiState = RemoteConfigUiState(
    minVersionCode = minVersionCode.toString(),
    valueSource = when (valueSource) {
        RemoteConfigValueSource.DEFAULT -> "앱 기본값"
        RemoteConfigValueSource.REMOTE -> "Firebase 원격 설정"
        RemoteConfigValueSource.INVALID_REMOTE -> "유효하지 않은 원격값 · 앱 기본값 사용"
    },
    lastFetchTime = lastFetchTimeMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "가져온 적 없음",
    fetchStatus = when (fetchStatus) {
        RemoteConfigFetchStatus.NOT_FETCHED -> "가져오기 전"
        RemoteConfigFetchStatus.FETCHING -> "가져오는 중"
        RemoteConfigFetchStatus.SUCCESS -> "성공"
        RemoteConfigFetchStatus.FAILURE -> "실패"
        RemoteConfigFetchStatus.THROTTLED -> "요청 제한됨"
    },
    listenerStatus = if (listenerError) "발생" else "없음",
    isRefreshing = fetchStatus == RemoteConfigFetchStatus.FETCHING,
)
