package com.joon.ringout.alarm

import androidx.compose.runtime.Composable

/** Root-owned explicit logout cleanup. Call only after the pending logout has been persisted. */
interface AlarmLogoutCleanup {
    /** Disables saved alarms and awaits cancellation. A failure leaves scheduling suppressed. */
    suspend fun cancelScheduledAlarms()

    /** Permits future user initiated scheduling; it never enables or recreates saved alarms. */
    fun allowScheduling()
}

@Composable
expect fun rememberAlarmLogoutCleanup(): AlarmLogoutCleanup
