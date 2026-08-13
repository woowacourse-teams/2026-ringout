package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

class IosAlarmReconciler(
    private val dataSource: AlarmDataSource,
    private val scheduler: IosAlarmScheduler,
    private val normalizeAlarmId: (String) -> String?,
    private val newAlarmId: () -> String = ::newAlarmId,
    private val currentMinuteOfDay: () -> Int = ::currentIosMinuteOfDay,
) {
    suspend fun reconcile(): IosAlarmReconcileReport {
        val failures = mutableListOf<IosAlarmReconcileFailure>()
        migrateNonUuidAlarmIds(failures)
        val roomAlarms = dataSource.getAll()
        val roomById = roomAlarms.associateBy { alarm -> alarm.request.id }
        val scheduledAlarms = scheduler.scheduledAlarmsAwait()
        val scheduledById = scheduledAlarms.associateBy(IosScheduledAlarmDto::alarmId)

        scheduledAlarms.forEach { scheduled ->
            val roomAlarm = roomById[scheduled.alarmId]
            if ((roomAlarm == null || !roomAlarm.enabled) &&
                scheduled.state == IosScheduledAlarmState.SCHEDULED
            ) {
                failures.capture(scheduled.alarmId) {
                    scheduler.cancelAwait(scheduled.alarmId)
                }
            }
        }

        roomAlarms.filter(SavedAlarmSchedule::enabled).forEach { roomAlarm ->
            val scheduled = scheduledById[roomAlarm.request.id]
            if (scheduled == null) {
                reconcileMissingScheduledAlarm(roomAlarm, failures)
            } else if (scheduled.state == IosScheduledAlarmState.DISABLED) {
                failures.capture(roomAlarm.request.id) {
                    dataSource.setEnabled(roomAlarm.request.id, false)
                }
            }
        }

        return IosAlarmReconcileReport(failures)
    }

    private suspend fun migrateNonUuidAlarmIds(
        failures: MutableList<IosAlarmReconcileFailure>,
    ) {
        dataSource.getAll()
            .filter { alarm -> normalizeAlarmId(alarm.request.id) == null }
            .forEach { alarm ->
                val oldId = alarm.request.id
                failures.capture(oldId) {
                    val newId = generateCanonicalAlarmId()
                    if (alarm.enabled) {
                        scheduler.scheduleAwait(alarm.request.copy(id = newId))
                    }
                    try {
                        check(dataSource.migrateId(oldId, newId)) {
                            "저장된 알람 ID를 변환하지 못했습니다."
                        }
                    } catch (error: Exception) {
                        if (alarm.enabled) scheduler.cancelAwait(newId)
                        throw error
                    }
                }
            }
    }

    private fun generateCanonicalAlarmId(): String {
        repeat(MaxAlarmIdGenerationAttempts) {
            val candidate = newAlarmId()
            normalizeAlarmId(candidate)?.let { return it }
        }
        error("정규 UUID 알람 ID를 생성하지 못했습니다.")
    }

    private suspend fun reconcileMissingScheduledAlarm(
        roomAlarm: SavedAlarmSchedule,
        failures: MutableList<IosAlarmReconcileFailure>,
    ) {
        val request = roomAlarm.request
        if (request.repeatEnabled || request.isFutureOneTime(currentMinuteOfDay())) {
            failures.capture(request.id) {
                scheduler.scheduleAwait(request)
            }
        } else {
            failures.capture(request.id) {
                dataSource.setEnabled(request.id, false)
            }
        }
    }
}

data class IosAlarmReconcileReport(
    val failures: List<IosAlarmReconcileFailure>,
) {
    val isSuccess: Boolean
        get() = failures.isEmpty()
}

data class IosAlarmReconcileFailure(
    val alarmId: String,
    val message: String,
)

private suspend fun MutableList<IosAlarmReconcileFailure>.capture(
    alarmId: String,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: Exception) {
        add(IosAlarmReconcileFailure(alarmId, error.message ?: "unknown failure"))
    }
}

private fun AlarmScheduleRequest.isFutureOneTime(currentMinuteOfDay: Int): Boolean {
    val (hour, minute) = parseAlarmTime()
    val alarmMinuteOfDay = hour * MinutesPerHour + minute
    return !repeatEnabled && selectedDays.isEmpty() && alarmMinuteOfDay > currentMinuteOfDay
}

internal suspend fun IosAlarmReconciler.reconcileBestEffort() {
    withContext(NonCancellable) {
        runCatching { reconcile() }
    }
}

private const val MinutesPerHour = 60
private const val MaxAlarmIdGenerationAttempts = 10

private fun currentIosMinuteOfDay(): Int {
    val formatter = NSDateFormatter().apply { dateFormat = "HH|mm" }
    val parts = formatter.stringFromDate(NSDate()).split("|")
    return parts[0].toInt() * MinutesPerHour + parts[1].toInt()
}
