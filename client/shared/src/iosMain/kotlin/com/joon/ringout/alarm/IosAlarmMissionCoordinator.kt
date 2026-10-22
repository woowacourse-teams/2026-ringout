package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.analytics.IosAlarmAnalyticsRecorder
import kotlinx.coroutines.CancellationException
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
    val ringingObservedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val scheduleVersion: Long = 1,
    val ownerAccountId: String? = null,
    val ownerCaptured: Boolean = false,
    val chainId: String? = null,
)

data class IosAlarmMissionEventsResult(
    val events: List<IosAlarmMissionEventDto> = emptyList(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

interface IosAlarmMissionEventListener {
    fun onEventRecorded()
}

interface IosAlarmMissionEventInbox {
    fun setEventListener(listener: IosAlarmMissionEventListener?)

    fun recordStopEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int,
        scheduleVersion: Long,
        chainId: String?,
        callback: (IosAlarmOperationResult) -> Unit,
    )

    fun recordOpenEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int,
        scheduleVersion: Long,
        chainId: String?,
        callback: (IosAlarmOperationResult) -> Unit,
    )

    fun pendingEvents(callback: (IosAlarmMissionEventsResult) -> Unit)

    /** All durable record projections, including events already consumed by mission recovery. */
    fun recordProjectionEvents(callback: (IosAlarmMissionEventsResult) -> Unit) {
        pendingEvents(callback)
    }

    fun markConsumed(
        eventId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    )

    fun ackRecordEventProjection(
        eventId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        markConsumed(eventId, callback)
    }

    fun upsertSourceSnapshot(
        snapshot: IosAlarmRecoverySourceSnapshotDto,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun setSourceEligibility(
        alarmId: String,
        scheduleVersion: Long,
        eligibility: IosAlarmRecoveryEligibility,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun recoveryState(callback: (IosAlarmRecoveryAttemptsResult) -> Unit) {
        callback(IosAlarmRecoveryAttemptsResult())
    }

    fun recoverReservations(callback: (IosAlarmRecoveryAttemptsResult) -> Unit) {
        callback(IosAlarmRecoveryAttemptsResult())
    }

    fun ackMissionProjection(
        occurrenceId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun ackRecordProjection(
        occurrenceId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun commitTerminal(
        chainId: String,
        outcome: IosAlarmRecoveryAttemptOutcome,
        completedAtEpochMillis: Long,
        occurrenceId: String?,
        createArrivalIntent: Boolean,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun ackTerminalRecord(
        chainId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    fun protectedReservationUuids(callback: (IosAlarmProtectedReservationsResult) -> Unit) {
        callback(IosAlarmProtectedReservationsResult())
    }

    fun nativeCleanupCandidates(callback: (IosAlarmNativeCleanupCandidatesResult) -> Unit) {
        callback(IosAlarmNativeCleanupCandidatesResult(
            code = IosAlarmOperationCode.SDK_ERROR,
            message = "Native cleanup is unsupported by this inbox.",
        ))
    }

    fun commitEventCleanup(
        eventId: String,
        fingerprint: String,
        callback: (IosAlarmNativeCleanupCommitResult) -> Unit,
    ) {
        callback(IosAlarmNativeCleanupCommitResult(
            code = IosAlarmOperationCode.SDK_ERROR,
            message = "Native cleanup is unsupported by this inbox.",
        ))
    }

    fun compactTerminalChain(
        chainId: String,
        fingerprint: String,
        callback: (IosAlarmNativeCleanupCommitResult) -> Unit,
    ) {
        callback(IosAlarmNativeCleanupCommitResult(
            code = IosAlarmOperationCode.SDK_ERROR,
            message = "Native cleanup is unsupported by this inbox.",
        ))
    }
}

data class IosAlarmProtectedReservationsResult(
    val reservationUuids: Set<String> = emptySet(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

class IosAlarmMissionCoordinator(
    private val dataSource: AlarmDataSource,
    private val inbox: IosAlarmMissionEventInbox,
    private val scheduler: IosAlarmScheduler,
    private val outcomeRecorder: IosMissionOutcomeRecorder,
    private val missionStore: IosActiveAlarmMissionStore = UserDefaultsIosActiveAlarmMissionStore(),
    private val analytics: IosAlarmAnalyticsRecorder? = null,
    private val arrivalNotifier: IosMissionArrivalNotifier = NoOpIosMissionArrivalNotifier,
) {
    private val mutex = Mutex()
    private val activeMission = MutableStateFlow<ActiveAlarmMission?>(null)

    init {
        val loadedMission = missionStore.loadActiveMission()
        val restoredMission = loadedMission
            ?.withDefaultArrivalRadius()
            .takeUnless { mission ->
                missionStore.loadPendingTerminal()?.occurrenceId == mission?.occurrenceId
            }
        if (restoredMission != null && restoredMission != loadedMission) {
            missionStore.saveActiveMission(restoredMission)
        }
        activeMission.value = restoredMission
    }

    val activeMissionFlow: StateFlow<ActiveAlarmMission?> = activeMission.asStateFlow()

    fun loadLastLocation(): ActiveAlarmMissionLocation? = missionStore.loadLastLocation()

    fun hasPendingDeadlineAlarm(): Boolean = missionStore.loadDeadlineAlarm() != null

    fun hasPendingTerminal(): Boolean = missionStore.loadPendingTerminal() != null

    /** Hold mission transitions out while native code rechecks and commits terminal compaction. */
    internal suspend fun compactIfUnprotected(
        references: Set<String>,
        compact: suspend () -> Boolean,
    ): Boolean = mutex.withLock {
        val active = activeMission.value
        val pending = missionStore.loadPendingTerminal()
        val deadline = missionStore.loadDeadlineAlarm()
        val protected = buildSet {
            active?.let { add(it.chainId); add(it.occurrenceId) }
            pending?.let { add(it.chainId); add(it.occurrenceId) }
            deadline?.let { add(it.sourceOccurrenceId); add(it.retryOccurrenceId) }
        }
        if (references.any(protected::contains)) return@withLock false
        compact()
    }

    internal fun deadlineAlarmForSystemId(systemAlarmId: String): IosMissionDeadlineAlarm? =
        missionStore.loadDeadlineAlarm()?.takeIf { registration ->
            registration.alarmKitId == systemAlarmId
        }

    suspend fun recoverPendingTerminal() = mutex.withLock {
        recoverPendingTerminalLocked()
    }

    suspend fun recoverPendingRetryDelivery(): Boolean = mutex.withLock {
        if (activeMission.value != null || missionStore.loadPendingTerminal() != null) {
            return@withLock false
        }
        val registration = missionStore.loadDeadlineAlarm() ?: return@withLock false
        if (registration.isNativeRecovery) return@withLock false
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
        val nativeRecovered = recoverNativeAttemptsLocked()
        val events = inbox.pendingEventsAwait().map(::withKnownChainId)
            .sortedBy(IosAlarmMissionEventDto::occurredAtEpochMillis)
        recoverPendingRetryCancellationLocked(events)
        var startedMission: ActiveAlarmMission? = nativeRecovered
        for (event in events) {
            startedMission = processEventLocked(event) ?: startedMission
        }
        startedMission
    }

    suspend fun processPendingEvents(): ActiveAlarmMission? = mutex.withLock {
        recoverPendingTerminalLocked()
        val nativeRecovered = recoverNativeAttemptsLocked()
        val events = inbox.pendingEventsAwait().map(::withKnownChainId)
            .sortedBy(IosAlarmMissionEventDto::occurredAtEpochMillis)
        recoverPendingRetryCancellationLocked(events)
        var startedMission: ActiveAlarmMission? = nativeRecovered
        for (event in events) {
            startedMission = processEventLocked(event) ?: startedMission
        }
        startedMission
    }

    /** Replays only record projections after auth resolves. It never restores a mission or schedules an alarm. */
    suspend fun processPendingRecordProjections() = mutex.withLock {
        outcomeRecorder.resolvePendingRecordDecisions()
        val recovery = inbox.recoveryAttemptsAwait()
        val recordReady = mutableSetOf<String>()
        recovery.attempts.sortedWith(compareBy<IosAlarmRecoveryAttemptDto> { it.chainId }.thenBy { it.sequence })
            .forEach { attempt ->
                val ready = try {
                    outcomeRecorder.recordRecoveryAttempt(attempt)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
                if (ready) {
                    recordReady += attempt.occurrenceId
                    if (!attempt.recordProjectionAck) inbox.ackRecordProjectionAwait(attempt.occurrenceId)
                }
            }
        recovery.terminals.filterNot { it.terminalRecordAck }.forEach { terminal ->
            if (terminal.outcome == IosAlarmRecoveryAttemptOutcome.SKIPPED_UNSUPPORTED_OVERLAP) {
                inbox.ackTerminalRecordAwait(terminal.chainId)
            } else if (terminal.occurrenceId in recordReady) {
                when (terminal.outcome) {
                    IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED -> if (!recordSuccessAndNotify(
                        terminal.chainId,
                        terminal.occurrenceId, iosMissionDate(terminal.completedAtEpochMillis),
                        terminal.completedAtEpochMillis,
                    )) return@forEach
                    IosAlarmRecoveryAttemptOutcome.TERMINAL_FORCED_STOP -> outcomeRecorder.recordFailure(
                        terminal.occurrenceId, iosMissionDate(terminal.completedAtEpochMillis),
                        terminal.completedAtEpochMillis,
                    )
                    else -> Unit
                }
                inbox.ackTerminalRecordAwait(terminal.chainId)
            }
        }
        inbox.recordProjectionEventsAwait().map(::withKnownChainId).forEach { event ->
            val ready = try {
                outcomeRecorder.recordRingingTimes(event)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                false
            }
            if (ready) inbox.ackRecordEventProjectionAwait(event.eventId)
        }
    }

    /** Revalidates delayed app results without invoking legacy import or reservation recovery. */
    suspend fun applyDirectDismissResult(
        result: IosAlarmDismissResult,
        commands: IosAlarmMissionCommands,
    ): Boolean = mutex.withLock {
        if (result.code != IosAlarmOperationCode.SUCCESS ||
            result.recovery.code != IosAlarmOperationCode.SUCCESS ||
            result.disposition !in setOf(IosAlarmDismissDisposition.HANDLED, IosAlarmDismissDisposition.PRESERVED)
        ) return@withLock false
        val occurrenceId = result.occurrenceId ?: return@withLock false
        // An await may overlap an Intent, terminal commit, or a newer retry. Never apply its old snapshot.
        val latest = try {
            commands.readRecoverySnapshotAwait()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return@withLock false
        }
        val handled = latest.attempts.any { it.occurrenceId == occurrenceId } ||
            latest.terminals.any { it.occurrenceId == occurrenceId } ||
            result.recovery.attempts.firstOrNull { it.occurrenceId == occurrenceId }?.chainId?.let { chain ->
                latest.terminals.any { it.chainId == chain }
            } == true
        if (!handled) return@withLock false
        applyNativeRecoveryLocked(latest, journalOnly = true)
        true
    }

    private suspend fun recoverNativeAttemptsLocked(): ActiveAlarmMission? {
        val recovered = applyNativeRecoveryLocked(inbox.recoveryAttemptsAwait())
        return refreshNativeReservationsLocked(recovered)
    }

    private suspend fun applyNativeRecoveryLocked(
        recovery: IosAlarmRecoveryAttemptsResult,
        journalOnly: Boolean = false,
    ): ActiveAlarmMission? {
        val terminalChains = recovery.terminals.map { it.chainId }.toSet() +
            listOfNotNull(missionStore.loadPendingTerminal()?.chainId)
        if (activeMission.value?.chainId in terminalChains) {
            missionStore.clearActiveMission()
            missionStore.clearDeadlineAlarm()
            activeMission.value = null
        }
        val attempts = recovery.attempts.sortedWith(
            compareBy<IosAlarmRecoveryAttemptDto> { it.stopAtEpochMillis }.thenBy { it.sequence },
        )
        // Record replay is independent of mission visibility and ordered by parent.
        val recordReady = mutableSetOf<String>()
        attempts.forEach { attempt ->
            runCatching {
                if (outcomeRecorder.recordRecoveryAttempt(attempt)) {
                    recordReady += attempt.occurrenceId
                    inbox.ackRecordProjectionAwait(attempt.occurrenceId)
                }
            }
        }
        var recovered: ActiveAlarmMission? = null
        attempts.filter { it.chainId !in terminalChains }
            .groupBy { it.chainId }
            .values.sortedBy { it.first().stopAtEpochMillis }
            .forEach { chain ->
                val latest = chain.maxBy { it.sequence }
                val current = activeMission.value
                if (current != null && current.chainId == latest.chainId && current.retryAttempt > latest.sequence) {
                    return@forEach
                }
                if (current != null && current.chainId != latest.chainId) {
                    inbox.commitTerminalAwait(
                        latest.chainId, IosAlarmRecoveryAttemptOutcome.SKIPPED_UNSUPPORTED_OVERLAP,
                    )
                } else {
                    if (current != null && current.occurrenceId != latest.occurrenceId) {
                        if (current.isAwaitingDeadlineLocation(Clock.System.now().toEpochMilliseconds())) {
                            recovered = current
                            return@forEach
                        }
                        val evidence = current.selectBestDeadlineLocation(listOfNotNull(
                            missionStore.loadDeadlineLocation(), missionStore.loadLastLocation(),
                        ))
                        if (evidence != null && current.hasReachedDestinationByDeadline(
                                evidence.latitude, evidence.longitude, evidence.accuracyMeters,
                                evidence.capturedAtEpochMillis,
                            )) {
                            completeTerminalLocked(
                                current, IosPendingMissionOutcome.SUCCESS,
                                recoverySnapshot = recovery.takeIf { journalOnly },
                            )
                            return@forEach
                        }
                        missionStore.clearLastLocation()
                        missionStore.clearDeadlineLocation()
                    }
                    val mission = latest.toActiveAlarmMission()
                    if (current != mission) replaceActiveMissionLocked(mission)
                    saveNativeDeadlineRegistration(latest, mission)
                    runCatching { inbox.ackMissionProjectionAwait(latest.occurrenceId) }
                    recovered = mission
                }
            }
        recovery.terminals.filterNot { it.terminalRecordAck }.forEach { terminal ->
            if (terminal.outcome == IosAlarmRecoveryAttemptOutcome.SKIPPED_UNSUPPORTED_OVERLAP) {
                inbox.ackTerminalRecordAwait(terminal.chainId)
            } else if (terminal.occurrenceId in recordReady) {
                runCatching {
                    when (terminal.outcome) {
                        IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED -> if (!recordSuccessAndNotify(
                            terminal.chainId,
                            terminal.occurrenceId, iosMissionDate(terminal.completedAtEpochMillis),
                            terminal.completedAtEpochMillis,
                        )) return@forEach
                        IosAlarmRecoveryAttemptOutcome.TERMINAL_FORCED_STOP -> outcomeRecorder.recordFailure(
                            terminal.occurrenceId, iosMissionDate(terminal.completedAtEpochMillis),
                            terminal.completedAtEpochMillis,
                        )
                        else -> Unit
                    }
                    inbox.ackTerminalRecordAwait(terminal.chainId)
                }
            }
        }
        return recovered
    }

    private suspend fun refreshNativeReservationsLocked(initial: ActiveAlarmMission?): ActiveAlarmMission? {
        var recovered = initial
        // Apply overlap/terminal suppression before attempting pending native calls.
        val refreshed = runCatching { inbox.recoverReservationsAwait() }.getOrNull()
        val current = activeMission.value
        val refreshedAttempt = refreshed?.attempts?.firstOrNull { it.occurrenceId == current?.occurrenceId }
        if (current != null && refreshedAttempt != null) {
            val updated = current.copy(recoveryReservationState = refreshedAttempt.toActiveAlarmMission().recoveryReservationState)
            if (updated != current) replaceActiveMissionLocked(updated)
            saveNativeDeadlineRegistration(refreshedAttempt, updated)
            if (recovered?.occurrenceId == updated.occurrenceId) recovered = updated
        }
        return recovered
    }

    private fun saveNativeDeadlineRegistration(
        attempt: IosAlarmRecoveryAttemptDto,
        mission: ActiveAlarmMission,
    ) {
        missionStore.saveDeadlineAlarm(
            IosMissionDeadlineAlarm(
                alarmKitId = attempt.nextReservationUuid,
                sourceOccurrenceId = attempt.occurrenceId,
                retryOccurrenceId = attempt.nextOccurrenceToken,
                retryAttempt = mission.retryAttempt + 1,
                isScheduleConfirmed =
                    attempt.currentReservation.status == IosAlarmRecoveryReservationStatus.CONFIRMED,
                missionSeed = mission.toRetryMissionSeed(),
                isNativeRecovery = true,
            ),
        )
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
        // Persist event timestamps before consuming the durable inbox, including duplicate deliveries.
        outcomeRecorder.recordRingingTimes(event)
        // A fallback delivery can start the mission before the native stop timestamp arrives.
        // Fill the already scheduled retry before the consumed-occurrence check returns early.
        missionStore.loadDeadlineAlarm()?.takeIf { registration ->
            event.occurrenceId == registration.sourceOccurrenceId || event.occurrenceId == registration.retryOccurrenceId
        }?.let { registration ->
            val interval = registration.missionSeed?.limitMinutes
                ?: activeMission.value?.takeIf { it.occurrenceId == registration.sourceOccurrenceId }?.limitMinutes
            if (interval != null) {
                outcomeRecorder.recordRetryRingingSchedule(registration.retryOccurrenceId, registration.sourceOccurrenceId, interval)
            }
        }
        if (missionStore.isConsumed(event.occurrenceId)) {
            inbox.markConsumedAwait(event.eventId)
            return null
        }
        analytics?.recordAlarmRingingStarted(
            occurrenceId = event.occurrenceId,
            retryAttempt = event.retryAttempt,
        )
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
                analytics?.recordMissionStarted(
                    occurrenceId = current.occurrenceId,
                    retryAttempt = current.retryAttempt,
                )
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
            val isDeliveredOneTimeOccurrence = savedAlarm?.let { alarm ->
                !alarm.enabled &&
                    !alarm.request.repeatEnabled &&
                    alarm.request.selectedDays.isEmpty()
            } == true
            if (savedAlarm == null || (!savedAlarm.enabled && !isDeliveredOneTimeOccurrence)) {
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                return null
            }
            // A fired one-time AlarmKit alarm disappears before its stop intent is
            // delivered. Reconciliation may therefore disable the future schedule
            // first; the durable user-action event still owns this occurrence.
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
        analytics?.recordMissionStarted(
            occurrenceId = mission.occurrenceId,
            retryAttempt = mission.retryAttempt,
        )
        return mission
    }

    /** Fill only chain IDs already recoverable from the stored mission/deadline state. */
    private fun withKnownChainId(event: IosAlarmMissionEventDto): IosAlarmMissionEventDto {
        if (event.chainId != null) return event
        if (event.retryAttempt == 0) return event.copy(chainId = event.occurrenceId)

        val activeMissionChain = missionStore.loadActiveMission()?.takeIf { mission ->
            mission.alarmId == event.alarmId &&
                mission.scheduleVersion == event.scheduleVersion &&
                mission.occurrenceId == event.occurrenceId
        }?.chainId
        val terminalChain = missionStore.loadPendingTerminal()?.takeIf { terminal ->
            terminal.occurrenceId == event.occurrenceId
        }?.chainId
        val firstRetryChain = missionStore.loadDeadlineAlarm()?.takeIf { registration ->
            registration.retryAttempt == 1 &&
                registration.retryOccurrenceId == event.occurrenceId &&
                registration.missionSeed?.alarmId == event.alarmId &&
                registration.missionSeed.scheduleVersion == event.scheduleVersion
        }?.sourceOccurrenceId
        val knownChainId = activeMissionChain ?: terminalChain ?: firstRetryChain ?: return event
        return event.copy(chainId = knownChainId)
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

    suspend fun requestForceEnd(occurrenceId: String): ForceEndMissionResult = mutex.withLock {
        missionStore.loadPendingTerminal()?.takeIf { it.occurrenceId == occurrenceId }?.let {
            return@withLock if (it.outcome == IosPendingMissionOutcome.FAILURE)
                ForceEndMissionResult.Completed else ForceEndMissionResult.NoLongerActive
        }
        val current = activeMission.value?.takeIf { it.occurrenceId == occurrenceId }
            ?: return@withLock ForceEndMissionResult.NoLongerActive
        var committed = false
        try {
            completeTerminalLocked(current, IosPendingMissionOutcome.FAILURE) { committed = true }
            ForceEndMissionResult.Completed
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (committed) ForceEndMissionResult.Completed else ForceEndMissionResult.Failed
        }
    }

    fun recordForceEndHoldStarted(occurrenceId: String) {
        val mission = activeMission.value?.takeIf { it.occurrenceId == occurrenceId } ?: return
        analytics?.recordForceEndHoldStarted(
            occurrenceId = mission.occurrenceId,
            retryAttempt = mission.retryAttempt,
        )
    }

    fun recordForceEndHoldCancelled(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) {
        if (activeMission.value?.occurrenceId != occurrenceId) return
        analytics?.recordForceEndHoldCancelled(occurrenceId, holdDurationMillis)
    }

    fun recordForceEndHoldCompleted(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) {
        if (activeMission.value?.occurrenceId != occurrenceId) return
        analytics?.recordForceEndHoldCompleted(occurrenceId, holdDurationMillis)
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
        analytics?.recordMissionExpired(
            occurrenceId = current.occurrenceId,
            retryAttempt = current.retryAttempt,
            startedAtEpochMillis = current.startedAtEpochMillis,
        )
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        if (missionStore.loadDeadlineAlarm()?.isNativeRecovery != true) {
            missionStore.clearActiveMission()
            activeMission.value = null
        }
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
            if (registration.isNativeRecovery) return
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
            isNativeRecovery = false,
        )
        missionStore.saveDeadlineAlarm(registration)
        scheduleDeadlineAlarmLocked(mission, registration)
    }

    private suspend fun scheduleDeadlineAlarmLocked(
        mission: ActiveAlarmMission,
        registration: IosMissionDeadlineAlarm,
    ) {
        outcomeRecorder.recordRetryRingingSchedule(registration.retryOccurrenceId, mission.occurrenceId, mission.limitMinutes)
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
                scheduleVersion = seed.scheduleVersion,
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
        recoverySnapshot: IosAlarmRecoveryAttemptsResult? = null,
        onCommitted: () -> Unit = {},
    ) {
        val completedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        inbox.commitTerminalAwait(
            chainId = mission.chainId,
            outcome = when (outcome) {
                IosPendingMissionOutcome.SUCCESS ->
                    IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED
                IosPendingMissionOutcome.FAILURE ->
                    IosAlarmRecoveryAttemptOutcome.TERMINAL_FORCED_STOP
            },
            completedAtEpochMillis = completedAtEpochMillis,
            occurrenceId = mission.occurrenceId,
        )
        missionStore.savePendingTerminal(
            IosPendingMissionTerminal(
                occurrenceId = mission.occurrenceId,
                chainId = mission.chainId,
                outcome = outcome,
                completedAtEpochMillis = completedAtEpochMillis,
                completedAt = iosMissionDate(completedAtEpochMillis),
                arrivalDestinationName = mission.destinationName,
            ),
        )
        when (outcome) {
            IosPendingMissionOutcome.SUCCESS -> analytics?.recordMissionCompleted(
                occurrenceId = mission.occurrenceId,
                retryAttempt = mission.retryAttempt,
                startedAtEpochMillis = mission.startedAtEpochMillis,
            )

            IosPendingMissionOutcome.FAILURE -> analytics?.recordMissionForceEnded(
                occurrenceId = mission.occurrenceId,
                retryAttempt = mission.retryAttempt,
                startedAtEpochMillis = mission.startedAtEpochMillis,
            )
        }
        onCommitted()
        activeMission.value = null
        missionStore.clearActiveMission()
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        recoverPendingTerminalLocked(recoverySnapshot)
    }

    private suspend fun recoverPendingTerminalLocked(recoverySnapshot: IosAlarmRecoveryAttemptsResult? = null) {
        val pending = missionStore.loadPendingTerminal() ?: return
        // Retry the durable tombstone before discarding any local projection.
        inbox.commitTerminalAwait(pending.chainId, when (pending.outcome) {
            IosPendingMissionOutcome.SUCCESS -> IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED
            IosPendingMissionOutcome.FAILURE -> IosAlarmRecoveryAttemptOutcome.TERMINAL_FORCED_STOP
        }, pending.completedAtEpochMillis, pending.occurrenceId,
            createArrivalIntent = pending.arrivalDestinationName != null)
        if (activeMission.value?.occurrenceId == pending.occurrenceId) activeMission.value = null
        if (missionStore.loadActiveMission()?.occurrenceId == pending.occurrenceId) missionStore.clearActiveMission()
        missionStore.clearLastLocation()
        missionStore.clearDeadlineLocation()
        val nativeAttempt = (recoverySnapshot ?: inbox.recoveryAttemptsAwait()).attempts
            .firstOrNull { it.occurrenceId == pending.occurrenceId }
        if (nativeAttempt != null && recoverySnapshot == null) runCatching { inbox.recoverReservationsAwait() }
        val cancellation = runCatching {
            // Direct projection may have accepted the child before final location evidence arrived.
            // Cancel all journal reservations for that terminal chain without running the scheduler.
            recoverySnapshot?.attempts?.filter { it.chainId == pending.chainId }?.forEach {
                scheduler.cancelAwait(it.nextReservationUuid)
            }
            cancelDeadlineAlarmLocked()
        }
        if (nativeAttempt == null) cancellation.getOrThrow()
        val recordReady = nativeAttempt == null || runCatching {
            outcomeRecorder.recordRecoveryAttempt(nativeAttempt)
        }.getOrDefault(false)
        val record = runCatching {
            when (pending.outcome) {
                IosPendingMissionOutcome.SUCCESS -> check(recordSuccessAndNotify(
                    pending.chainId, pending.occurrenceId, pending.completedAt,
                    pending.completedAtEpochMillis, pending,
                )) { "도착 알림 시도 상태를 저장하지 못했습니다." }
                IosPendingMissionOutcome.FAILURE -> outcomeRecorder.recordFailure(
                    pending.occurrenceId, pending.completedAt, pending.completedAtEpochMillis)
            }
        }
        if (record.isSuccess && recordReady) {
            inbox.ackTerminalRecordAwait(pending.chainId)
        }
        if (cancellation.isSuccess && record.isSuccess) missionStore.clearPendingTerminal()
        cancellation.getOrThrow()
        record.getOrThrow()
    }

    private suspend fun recordSuccessAndNotify(
        chainId: String,
        occurrenceId: String,
        completedAt: String,
        completedAtEpochMillis: Long,
        pending: IosPendingMissionTerminal? = null,
    ): Boolean {
        outcomeRecorder.recordSuccess(occurrenceId, completedAt, completedAtEpochMillis)
        val result = arrivalNotifier.notifyAwait(IosMissionArrivalRequest(chainId, occurrenceId))
        if (result == IosMissionArrivalResult.CLAIM_STORAGE_FAILED) return false
        if (result != IosMissionArrivalResult.MISSING_CHAIN) return true
        // Only new legacy successes carry a destination. Old pending records cannot opt in.
        if (pending?.arrivalDestinationName == null || pending.arrivalFallbackAttempted) return true
        missionStore.savePendingTerminal(pending.copy(arrivalFallbackAttempted = true))
        return arrivalNotifier.notifyAwait(
            IosMissionArrivalRequest(chainId, occurrenceId, pending.arrivalDestinationName),
        ) != IosMissionArrivalResult.CLAIM_STORAGE_FAILED
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
    val isNativeRecovery: Boolean = false,
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
    val scheduleVersion: Long = 1,
)

enum class IosPendingMissionOutcome {
    SUCCESS,
    FAILURE,
}

data class IosPendingMissionTerminal(
    val occurrenceId: String,
    val chainId: String = occurrenceId,
    val outcome: IosPendingMissionOutcome,
    val completedAtEpochMillis: Long,
    val completedAt: String,
    val arrivalDestinationName: String? = null,
    val arrivalFallbackAttempted: Boolean = false,
)

interface IosMissionOutcomeRecorder {
    suspend fun recordRingingTimes(event: IosAlarmMissionEventDto): Boolean = true

    suspend fun resolvePendingRecordDecisions() = Unit

    suspend fun recordRecoveryAttempt(attempt: IosAlarmRecoveryAttemptDto): Boolean {
        recordRingingTimes(attempt.toMissionEvent())
        return attempt.snapshot.owner.kind != IosAlarmRecoveryOwnerKind.UNCAPTURED
    }

    suspend fun recordRetryRingingSchedule(occurrenceId: String, sourceOccurrenceId: String, intervalMinutes: Int) = Unit

    suspend fun recordSuccess(occurrenceId: String, completedAt: String, completedAtEpochMillis: Long? = null)
    suspend fun recordFailure(occurrenceId: String, completedAt: String, completedAtEpochMillis: Long? = null)
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
            chainId = record.getOrNull(14)?.takeIf(String::isNotBlank) ?: occurrenceId,
            retryAttempt = record.getOrNull(12)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            scheduleVersion = record.getOrNull(13)?.toLongOrNull() ?: 1,
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
                mission.scheduleVersion.toString(),
                mission.chainId,
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
            record.size != DeadlineAlarmRecordSizeV3 &&
            record.size != DeadlineAlarmRecordSizeV4 &&
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
                scheduleVersion = record.getOrNull(15)?.toLongOrNull() ?: 1,
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
                isNativeRecovery =
                    record.getOrNull(16)?.toBooleanStrictOrNull() ?: false,
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
                registration.missionSeed?.scheduleVersion?.toString().orEmpty(),
                registration.isNativeRecovery.toString(),
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
        val completedAtEpochMillis = record.getOrNull(2)?.toLongOrNull()
            ?: Clock.System.now().toEpochMilliseconds()
        return IosPendingMissionTerminal(
            occurrenceId = record[0].takeIf(String::isNotBlank) ?: return null,
            chainId = record.getOrNull(4)?.takeIf(String::isNotBlank)
                ?: record[0].takeIf(String::isNotBlank) ?: return null,
            outcome = runCatching { IosPendingMissionOutcome.valueOf(record[1]) }.getOrNull()
                ?: return null,
            completedAtEpochMillis = completedAtEpochMillis,
            completedAt = record.getOrNull(3)?.takeIf(String::isNotBlank)
                ?: iosMissionDate(completedAtEpochMillis),
            arrivalDestinationName = record.getOrNull(5)?.takeIf(String::isNotBlank),
            arrivalFallbackAttempted = record.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
        )
    }

    override fun savePendingTerminal(pending: IosPendingMissionTerminal) {
        val previous = defaults.stringArrayForKey(KeyPendingTerminal)
        defaults.setObject(
            listOf(
                pending.occurrenceId,
                pending.outcome.name,
                pending.completedAtEpochMillis.toString(),
                pending.completedAt.orEmpty(),
                pending.chainId,
                pending.arrivalDestinationName.orEmpty(),
                pending.arrivalFallbackAttempted.toString(),
            ),
            forKey = KeyPendingTerminal,
        )
        // The legacy fallback must reach persistent storage before any OS notification request.
        if (pending.arrivalFallbackAttempted && !defaults.synchronize()) {
            if (previous == null) defaults.removeObjectForKey(KeyPendingTerminal)
            else defaults.setObject(previous, forKey = KeyPendingTerminal)
            error("도착 알림 시도 상태를 저장하지 못했습니다.")
        }
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
        const val ActiveMissionRecordSize = 15
        const val LastLocationRecordSize = 4
        const val LegacyDeadlineAlarmRecordSize = 4
        const val DeadlineAlarmRecordSizeV2 = 14
        const val DeadlineAlarmRecordSizeV3 = 15
        const val DeadlineAlarmRecordSizeV4 = 16
        const val DeadlineAlarmRecordSize = 17
        const val LegacyPendingTerminalRecordSize = 2
        const val PendingTerminalRecordSize = 7
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
        chainId = event.chainId ?: event.occurrenceId,
        retryAttempt = event.retryAttempt,
        alarmTime = time,
        scheduleVersion = event.scheduleVersion,
        startedAtEpochMillis = event.occurredAtEpochMillis,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = DefaultArrivalRadiusMeters,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = alarmSoundUri != null,
    )

private fun ActiveAlarmMission.toRetryMissionSeed(): IosRetryMissionSeed =
    IosRetryMissionSeed(
        alarmId = alarmId,
        scheduleVersion = scheduleVersion,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        alarmTime = alarmTime,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = DefaultArrivalRadiusMeters,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = hasAlarmSoundUri,
    )

private fun ActiveAlarmMission.withDefaultArrivalRadius(): ActiveAlarmMission =
    copy(arrivalRadiusMeters = DefaultArrivalRadiusMeters)

private fun ActiveAlarmMission.isExpiredAtNow(): Boolean =
    isExpiredAt(Clock.System.now().toEpochMilliseconds())

private fun IosRetryMissionSeed.toActiveAlarmMission(
    event: IosAlarmMissionEventDto,
): ActiveAlarmMission =
    ActiveAlarmMission(
        alarmId = alarmId,
        scheduleVersion = scheduleVersion,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        expiresAtEpochMillis = event.occurredAtEpochMillis + limitMinutes * MillisPerMinute,
        occurrenceId = event.occurrenceId,
        chainId = event.chainId ?: event.occurrenceId,
        retryAttempt = event.retryAttempt,
        alarmTime = alarmTime,
        startedAtEpochMillis = event.occurredAtEpochMillis,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = DefaultArrivalRadiusMeters,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = hasAlarmSoundUri,
    )

internal fun IosAlarmRecoveryAttemptDto.toMissionEvent(): IosAlarmMissionEventDto =
    IosAlarmMissionEventDto(
        eventId = occurrenceId,
        alarmId = snapshot.sourceAlarmId,
        occurrenceId = occurrenceId,
        action = IosAlarmMissionAction.STOP,
        occurredAtEpochMillis = stopAtEpochMillis,
        retryAttempt = sequence,
        ringingObservedAtEpochMillis = null,
        ringingStoppedAtEpochMillis = stopAtEpochMillis,
        scheduleVersion = snapshot.scheduleVersion,
        ownerAccountId = snapshot.owner.accountId,
        ownerCaptured = snapshot.owner.kind != IosAlarmRecoveryOwnerKind.UNCAPTURED,
        chainId = chainId,
    )

private fun IosAlarmRecoveryAttemptDto.toActiveAlarmMission(): ActiveAlarmMission {
    val limitMinutes = (snapshot.limitSeconds / 60L).toInt().coerceAtLeast(1)
    return ActiveAlarmMission(
        alarmId = snapshot.sourceAlarmId,
        destinationName = snapshot.destination.name,
        limitMinutes = limitMinutes,
        expiresAtEpochMillis = expiresAtEpochMillis,
        occurrenceId = occurrenceId,
        chainId = chainId,
        retryAttempt = sequence,
        alarmTime = snapshot.originalTime.orEmpty(),
        startedAtEpochMillis = stopAtEpochMillis,
        destinationLatitude = snapshot.destination.latitude,
        destinationLongitude = snapshot.destination.longitude,
        arrivalRadiusMeters = snapshot.arrivalRadiusMeters
            ?.takeIf { it.isFinite() && it > 0 }
            ?: DefaultArrivalRadiusMeters,
        alarmSoundUri = snapshot.soundUri,
        hasAlarmSoundUri = snapshot.soundUri != null,
        scheduleVersion = snapshot.scheduleVersion,
        recoveryReservationState = when (currentReservation.status) {
            IosAlarmRecoveryReservationStatus.CONFIRMED -> MissionRecoveryReservationState.CONFIRMED
            IosAlarmRecoveryReservationStatus.PENDING -> MissionRecoveryReservationState.PENDING
            IosAlarmRecoveryReservationStatus.FAILED -> MissionRecoveryReservationState.FAILED
            else -> MissionRecoveryReservationState.UNKNOWN
        },
    )
}

internal suspend fun IosAlarmMissionEventInbox.pendingEventsAwait():
    List<IosAlarmMissionEventDto> {
    val result = awaitSingleCallback<IosAlarmMissionEventsResult> { callback ->
        pendingEvents(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("알람 미션 이벤트를 읽지 못했습니다.")
    return result.events
}

internal suspend fun IosAlarmMissionEventInbox.recordProjectionEventsAwait():
    List<IosAlarmMissionEventDto> {
    val result = awaitSingleCallback<IosAlarmMissionEventsResult> { callback ->
        recordProjectionEvents(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("알람 기록 투영 이벤트를 읽지 못했습니다.")
    return result.events
}

internal suspend fun IosAlarmMissionEventInbox.recordStopEventAwait(
    alarmId: String,
    occurrenceId: String?,
    retryAttempt: Int,
    scheduleVersion: Long = 1,
    chainId: String? = null,
) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        recordStopEvent(
            alarmId = alarmId,
            occurrenceId = occurrenceId,
            retryAttempt = retryAttempt,
            scheduleVersion = scheduleVersion,
            chainId = chainId,
            callback = callback,
        )
    }
    result.requireAlarmKitSuccess("알람 중지 이벤트를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.markConsumedAwait(eventId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        markConsumed(eventId, callback)
    }
    result.requireAlarmKitSuccess("알람 미션 이벤트를 소비 처리하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.ackRecordEventProjectionAwait(eventId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        ackRecordEventProjection(eventId, callback)
    }
    result.requireAlarmKitSuccess("알람 이벤트 기록 투영 ACK를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.upsertSourceSnapshotAwait(
    snapshot: IosAlarmRecoverySourceSnapshotDto,
) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        upsertSourceSnapshot(snapshot, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 스냅샷을 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.setSourceEligibilityAwait(
    alarmId: String,
    scheduleVersion: Long,
    eligibility: IosAlarmRecoveryEligibility,
) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        setSourceEligibility(alarmId, scheduleVersion, eligibility, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 eligibility를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.recoveryAttemptsAwait():
    IosAlarmRecoveryAttemptsResult {
    val result = awaitSingleCallback<IosAlarmRecoveryAttemptsResult> { callback ->
        recoveryState(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("알람 복구 예약을 읽지 못했습니다.")
    return result
}

internal suspend fun IosAlarmMissionEventInbox.ackMissionProjectionAwait(occurrenceId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        ackMissionProjection(occurrenceId, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 미션 반영 ACK를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.ackRecordProjectionAwait(occurrenceId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        ackRecordProjection(occurrenceId, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 기록 반영 ACK를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.commitTerminalAwait(
    chainId: String,
    outcome: IosAlarmRecoveryAttemptOutcome,
    completedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    occurrenceId: String? = null,
    createArrivalIntent: Boolean = true,
) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        commitTerminal(chainId, outcome, completedAtEpochMillis, occurrenceId, createArrivalIntent, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 종료 상태를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.ackTerminalRecordAwait(chainId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        ackTerminalRecord(chainId, callback)
    }
    result.requireAlarmKitSuccess("알람 복구 종료 기록 ACK를 저장하지 못했습니다.")
}

internal suspend fun IosAlarmMissionEventInbox.protectedReservationUuidsAwait(): Set<String> {
    val result = awaitSingleCallback<IosAlarmProtectedReservationsResult> { callback ->
        protectedReservationUuids(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("보호 예약 목록을 읽지 못했습니다.")
    return result.reservationUuids
}

internal suspend fun IosAlarmMissionEventInbox.nativeCleanupCandidatesAwait(): IosAlarmNativeCleanupCandidatesResult {
    val result = awaitSingleCallback<IosAlarmNativeCleanupCandidatesResult> { callback ->
        nativeCleanupCandidates(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("네이티브 정리 후보를 읽지 못했습니다.")
    return result
}

internal suspend fun IosAlarmMissionEventInbox.commitEventCleanupAwait(
    eventId: String,
    fingerprint: String,
): IosAlarmNativeCleanupCommitResult {
    val result = awaitSingleCallback<IosAlarmNativeCleanupCommitResult> { callback ->
        commitEventCleanup(eventId, fingerprint, callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("네이티브 이벤트 정리를 저장하지 못했습니다.")
    return result
}

internal suspend fun IosAlarmMissionEventInbox.compactTerminalChainAwait(
    chainId: String,
    fingerprint: String,
): IosAlarmNativeCleanupCommitResult {
    val result = awaitSingleCallback<IosAlarmNativeCleanupCommitResult> { callback ->
        compactTerminalChain(chainId, fingerprint, callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("네이티브 종료 체인을 정리하지 못했습니다.")
    return result
}

private const val MillisPerMinute = 60_000L
private const val MillisPerSecond = 1_000.0

internal suspend fun IosAlarmMissionEventInbox.recoverReservationsAwait(): IosAlarmRecoveryAttemptsResult {
    val result = awaitSingleCallback<IosAlarmRecoveryAttemptsResult> { callback -> recoverReservations(callback) }
    IosAlarmOperationResult(result.code, result.message).requireAlarmKitSuccess("알람 복구를 실행하지 못했습니다.")
    return result
}
