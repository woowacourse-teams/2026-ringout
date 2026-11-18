package com.joon.ringout.domain.connectivity

import kotlinx.coroutines.flow.StateFlow

enum class NetworkStatus { Unknown, Online, Offline }

interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
}
