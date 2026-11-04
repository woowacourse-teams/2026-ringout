package com.joon.ringout.data.connectivity

import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.*
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    private val mutableStatus = MutableStateFlow(NetworkStatus.Unknown)
    override val status: StateFlow<NetworkStatus> = mutableStatus.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("ringout.connectivity", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            mutableStatus.value = if (nw_path_get_status(path) == nw_path_status_satisfied) {
                NetworkStatus.Online
            } else NetworkStatus.Offline
        }
        nw_path_monitor_start(monitor)
    }
}
