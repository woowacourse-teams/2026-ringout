package com.joon.ringout.presentation.developertools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.developertools.AppBuildInfo
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness
import com.joon.ringout.domain.outbox.OutboxReader

@Composable
fun DeveloperToolsRoute(
    buildInfo: AppBuildInfo,
    pushTokenRegistrationReadiness: PushTokenRegistrationReadiness,
    outboxReader: OutboxReader,
    onBack: () -> Unit,
    remoteConfig: RemoteConfigUiState? = null,
    onRefreshRemoteConfig: () -> Unit = {},
) {
    val toolsViewModel = viewModel { DeveloperToolsViewModel(buildInfo, outboxReader) }
    val toolsUiState by toolsViewModel.uiState.collectAsState()
    val registrationStatus by pushTokenRegistrationReadiness.registrationStatus.collectAsState()
    DeveloperToolsScreen(
        uiState = toolsUiState.copy(
            pushTokenRegistrationStatus = registrationStatus,
            remoteConfig = remoteConfig,
        ),
        onBack = onBack,
        onRefreshRemoteConfig = onRefreshRemoteConfig,
        onRefreshOutbox = toolsViewModel::refreshOutbox,
    )
}
