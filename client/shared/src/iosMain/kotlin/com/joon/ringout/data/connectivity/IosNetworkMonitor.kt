package com.joon.ringout.data.connectivity

import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.*
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    override val status = callbackFlow {
        trySend(NetworkStatus.Unknown)
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("ringout.connectivity", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(if (nw_path_get_status(path) == nw_path_status_satisfied) NetworkStatus.Online else NetworkStatus.Offline)
        }
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.distinctUntilChanged()
}
