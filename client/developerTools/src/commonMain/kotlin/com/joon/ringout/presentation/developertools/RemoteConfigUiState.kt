package com.joon.ringout.presentation.developertools

/** Platform-neutral diagnostic information shown only when provided by a platform. */
data class RemoteConfigUiState(
    val minVersionCode: String,
    val valueSource: String,
    val lastFetchTime: String,
    val fetchStatus: String,
    val listenerStatus: String,
    val isRefreshing: Boolean,
)
