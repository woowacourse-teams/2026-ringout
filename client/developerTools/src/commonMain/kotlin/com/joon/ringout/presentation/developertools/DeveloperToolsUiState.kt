package com.joon.ringout.presentation.developertools

import com.joon.ringout.domain.push.PushTokenRegistrationStatus

internal data class DeveloperToolsUiState(
    val versionName: String,
    val versionCode: String,
    val buildType: String,
    val serverEnvironment: String,
    val apiBaseUrl: String,
    val pushTokenRegistrationStatus: PushTokenRegistrationStatus,
    val remoteConfig: RemoteConfigUiState? = null,
)

internal val DeveloperToolsPreviewState = DeveloperToolsUiState(
    versionName = "1.2.1",
    versionCode = "261020101",
    buildType = "debug",
    serverEnvironment = "개발",
    apiBaseUrl = "https://dev-api.ringout.my",
    pushTokenRegistrationStatus = PushTokenRegistrationStatus.Succeeded,
    remoteConfig = RemoteConfigUiState(
        minVersionCode = "1",
        valueSource = "앱 기본값",
        lastFetchTime = "가져온 적 없음",
        fetchStatus = "가져오기 전",
        listenerStatus = "없음",
        isRefreshing = false,
    ),
)
