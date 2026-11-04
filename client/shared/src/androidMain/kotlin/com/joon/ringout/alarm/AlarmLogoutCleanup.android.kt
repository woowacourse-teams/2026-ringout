package com.joon.ringout.alarm

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException

private const val SchedulingPreferences = "ringout_alarm_scheduling"
private const val SchedulingBlocked = "explicit_logout_blocked"
private const val BlockedMissionOccurrences = "logout_cancelled_mission_occurrences"

internal fun isAlarmSchedulingBlocked(context: Context): Boolean =
    context.getSharedPreferences(SchedulingPreferences, Context.MODE_PRIVATE)
        .getBoolean(SchedulingBlocked, false)

internal fun isMissionReservationBlocked(context: Context, occurrenceId: String): Boolean =
    isAlarmSchedulingBlocked(context) ||
        context.getSharedPreferences(SchedulingPreferences, Context.MODE_PRIVATE)
            .getStringSet(BlockedMissionOccurrences, emptySet()).orEmpty().contains(occurrenceId)

internal fun blockMissionReservations(context: Context, occurrenceId: String) {
    val preferences = context.getSharedPreferences(SchedulingPreferences, Context.MODE_PRIVATE)
    val occurrences = preferences.getStringSet(BlockedMissionOccurrences, emptySet()).orEmpty()
    check(preferences.edit().putStringSet(BlockedMissionOccurrences, occurrences + occurrenceId).commit()) {
        "미션 예약 취소 상태를 저장하지 못했습니다."
    }
}

private fun setAlarmSchedulingBlocked(context: Context, blocked: Boolean) {
    check(
        context.getSharedPreferences(SchedulingPreferences, Context.MODE_PRIVATE)
            .edit().putBoolean(SchedulingBlocked, blocked).commit(),
    ) { "알람 예약 차단 상태를 저장하지 못했습니다." }
}

@Composable
actual fun rememberAlarmLogoutCleanup(): AlarmLogoutCleanup {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : AlarmLogoutCleanup {
            override suspend fun cancelScheduledAlarms() {
                // This native mirror also protects receivers that run before the app root exists.
                setAlarmSchedulingBlocked(context, true)
                var firstFailure: Exception? = null
                val cancellations: List<suspend () -> Unit> = listOf(
                    { AndroidAlarmScheduler(context).cancelAllForLogout() },
                    { cancelAlarmRescheduleRetry(context) },
                    { AlarmMissionCoordinator(context).cancelFutureReservationsForLogout() },
                )
                cancellations.forEach { cancel ->
                    try {
                        cancel()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        firstFailure?.addSuppressed(error) ?: run { firstFailure = error }
                    }
                }
                firstFailure?.let { throw it }
            }

            override fun allowScheduling() {
                setAlarmSchedulingBlocked(context, false)
            }
        }
    }
}
