package com.joon.ringout.presentation.developertools

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.developertools.AppBuildInfo

@Composable
fun DeveloperToolsRoute(buildInfo: AppBuildInfo, onBack: () -> Unit) {
    val toolsViewModel = viewModel { DeveloperToolsViewModel(buildInfo) }
    DeveloperToolsScreen(uiState = toolsViewModel.uiState, onBack = onBack)
}
