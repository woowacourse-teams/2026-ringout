package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

enum class IosAlarmMissionAction {
    STOP,
    OPEN_APP,
}

data class IosAlarmMissionEventDto(
    val eventId: String,
    val alarmId: String,
    val occurrenceId: String,
    val action: IosAlarmMissionAction,
    val occurredAtEpochMillis: Long,
    val retryAttempt: Int = 0,
)

data class IosAlarmMissionEventsResult(
    val events: List<IosAlarmMissionEventDto> = emptyList(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

interface IosAlarmMissionEventInbox {
    fun pendingEvents(callback: (IosAlarmMissionEventsResult) -> Unit)

    fun markConsumed(
        eventId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    )
}

class IosAlarmMissionCoordinator(
    private val dataSource: AlarmDataSource,
    private val inbox: IosAlarmMissionEventInbox,
    private val scheduler: IosAlarmScheduler,
    private val outcomeRecorder: IosMissionOutcomeRecorder,
    private val missionStore: IosActiveAlarmMissionStore = UserDefaultsIosActiveAlarmMissionStore(),
) {
    private val mutex = Mutex()
    private val activeMission = MutableStateFlow(missionStore.loadActiveMission())

    val activeMissionFlow: StateFlow<ActiveAlarmMission?> = activeMission.asStateFlow()

    fun loadLastLocation(): ActiveAlarmMissionLocation? = missionStore.loadLastLocation()

    fun hasPendingDeadlineAlarm(): Boolean = missionStore.loadDeadlineAlarm() != null

    fun hasPendingTerminal(): Boolean = missionStore.loadPendingTerminal() != null

    suspend fun recoverPendingTerminal() = mutex.withLock {
        recoverPendingTerminalLocked()
    }

    suspend fun recoverPendingRetryDelivery(): Boolean = mutex.withLock {
        if (activeMission.value != null || missionStore.loadPendingTerminal() != null) {
            return@withLock false
        }
        val registration = missionStore.loadDeadlineAlarm() ?: return@withLock false
        if (!registration.isScheduleConfirmed || registration.missionSeed == null) {
            return@withLock false
        }
        val pendingRetryWasDelivered = inbox.pendingEventsAwait().any { event ->
            event.occurrenceId == registration.retryOccurrenceId
        }
        if (pendingRetryWasDelivered) return@withLock false
        val isStillScheduled = scheduler.scheduledAlarmsAwait().any { alarm ->
            alarm.alarmId == registration.alarmKitId &&
                alarm.state != IosScheduledAlarmState.DISABLED
        }
        if (isStillScheduled) return@withLock false
        val retryArrivedBeforeRecoverySchedule = inbox.pendingEventsAwait().any { event ->
            event.occurrenceId == registration.retryOccurrenceId
        }
        if (retryArrivedBeforeRecoverySchedule) return@withLock false
        scheduleRetryAlarmLocked(registration, delaySeconds = 0.0)
        val retryArrivedDuringRecoverySchedule = inbox.pendingEventsAwait().any { event ->
            event.occurrenceId == registration.retryOccurrenceId
        }
        if (retryArrivedDuringRecoverySchedule) {
            missionStore.saveDeadlineAlarm(
                registration.copy(needsRecoveryCancellation = true),
            )
            scheduler.cancelAwait(registration.alarmKitId)
            missionStore.saveDeadlineAlarm(
                registration.copy(needsRecoveryCancellation = false),
            )
            return@withLock false
        }
        true
    }

    suspend fun processPendingEventsAfterRetryRecovery(): ActiveAlarmMission? = mutex.withLock {
        val events = inbox.pendingEventsAwait()
            .sortedBy(IosAlarmMissionEventDto::occurredAtEpochMillis)
        recoverPendingRetryCancellationLocked(events)
        var startedMission: ActiveAlarmMission? = null
        for (event in events) {
            startedMission = processEventLocked(event) ?: startedMission
        }
        startedMission
    }

    suspend fun processPendingEvents(): ActiveAlarmMission? = mutex.withLock {
        recoverPendingTerminalLocked()
        val events = inbox.pendingEventsAwait()
            .sortedBy(IosAlarmMissionEventDto::occurredAtEpochMillis)
        recoverPendingRetryCancellationLocked(events)
        var startedMission: ActiveAlarmMission? = null
        for (event in events) {
            startedMission = processEventLocked(event) ?: startedMission
        }
        startedMission
    }

    private suspend fun recoverPendingRetryCancellationLocked(
        events: List<IosAlarmMissionEventDto>,
    ) {
        val registration = missionStore.loadDeadlineAlarm() ?: return
        if (
            !registration.needsRecoveryCancellation ||
            events.none { event -> event.occurrenceId == registration.retryOccurrenceId }
        ) {
            return
        }
        scheduler.cancelAwait(registration.alarmKitId)
        missionStore.saveDeadlineAlarm(
            registration.copy(needsRecoveryCancellation = false),
        )
    }

    private suspend fun processEventLocked(event: IosAlarmMissionEventDto): ActiveAlarmMission? {
        if (missionStore.isConsumed(event.occurrenceId)) {
            inbox.markConsumedAwait(event.eventId)
            return null
        }
        val current = activeMission.value
        if (current != null) {
            if (current.occurrenceId == event.occurrenceId) {
                missionStore.loadDeadlineAlarm()
                    ?.takeIf { registration ->
                        registration.retryOccurrenceId == current.occurrenceId
                    }
                    ?.let { missionStore.clearDeadlineAlarm() }
                ensureDeadlineAlarmLocked(current)
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return current
            }
        }
        val deadlineAlarm = missionStore.loadDeadlineAlarm()
        val matchingRetry = deadlineAlarm?.takeIf { registration ->
            registration.retryOccurrenceId == event.occurrenceId &&
                (current == null || registration.sourceOccurrenceId == current.occurrenceId)
        }
        if (
            current != null &&
            matchingRetry != null &&
            current.isAwaitingDeadlineLocation(Clock.System.now().toEpochMilliseconds())
        ) {
            // The AlarmKit alert may be stopped before Core Location delivers its
            // final callback. Keep the event in the inbox until the source mission's
            // acquisition window has been resolved.
            return current
        }
        if (current != null && matchingRetry != null) {
            val deadlineEvidence = current.selectBestDeadlineLocation(
                listOfNotNull(
                    missionStore.loadDeadlineLocation(),
                    missionStore.loadLastLocation(),
                ),
            )
            if (
                deadlineEvidence != null &&
                current.hasReachedDestinationByDeadline(
                    latitude = deadlineEvidence.latitude,
                    longitude = deadlineEvidence.longitude,
                    accuracyMeters = deadlineEvidence.accuracyMeters,
                    capturedAtEpochMillis = deadlineEvidence.capturedAtEpochMillis,
                )
            ) {
                completeTerminalLocked(current, IosPendingMissionOutcome.SUCCESS)
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return null
            }
        }
        if (current != null) {
            if (matchingRetry == null) {
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return null
            }
            missionStore.clearLastLocation()
        }
        if (matchingRetry != null) {
            missionStore.clearLastLocation()
            missionStore.clearDeadlineLocation()
        }
        if (current == null) {
            if (matchingRetry == null && deadlineAlarm != null) {
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return null
            }
            if (matchingRetry == null) {
                missionStore.clearLastLocation()
                missionStore.clearDeadlineLocation()
            }
        }
        val mission = matchingRetry?.missionSeed?.toActiveAlarmMission(
            event.copy(retryAttempt = matchingRetry.retryAttempt),
        ) ?: run {
            val savedAlarm = dataSource.getById(event.alarmId)
            val requiresEnabledSourceAlarm = event.retryAttempt == 0
            if (savedAlarm == null || (requiresEnabledSourceAlarm && !savedAlarm.enabled)) {
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return null
            }
            savedAlarm.request.toActiveAlarmMission(event)
        }

        if (matchingRetry != null) {
            scheduler.cancelAwait(matchingRetry.alarmKitId)
        }
        replaceActiveMissionLocked(mission)
        if (matchingRetry != null) {
            missionStore.clearDeadlineAlarm()
        }
        ensureDeadlineAlarmLocked(mission)
        missionStore.markConsumed(event.occurrenceId)
        inbox.markConsumedAwait(event.eventId)
        return mission
    }

    private fun replaceActiveMissionLocked(mission: ActiveAlarmMission) {
        missionStore.saveActiveMission(mission)
        activeMission.value = mission
    }

    suspend fun clearActiveMission(occurrenceId: String? = null) = mutex.withLock {
        val current = activeMission.value ?: return@withLock
        if (occurrenceId != null && current.occurrenceId != occurrenceId) return@withLock
        completeTerminalLocked(current, IosPendingMissionOutcome.FAILURE)
    }

    suspend fun ensureDeadlineAlarm() = mutex.withLock {
        activeMission.value?.let { mission -> ensureDeadlineAlarmLocked(mission) }
    }

    suspend fun onLocationUpdated(
        occurrenceId: String,
        location: ActiveAlarmMissionLocation,
        shouldPersist: Boolean = true,
        evaluatedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = mutex.withLock {
        val current = activeMission.value
        ?.takeIf { mission -> mission.occurrenceId == occurrenceId }
            ?: return@withLock false
        if (
            current.isExpiredAt(evaluatedAtEpochMillis) &&
            !current.isAwaitingDeadlineLocation(evaluatedAtEpochMillis)
        ) {
            return@withLock false
        }
        if (shouldPersist) missionStore.saveLastLocation(location)
        val bestDeadlineLocation = current.selectBestDeadlineLocation(
            listOfNotNull(missionStore.loadDeadlineLocation(), location),
        )
        if (bestDeadlineLocation != null) {
            missionStore.saveDeadlineLocation(bestDeadlineLocation)
        }
        if (current.isExpiredAt(evaluatedAtEpochMillis)) {
            return@withLock false
        }
        if (
            !current.hasReachedDestination(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracyMeters,
            )
        ) {
            return@withLock false
        }

        completeTerminalLocked(current, IosPendingMissionOutcome.SUCCESS)
        true
    }

    suspend fun handleDeadline(occurrenceId: String): ActiveAlarmMission? = mutex.withLock {
        val current = activeMission.value
            ?.takeIf { mission -> mission.occurrenceId == occurrenceId }
            ?: return@withLock null
        val deadlineLocation = current.selectBestDeadlineLocation(
            listOfNotNull(
                missionStore.loadDeadlineLocation(),
                missionStore.loadLastLocation(),
            ),
        )
        if (
            deadlineLocation != null &&
            current.hasReachedDestinationByDeadline(
                latitude = deadlineLocation.latitude,
                longitude = deadlineLocation.longitude,
                accuracyMeters = deadlineLocation.accuracyMeters,
                capturedAtEpochMillis = deadlineLocation.capturedAtEpochMillis,
            )
        ) {
            completeTerminalLocked(current, IosPendingMissionOutcome.SUCCESS)
            return@withLock null
        }

        ensureDeadlineAlarmLocked(current)
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        missionStore.clearActiveMission()
        activeMission.value = null
        current
    }

    private suspend fun ensureDeadlineAlarmLocked(mission: ActiveAlarmMission) {
        val existing = missionStore.loadDeadlineAlarm()
        if (existing?.sourceOccurrenceId == mission.occurrenceId) {
            val registration = if (existing.missionSeed == null) {
                existing.copy(missionSeed = mission.toRetryMissionSeed()).also {
                    missionStore.saveDeadlineAlarm(it)
                }
            } else {
                existing
            }
            val isScheduled = scheduler.scheduledAlarmsAwait().any { alarm ->
                alarm.alarmId == registration.alarmKitId &&
                    alarm.state != IosScheduledAlarmState.DISABLED
            }
            when {
                isScheduled && !registration.isScheduleConfirmed ->
                    missionStore.saveDeadlineAlarm(
                        registration.copy(isScheduleConfirmed = true),
                    )

                !isScheduled &&
                    (!registration.isScheduleConfirmed || !mission.isExpiredAtNow()) ->
                    scheduleDeadlineAlarmLocked(mission, registration)
            }
            return
        }
        if (existing != null) {
            cancelDeadlineAlarmLocked()
        }
        val retryAttempt = mission.retryAttempt + 1
        val registration = IosMissionDeadlineAlarm(
            alarmKitId = NSUUID().UUIDString,
            sourceOccurrenceId = mission.occurrenceId,
            retryOccurrenceId =
                "${mission.occurrenceId.substringBefore(":retry-")}:retry-$retryAttempt:${NSUUID().UUIDString}",
            retryAttempt = retryAttempt,
            isScheduleConfirmed = false,
            missionSeed = mission.toRetryMissionSeed(),
        )
        missionStore.saveDeadlineAlarm(registration)
        scheduleDeadlineAlarmLocked(mission, registration)
    }

    private suspend fun scheduleDeadlineAlarmLocked(
        mission: ActiveAlarmMission,
        registration: IosMissionDeadlineAlarm,
    ) {
        val remainingMillis = (mission.expiresAtEpochMillis -
            Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
        scheduleRetryAlarmLocked(
            registration = registration,
            delaySeconds = remainingMillis / MillisPerSecond,
        )
    }

    private suspend fun scheduleRetryAlarmLocked(
        registration: IosMissionDeadlineAlarm,
        delaySeconds: Double,
    ) {
        val seed = registration.missionSeed
            ?: error("재울림 미션 스냅샷이 없습니다.")
        scheduler.scheduleRetryAwait(
            IosAlarmRetryScheduleDto(
                alarmKitId = registration.alarmKitId,
                sourceAlarmId = seed.alarmId,
                occurrenceId = registration.retryOccurrenceId,
                retryAttempt = registration.retryAttempt,
                title = seed.destinationName.ifBlank { "Ringout" },
                delaySeconds = delaySeconds,
            ),
        )
        missionStore.saveDeadlineAlarm(registration.copy(isScheduleConfirmed = true))
    }

    private suspend fun cancelDeadlineAlarmLocked() {
        val registration = missionStore.loadDeadlineAlarm() ?: return
        missionStore.markConsumed(registration.retryOccurrenceId)
        scheduler.cancelAwait(registration.alarmKitId)
        missionStore.clearDeadlineAlarm()
    }

    private suspend fun completeTerminalLocked(
        mission: ActiveAlarmMission,
        outcome: IosPendingMissionOutcome,
    ) {
        missionStore.savePendingTerminal(
            IosPendingMissionTerminal(
                occurrenceId = mission.occurrenceId,
                outcome = outcome,
                completedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        missionStore.clearActiveMission()
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        activeMission.value = null
        recoverPendingTerminalLocked()
    }

    private suspend fun recoverPendingTerminalLocked() {
        val pending = missionStore.loadPendingTerminal() ?: return
        if (activeMission.value?.occurrenceId == pending.occurrenceId) {
            missionStore.clearActiveMission()
            activeMission.value = null
        }
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        cancelDeadlineAlarmLocked()
        when (pending.outcome) {
            IosPendingMissionOutcome.SUCCESS ->
                outcomeRecorder.recordSuccess(
                    pending.occurrenceId,
                    pending.completedAtEpochMillis,
                )

            IosPendingMissionOutcome.FAILURE ->
                outcomeRecorder.recordFailure(
                    pending.occurrenceId,
                    pending.completedAtEpochMillis,
                )
        }
        missionStore.clearPendingTerminal()
    }
}

data class IosMissionDeadlineAlarm(
    val alarmKitId: String,
    val sourceOccurrenceId: String,
    val retryOccurrenceId: String,
    val retryAttempt: Int,
    val isScheduleConfirmed: Boolean = false,
    val missionSeed: IosRetryMissionSeed? = null,
    val needsRecoveryCancellation: Boolean = false,
)

data class IosRetryMissionSeed(
    val alarmId: String,
    val destinationName: String,
    val limitMinutes: Int,
    val alarmTime: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val arrivalRadiusMeters: Double,
    val alarmSoundUri: String?,
    val hasAlarmSoundUri: Boolean,
)

enum class IosPendingMissionOutcome {
    SUCCESS,
    FAILURE,
}

data class IosPendingMissionTerminal(
    val occurrenceId: String,
    val outcome: IosPendingMissionOutcome,
    val completedAtEpochMillis: Long,
)

interface IosMissionOutcomeRecorder {
    suspend fun recordSuccess(occurrenceId: String, completedAtEpochMillis: Long)
    suspend fun recordFailure(occurrenceId: String, completedAtEpochMillis: Long)
}

interface IosActiveAlarmMissionStore {
    fun loadActiveMission(): ActiveAlarmMission?
    fun saveActiveMission(mission: ActiveAlarmMission)
    fun clearActiveMission()
    fun isConsumed(occurrenceId: String): Boolean
    fun markConsumed(occurrenceId: String)
    fun loadLastLocation(): ActiveAlarmMissionLocation?
    fun saveLastLocation(location: ActiveAlarmMissionLocation)
    fun clearLastLocation()
    fun loadDeadlineLocation(): ActiveAlarmMissionLocation?
    fun saveDeadlineLocation(location: ActiveAlarmMissionLocation)
    fun clearDeadlineLocation()
    fun loadDeadlineAlarm(): IosMissionDeadlineAlarm?
    fun saveDeadlineAlarm(registration: IosMissionDeadlineAlarm)
    fun clearDeadlineAlarm()
    fun loadPendingTerminal(): IosPendingMissionTerminal?
    fun savePendingTerminal(pending: IosPendingMissionTerminal)
    fun clearPendingTerminal()
}

internal class UserDefaultsIosActiveAlarmMissionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : IosActiveAlarmMissionStore {
    override fun loadActiveMission(): ActiveAlarmMission? {
        val rawRecord = defaults.stringArrayForKey(KeyActiveMission) ?: return null
        if (rawRecord.size !in LegacyActiveMissionRecordSize..ActiveMissionRecordSize) return null
        val record = rawRecord.map { value -> value as? String ?: return null }
        val alarmId = record[0].takeIf { it.isNotBlank() } ?: return null
        val occurrenceId = record[1].takeIf { it.isNotBlank() } ?: return null
        val limitMinutes = record[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val expiresAt = record[4].toLongOrNull() ?: return null
        val startedAt = record[5].toLongOrNull() ?: return null
        val latitude = record[7].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val longitude = record[8].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val radiusMeters = record[9].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        val hasSoundUri = record[11].toBooleanStrictOrNull() ?: return null
        if (expiresAt <= startedAt) return null
        return ActiveAlarmMission(
            alarmId = alarmId,
            destinationName = record[2],
            limitMinutes = limitMinutes,
            expiresAtEpochMillis = expiresAt,
            occurrenceId = occurrenceId,
            retryAttempt = record.getOrNull(12)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            alarmTime = record[6],
            startedAtEpochMillis = startedAt,
            destinationLatitude = latitude,
            destinationLongitude = longitude,
            arrivalRadiusMeters = radiusMeters,
            alarmSoundUri = record[10].takeIf { hasSoundUri },
            hasAlarmSoundUri = hasSoundUri,
        )
    }

    override fun saveActiveMission(mission: ActiveAlarmMission) {
        defaults.setObject(
            listOf(
                mission.alarmId,
                mission.occurrenceId,
                mission.destinationName,
                mission.limitMinutes.toString(),
                mission.expiresAtEpochMillis.toString(),
                mission.startedAtEpochMillis.toString(),
                mission.alarmTime,
                mission.destinationLatitude.toString(),
                mission.destinationLongitude.toString(),
                mission.arrivalRadiusMeters.toString(),
                mission.alarmSoundUri.orEmpty(),
                mission.hasAlarmSoundUri.toString(),
                mission.retryAttempt.toString(),
            ),
            forKey = KeyActiveMission,
        )
    }

    override fun clearActiveMission() {
        defaults.removeObjectForKey(KeyActiveMission)
    }

    override fun isConsumed(occurrenceId: String): Boolean =
        occurrenceId in consumedOccurrenceIds()

    override fun markConsumed(occurrenceId: String) {
        val updated = (consumedOccurrenceIds() + occurrenceId).takeLast(MaxConsumedOccurrences)
        defaults.setObject(updated.joinToString("\n"), forKey = KeyConsumedOccurrences)
    }

    override fun loadLastLocation(): ActiveAlarmMissionLocation? {
        val record = defaults.stringArrayForKey(KeyLastLocation)
            ?.map { value -> value as? String ?: return null }
            ?: return null
        if (record.size != LastLocationRecordSize) return null
        val latitude = record[0].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val longitude = record[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val accuracy = record[2].toFloatOrNull()?.takeIf(Float::isFinite) ?: return null
        val capturedAt = record[3].toLongOrNull() ?: return null
        return ActiveAlarmMissionLocation(latitude, longitude, accuracy, capturedAt)
    }

    override fun saveLastLocation(location: ActiveAlarmMissionLocation) {
        defaults.setObject(
            listOf(
                location.latitude.toString(),
                location.longitude.toString(),
                location.accuracyMeters.toString(),
                location.capturedAtEpochMillis.toString(),
            ),
            forKey = KeyLastLocation,
        )
    }

    override fun clearLastLocation() {
        defaults.removeObjectForKey(KeyLastLocation)
    }

    override fun loadDeadlineLocation(): ActiveAlarmMissionLocation? =
        defaults.stringArrayForKey(KeyDeadlineLocation)
            ?.map { value -> value as? String ?: return null }
            ?.toMissionLocation()

    override fun saveDeadlineLocation(location: ActiveAlarmMissionLocation) {
        defaults.setObject(location.toRecord(), forKey = KeyDeadlineLocation)
    }

    override fun clearDeadlineLocation() {
        defaults.removeObjectForKey(KeyDeadlineLocation)
    }

    override fun loadDeadlineAlarm(): IosMissionDeadlineAlarm? {
        val record = defaults.stringArrayForKey(KeyDeadlineAlarm)
            ?.map { value -> value as? String ?: return null }
            ?: return null
        if (
            record.size != LegacyDeadlineAlarmRecordSize &&
            record.size != DeadlineAlarmRecordSizeV2 &&
            record.size != DeadlineAlarmRecordSize
        ) {
            return null
        }
        val missionSeed = if (record.size >= DeadlineAlarmRecordSizeV2) {
            IosRetryMissionSeed(
                alarmId = record[5].takeIf(String::isNotBlank) ?: return null,
                destinationName = record[6],
                limitMinutes = record[7].toIntOrNull()?.takeIf { it > 0 } ?: return null,
                alarmTime = record[8],
                destinationLatitude =
                    record[9].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null,
                destinationLongitude =
                    record[10].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null,
                arrivalRadiusMeters = record[11].toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it > 0 } ?: return null,
                alarmSoundUri = record[12].takeIf { record[13].toBooleanStrictOrNull() == true },
                hasAlarmSoundUri = record[13].toBooleanStrictOrNull() ?: return null,
            )
        } else {
            null
        }
        return IosMissionDeadlineAlarm(
            alarmKitId = record[0].takeIf(String::isNotBlank) ?: return null,
            sourceOccurrenceId = record[1].takeIf(String::isNotBlank) ?: return null,
            retryOccurrenceId = record[2].takeIf(String::isNotBlank) ?: return null,
            retryAttempt = record[3].toIntOrNull()?.takeIf { it > 0 } ?: return null,
            isScheduleConfirmed = record.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
            missionSeed = missionSeed,
            needsRecoveryCancellation =
                record.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
        )
    }

    override fun saveDeadlineAlarm(registration: IosMissionDeadlineAlarm) {
        defaults.setObject(
            listOf(
                registration.alarmKitId,
                registration.sourceOccurrenceId,
                registration.retryOccurrenceId,
                registration.retryAttempt.toString(),
                registration.isScheduleConfirmed.toString(),
                registration.missionSeed?.alarmId.orEmpty(),
                registration.missionSeed?.destinationName.orEmpty(),
                registration.missionSeed?.limitMinutes?.toString().orEmpty(),
                registration.missionSeed?.alarmTime.orEmpty(),
                registration.missionSeed?.destinationLatitude?.toString().orEmpty(),
                registration.missionSeed?.destinationLongitude?.toString().orEmpty(),
                registration.missionSeed?.arrivalRadiusMeters?.toString().orEmpty(),
                registration.missionSeed?.alarmSoundUri.orEmpty(),
                registration.missionSeed?.hasAlarmSoundUri?.toString().orEmpty(),
                registration.needsRecoveryCancellation.toString(),
            ),
            forKey = KeyDeadlineAlarm,
        )
    }

    override fun clearDeadlineAlarm() {
        defaults.removeObjectForKey(KeyDeadlineAlarm)
    }

    override fun loadPendingTerminal(): IosPendingMissionTerminal? {
        val record = defaults.stringArrayForKey(KeyPendingTerminal)
            ?.map { value -> value as? String ?: return null }
            ?: return null
        if (record.size !in LegacyPendingTerminalRecordSize..PendingTerminalRecordSize) return null
        return IosPendingMissionTerminal(
            occurrenceId = record[0].takeIf(String::isNotBlank) ?: return null,
            outcome = runCatching { IosPendingMissionOutcome.valueOf(record[1]) }.getOrNull()
                ?: return null,
            completedAtEpochMillis = record.getOrNull(2)?.toLongOrNull()
                ?: Clock.System.now().toEpochMilliseconds(),
        )
    }

    override fun savePendingTerminal(pending: IosPendingMissionTerminal) {
        defaults.setObject(
            listOf(
                pending.occurrenceId,
                pending.outcome.name,
                pending.completedAtEpochMillis.toString(),
            ),
            forKey = KeyPendingTerminal,
        )
    }

    override fun clearPendingTerminal() {
        defaults.removeObjectForKey(KeyPendingTerminal)
    }

    private fun consumedOccurrenceIds(): List<String> =
        defaults.stringForKey(KeyConsumedOccurrences)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.toList()
            .orEmpty()

    private companion object {
        const val KeyActiveMission = "ios.activeMission.record.v2"
        const val KeyLastLocation = "ios.activeMission.lastLocation.v1"
        const val KeyDeadlineLocation = "ios.activeMission.deadlineLocation.v1"
        const val KeyDeadlineAlarm = "ios.activeMission.deadlineAlarm.v1"
        const val KeyPendingTerminal = "ios.activeMission.pendingTerminal.v1"
        const val KeyConsumedOccurrences = "ios.activeMission.consumedOccurrences"
        const val MaxConsumedOccurrences = 200
        const val LegacyActiveMissionRecordSize = 12
        const val ActiveMissionRecordSize = 13
        const val LastLocationRecordSize = 4
        const val LegacyDeadlineAlarmRecordSize = 4
        const val DeadlineAlarmRecordSizeV2 = 14
        const val DeadlineAlarmRecordSize = 15
        const val LegacyPendingTerminalRecordSize = 2
        const val PendingTerminalRecordSize = 3
    }
}

private fun List<String>.toMissionLocation(): ActiveAlarmMissionLocation? {
    if (size != 4) return null
    val latitude = this[0].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
    val longitude = this[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
    val accuracy = this[2].toFloatOrNull()?.takeIf(Float::isFinite) ?: return null
    val capturedAt = this[3].toLongOrNull() ?: return null
    return ActiveAlarmMissionLocation(latitude, longitude, accuracy, capturedAt)
}

private fun ActiveAlarmMissionLocation.toRecord(): List<String> = listOf(
    latitude.toString(),
    longitude.toString(),
    accuracyMeters.toString(),
    capturedAtEpochMillis.toString(),
)

private fun AlarmScheduleRequest.toActiveAlarmMission(
    event: IosAlarmMissionEventDto,
): ActiveAlarmMission =
    ActiveAlarmMission(
        alarmId = id,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        expiresAtEpochMillis = event.occurredAtEpochMillis + limitMinutes * MillisPerMinute,
        occurrenceId = event.occurrenceId,
        retryAttempt = event.retryAttempt,
        alarmTime = time,
        startedAtEpochMillis = event.occurredAtEpochMillis,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = targetDistanceKm * MetersPerKilometer,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = alarmSoundUri != null,
    )

private fun ActiveAlarmMission.toRetryMissionSeed(): IosRetryMissionSeed =
    IosRetryMissionSeed(
        alarmId = alarmId,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        alarmTime = alarmTime,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = arrivalRadiusMeters,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = hasAlarmSoundUri,
    )

private fun ActiveAlarmMission.isExpiredAtNow(): Boolean =
    isExpiredAt(Clock.System.now().toEpochMilliseconds())

private fun IosRetryMissionSeed.toActiveAlarmMission(
    event: IosAlarmMissionEventDto,
): ActiveAlarmMission =
    ActiveAlarmMission(
        alarmId = alarmId,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        expiresAtEpochMillis = event.occurredAtEpochMillis + limitMinutes * MillisPerMinute,
        occurrenceId = event.occurrenceId,
        retryAttempt = event.retryAttempt,
        alarmTime = alarmTime,
        startedAtEpochMillis = event.occurredAtEpochMillis,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = arrivalRadiusMeters,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = hasAlarmSoundUri,
    )

internal suspend fun IosAlarmMissionEventInbox.pendingEventsAwait():
    List<IosAlarmMissionEventDto> {
    val result = awaitSingleCallback<IosAlarmMissionEventsResult> { callback ->
        pendingEvents(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("알람 미션 이벤트를 읽지 못했습니다.")
    return result.events
}

internal suspend fun IosAlarmMissionEventInbox.markConsumedAwait(eventId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        markConsumed(eventId, callback)
    }
    result.requireAlarmKitSuccess("알람 미션 이벤트를 소비 처리하지 못했습니다.")
}

private const val MillisPerMinute = 60_000L
private const val MetersPerKilometer = 1_000.0
private const val MillisPerSecond = 1_000.0
