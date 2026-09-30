package com.joon.ringout.domain.connectivity

import kotlinx.coroutines.flow.Flow

enum class NetworkStatus { Unknown, Online, Offline }

interface NetworkMonitor {
    val status: Flow<NetworkStatus>
}
