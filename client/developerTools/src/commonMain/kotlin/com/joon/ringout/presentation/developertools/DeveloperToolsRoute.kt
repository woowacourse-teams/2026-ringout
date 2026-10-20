package com.joon.ringout.presentation.developertools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.developertools.AppBuildInfo
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness

@Composable
fun DeveloperToolsRoute(
    buildInfo: AppBuildInfo,
    pushTokenRegistrationReadiness: PushTokenRegistrationReadiness,
    onBack: () -> Unit,
) {
    val toolsViewModel = viewModel { DeveloperToolsViewModel(buildInfo) }
    val registrationStatus by pushTokenRegistrationReadiness.registrationStatus.collectAsState()
    DeveloperToolsScreen(
        uiState = toolsViewModel.uiState.copy(pushTokenRegistrationStatus = registrationStatus),
        onBack = onBack,
    )
}
