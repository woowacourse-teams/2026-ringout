package com.joon.ringout.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.platform.LocalIosNativeServices
import platform.Foundation.NSUserDefaults

private const val SchedulingBlocked = "ringout.alarm.explicit_logout_blocked"
private const val BlockedReservationIds = "ringout.alarm.logout_cancelled_reservation_ids"

internal fun isIosAlarmSchedulingBlocked(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(SchedulingBlocked)

internal fun isIosMissionReservationBlocked(reservationId: String): Boolean =
    isIosAlarmSchedulingBlocked() ||
        NSUserDefaults.standardUserDefaults.stringArrayForKey(BlockedReservationIds)
            .orEmpty().contains(reservationId)

@Composable
actual fun rememberAlarmLogoutCleanup(): AlarmLogoutCleanup {
    val services = LocalIosNativeServices.current
    return remember(services) {
        val store = IosAlarmStore(
            dataSource = RoomAlarmDataSource(getRingoutDatabase().alarmDao()),
            scheduler = services.alarmScheduler(),
            recoveryInbox = services.alarmMissionEventInbox(),
        )
        object : AlarmLogoutCleanup {
            override suspend fun cancelScheduledAlarms() {
                val defaults = NSUserDefaults.standardUserDefaults
                defaults.setBool(true, SchedulingBlocked)
                defaults.setInteger(
                    defaults.integerForKey("ringout.alarm.logout_generation") + 1,
                    "ringout.alarm.logout_generation",
                )
                val deadlineId = defaults.stringArrayForKey("ios.activeMission.deadlineAlarm.v1")
                    ?.firstOrNull() as? String
                if (deadlineId != null) {
                    val ids = defaults.stringArrayForKey(BlockedReservationIds).orEmpty()
                    defaults.setObject((ids + deadlineId).distinct(), BlockedReservationIds)
                }
                store.cancelAllForLogout { services.normalizeAlarmId(it) != null }
            }

            override fun allowScheduling() {
                NSUserDefaults.standardUserDefaults.setBool(false, SchedulingBlocked)
            }
        }
    }
}
