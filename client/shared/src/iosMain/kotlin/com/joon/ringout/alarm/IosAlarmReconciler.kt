package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSUserDefaults

class IosAlarmReconciler(
    private val dataSource: AlarmDataSource,
    private val scheduler: IosAlarmScheduler,
    private val normalizeAlarmId: (String) -> String?,
    private val presentationMigrationState: IosAlarmPresentationMigrationState =
        CompletedIosAlarmPresentationMigrationState,
    private val newAlarmId: () -> String = ::newAlarmId,
    private val currentMinuteOfDay: () -> Int = ::currentIosMinuteOfDay,
) {
    suspend fun reconcile(): IosAlarmReconcileReport {
        val failures = mutableListOf<IosAlarmReconcileFailure>()
        val shouldMigratePresentation = !presentationMigrationState.isMigrationComplete(
            CurrentAlarmPresentationVersion,
        )
        migrateNonUuidAlarmIds(failures, shouldMigratePresentation)
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
                reconcileMissingScheduledAlarm(
                    roomAlarm = roomAlarm,
                    failures = failures,
                    shouldMarkPresentationMigrated = shouldMigratePresentation,
                )
            } else if (scheduled.state == IosScheduledAlarmState.DISABLED) {
                failures.capture(roomAlarm.request.id) {
                    dataSource.setEnabled(roomAlarm.request.id, false)
                }
            } else if (shouldMigratePresentation) {
                migrateScheduledPresentationIfNeeded(roomAlarm, scheduled, failures)
            }
        }

        if (shouldMigratePresentation) completePresentationMigrationIfReady()

        return IosAlarmReconcileReport(failures)
    }

    private suspend fun migrateNonUuidAlarmIds(
        failures: MutableList<IosAlarmReconcileFailure>,
        shouldMarkPresentationMigrated: Boolean,
    ) {
        dataSource.getAll()
            .filter { alarm -> normalizeAlarmId(alarm.request.id) == null }
            .forEach { alarm ->
                val oldId = alarm.request.id
                failures.capture(oldId) {
                    val newId = generateCanonicalAlarmId()
                    if (alarm.enabled) {
                        scheduleWithCurrentPresentation(
                            request = alarm.request.copy(id = newId),
                            shouldMarkPresentationMigrated = shouldMarkPresentationMigrated,
                        )
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
        shouldMarkPresentationMigrated: Boolean,
    ) {
        val request = roomAlarm.request
        if (request.repeatEnabled || request.isFutureOneTime(currentMinuteOfDay())) {
            failures.capture(request.id) {
                scheduleWithCurrentPresentation(request, shouldMarkPresentationMigrated)
            }
        } else {
            failures.capture(request.id) {
                dataSource.setEnabled(request.id, false)
            }
        }
    }

    private suspend fun migrateScheduledPresentationIfNeeded(
        roomAlarm: SavedAlarmSchedule,
        scheduled: IosScheduledAlarmDto,
        failures: MutableList<IosAlarmReconcileFailure>,
    ) {
        val request = roomAlarm.request
        if (scheduled.state != IosScheduledAlarmState.SCHEDULED) return
        if (!request.repeatEnabled && !request.isFutureOneTime(currentMinuteOfDay())) return
        if (
            request.id in presentationMigrationState.migratedAlarmIds(
                CurrentAlarmPresentationVersion,
            )
        ) {
            return
        }

        failures.capture(request.id) {
            scheduleWithCurrentPresentation(request, shouldMarkPresentationMigrated = true)
        }
    }

    private suspend fun scheduleWithCurrentPresentation(
        request: AlarmScheduleRequest,
        shouldMarkPresentationMigrated: Boolean,
    ) {
        scheduler.scheduleAwait(request)
        if (shouldMarkPresentationMigrated) {
            presentationMigrationState.markAlarmMigrated(
                alarmId = request.id,
                version = CurrentAlarmPresentationVersion,
            )
        }
    }

    private suspend fun completePresentationMigrationIfReady() {
        val migratedIds = presentationMigrationState.migratedAlarmIds(
            CurrentAlarmPresentationVersion,
        )
        val hasPendingAlarm = dataSource.getEnabled().any { alarm ->
            val request = alarm.request
            (request.repeatEnabled || request.isFutureOneTime(currentMinuteOfDay())) &&
                request.id !in migratedIds
        }
        if (!hasPendingAlarm) {
            presentationMigrationState.markMigrationComplete(CurrentAlarmPresentationVersion)
        }
    }
}

interface IosAlarmPresentationMigrationState {
    fun isMigrationComplete(version: Int): Boolean

    fun migratedAlarmIds(version: Int): Set<String>

    fun markAlarmMigrated(alarmId: String, version: Int)

    fun markMigrationComplete(version: Int)
}

private object CompletedIosAlarmPresentationMigrationState :
    IosAlarmPresentationMigrationState {
    override fun isMigrationComplete(version: Int) = true

    override fun migratedAlarmIds(version: Int) = emptySet<String>()

    override fun markAlarmMigrated(alarmId: String, version: Int) = Unit

    override fun markMigrationComplete(version: Int) = Unit
}

internal class UserDefaultsIosAlarmPresentationMigrationState(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : IosAlarmPresentationMigrationState {
    override fun isMigrationComplete(version: Int): Boolean {
        prepare(version)
        return defaults.integerForKey(KeyCompletedVersion) == version.toLong()
    }

    override fun migratedAlarmIds(version: Int): Set<String> {
        prepare(version)
        return defaults.stringForKey(KeyMigratedAlarmIds)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()
    }

    override fun markAlarmMigrated(alarmId: String, version: Int) {
        val migratedIds = migratedAlarmIds(version) + alarmId
        defaults.setObject(migratedIds.sorted().joinToString("\n"), forKey = KeyMigratedAlarmIds)
    }

    override fun markMigrationComplete(version: Int) {
        prepare(version)
        defaults.setInteger(version.toLong(), forKey = KeyCompletedVersion)
    }

    private fun prepare(version: Int) {
        if (defaults.integerForKey(KeyTargetVersion) == version.toLong()) return
        defaults.setInteger(version.toLong(), forKey = KeyTargetVersion)
        defaults.removeObjectForKey(KeyMigratedAlarmIds)
        defaults.removeObjectForKey(KeyCompletedVersion)
    }

    private companion object {
        const val KeyTargetVersion = "ios.alarm.presentationMigration.targetVersion"
        const val KeyCompletedVersion = "ios.alarm.presentationMigration.completedVersion"
        const val KeyMigratedAlarmIds = "ios.alarm.presentationMigration.migratedAlarmIds"
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
private const val CurrentAlarmPresentationVersion = 2

private fun currentIosMinuteOfDay(): Int {
    val formatter = NSDateFormatter().apply { dateFormat = "HH|mm" }
    val parts = formatter.stringFromDate(NSDate()).split("|")
    return parts[0].toInt() * MinutesPerHour + parts[1].toInt()
}
