package com.joon.ringout.presentation.developertools

import androidx.lifecycle.ViewModel
import com.joon.ringout.domain.developertools.AppBuildInfo

internal class DeveloperToolsViewModel(buildInfo: AppBuildInfo) : ViewModel() {
    val uiState = DeveloperToolsUiState(
        versionName = buildInfo.versionName,
        versionCode = buildInfo.versionCode,
        buildType = buildInfo.buildType,
        serverEnvironment = buildInfo.serverEnvironment,
        apiBaseUrl = buildInfo.apiBaseUrl,
    )
}
