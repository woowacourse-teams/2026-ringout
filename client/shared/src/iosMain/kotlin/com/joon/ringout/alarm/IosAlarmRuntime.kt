package com.joon.ringout.alarm

import com.joon.ringout.analytics.IosAlarmAnalytics
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.platform.IosNativeServices
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUUID

class IosAlarmRuntime(
    private val dataSource: AlarmDataSource,
    private val scheduler: IosAlarmScheduler,
    private val eventInbox: IosAlarmMissionEventInbox,
    private val missionCoordinator: IosAlarmMissionCoordinator,
    private val reconciler: IosAlarmReconciler,
    private val locationService: IosMissionLocationService,
    private val ringingHandoffGraceMillis: Long = DefaultRingingHandoffGraceMillis,
    private val runtimeDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val startMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + runtimeDispatcher)
    private val currentLocation = MutableStateFlow(missionCoordinator.loadLastLocation())
    private val locationState = MutableStateFlow(locationService.currentState())
    private val ringingAlarm = MutableStateFlow<IosRingingAlarm?>(null)
    private val ringingDismissMutex = Mutex()
    private val ringingTransitionMutex = Mutex()
    private var trackedOccurrenceId: String? = null
    private var lastAcceptedFix: TrackingLocationFix? = null
    private var deadlineJob: Job? = null
    private var deadlineRecoveryJob: Job? = null
    private var terminalRecoveryJob: Job? = null
    private var retryDeliveryRecoveryJob: Job? = null
    private var ringingHandoffJob: Job? = null
    private var ringingHandoffSystemAlarmId: String? = null
    private var isDismissingRingingAlarm = false
    internal val ringingAlarmFlow: StateFlow<IosRingingAlarm?> = ringingAlarm.asStateFlow()
    val activeMissionFlow: StateFlow<ActiveAlarmMission?> =
        missionCoordinator.activeMissionFlow
    val currentLocationFlow: StateFlow<ActiveAlarmMissionLocation?> =
        currentLocation.asStateFlow()
    val locationStateFlow: StateFlow<MissionLocationState> = locationState.asStateFlow()

    private val locationListener = object : IosMissionLocationListener {
        override fun onStateChanged(state: MissionLocationState) {
            locationState.value = state
            scope.launch { syncTracking() }
        }

        override fun onLocation(
            occurrenceId: String,
            location: ActiveAlarmMissionLocation,
            elapsedRealtimeNanos: Long,
        ) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                handleLocation(occurrenceId, location, elapsedRealtimeNanos)
            }
        }

        override fun onError(message: String) {
            locationState.value = locationService.currentState()
        }
    }

    private val alarmStateListener = object : IosAlarmStateListener {
        override fun onAlarmsChanged(alarms: List<IosScheduledAlarmDto>) {
            scope.launch { handleAlarmSnapshot(alarms) }
        }

        override fun onError(message: String) = Unit
    }

    private val missionEventListener = object : IosAlarmMissionEventListener {
        override fun onEventRecorded() {
            scope.launch { handleMissionEventRecorded() }
        }
    }

    suspend fun start() = startMutex.withLock {
        scheduler.setStateListener(alarmStateListener)
        eventInbox.setEventListener(missionEventListener)
        locationService.setListener(locationListener)
        locationState.value = locationService.currentState()
        runStartupStep { missionCoordinator.recoverPendingTerminal() }
        syncTracking()
        try {
            resolveExpiredMissionAfterDeadlineEvidenceWindow()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (missionCoordinator.hasPendingTerminal()) scheduleTerminalRecovery()
        }
        var startedMission: ActiveAlarmMission? = null
        runStartupStep {
            startedMission = missionCoordinator.processPendingEvents()
        }
        startedMission?.let(::clearRingingIfMissionMatches)
        if (missionCoordinator.hasPendingTerminal()) scheduleTerminalRecovery()
        if (
            activeMissionFlow.value == null &&
            !missionCoordinator.hasPendingDeadlineAlarm() &&
            ringingAlarm.value == null
        ) {
            runStartupStep { reconciler.reconcile() }
        }
        syncTracking()
        ensureDeadlineAlarmOrScheduleRecovery()
        scheduleRetryDeliveryRecoveryIfNeeded()
        runStartupStep { refreshRingingAlarmSnapshot() }
    }

    internal suspend fun dismissRingingAlarm(expectedSystemAlarmId: String): Boolean =
        ringingDismissMutex.withLock {
            val ringing = ringingAlarm.value ?: return@withLock true
            if (ringing.systemAlarmId != expectedSystemAlarmId) return@withLock false
            val activeMission = activeMissionFlow.value
            if (
                ringing.occurrenceId != null &&
                activeMission?.occurrenceId == ringing.occurrenceId
            ) {
                ringingAlarm.value = null
                return@withLock true
            }

            isDismissingRingingAlarm = true
            try {
                scheduler.stopAwait(ringing.systemAlarmId)
                eventInbox.recordOpenEventAwait(
                    alarmId = ringing.alarmId,
                    occurrenceId = ringing.occurrenceId,
                    retryAttempt = ringing.retryAttempt,
                )
                missionCoordinator.processPendingEvents()
                if (ringingAlarm.value?.systemAlarmId == expectedSystemAlarmId) {
                    cancelRingingHandoff()
                    ringingAlarm.value = null
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                false
            } finally {
                isDismissingRingingAlarm = false
                syncTracking()
                scheduleRetryDeliveryRecoveryIfNeeded()
            }
        }

    suspend fun forceEndActiveMission(occurrenceId: String? = null) {
        try {
            missionCoordinator.clearActiveMission(occurrenceId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            scheduleTerminalRecovery()
        } finally {
            syncTracking()
            scheduleRetryDeliveryRecoveryIfNeeded()
        }
    }

    suspend fun handleDeadline(occurrenceId: String? = null) {
        val current = activeMissionFlow.value ?: return
        if (occurrenceId != null && current.occurrenceId != occurrenceId) return
        var shouldRecoverActiveDeadlineAlarm = false
        try {
            val remainingGraceMillis =
                (current.expiresAtEpochMillis + DeadlineLocationAcquisitionTimeoutMillis -
                    Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
            delay(remainingGraceMillis)
            missionCoordinator.handleDeadline(current.occurrenceId)
            missionCoordinator.processPendingEvents()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (missionCoordinator.hasPendingTerminal()) {
                scheduleTerminalRecovery()
            } else {
                val active = activeMissionFlow.value
                if (active != null && active.occurrenceId != current.occurrenceId) {
                    shouldRecoverActiveDeadlineAlarm = true
                } else {
                    scheduleDeadlineRecovery(current)
                }
            }
        } finally {
            syncTracking()
            if (shouldRecoverActiveDeadlineAlarm) scheduleDeadlineAlarmRecovery()
            scheduleRetryDeliveryRecoveryIfNeeded()
        }
    }

    fun requestWhenInUseAuthorization() {
        locationService.requestWhenInUseAuthorization()
    }

    fun requestAlwaysAuthorization() {
        locationService.requestAlwaysAuthorization()
    }

    fun confirmAlwaysAuthorizationResult() {
        locationService.confirmAlwaysAuthorizationResult()
    }

    fun requestTemporaryFullAccuracyAuthorization() {
        locationService.requestTemporaryFullAccuracyAuthorization(
            ActiveMissionFullAccuracyPurposeKey,
        )
    }

    fun recordForceEndHoldStarted(occurrenceId: String) {
        missionCoordinator.recordForceEndHoldStarted(occurrenceId)
    }

    fun recordForceEndHoldCancelled(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) {
        missionCoordinator.recordForceEndHoldCancelled(occurrenceId, holdDurationMillis)
    }

    fun recordForceEndHoldCompleted(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) {
        missionCoordinator.recordForceEndHoldCompleted(occurrenceId, holdDurationMillis)
    }

    private suspend fun handleLocation(
        occurrenceId: String,
        location: ActiveAlarmMissionLocation,
        elapsedRealtimeNanos: Long,
    ) {
        val mission = activeMissionFlow.value
            ?.takeIf { it.occurrenceId == occurrenceId }
            ?: return
        val candidate = TrackingLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracyMeters,
            capturedAtEpochMillis = location.capturedAtEpochMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
        val handling = evaluateTrackingLocationHandling(
            previous = lastAcceptedFix,
            candidate = candidate,
            nowElapsedRealtimeNanos = currentElapsedRealtimeNanos(),
            mission = mission,
        )
        if (!handling.shouldForwardToCoordinator) return
        if (handling.shouldReplaceLastAcceptedFix) {
            lastAcceptedFix = candidate
            currentLocation.value = location
        }
        try {
            missionCoordinator.onLocationUpdated(
                occurrenceId = occurrenceId,
                location = location,
                shouldPersist = handling.shouldReplaceLastAcceptedFix,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (missionCoordinator.hasPendingTerminal()) {
                scheduleTerminalRecovery()
            } else {
                throw error
            }
        } finally {
            if (activeMissionFlow.value == null) syncTracking()
        }
    }

    private fun syncTracking() {
        val mission = activeMissionFlow.value
        if (mission == null) {
            stopTracking()
            return
        }
        retryDeliveryRecoveryJob?.cancel()
        retryDeliveryRecoveryJob = null
        if (trackedOccurrenceId != mission.occurrenceId) {
            stopTracking()
            currentLocation.value = missionCoordinator.loadLastLocation()
            trackedOccurrenceId = mission.occurrenceId
            if (locationState.value.canStartContinuousTracking) {
                locationService.startTracking(mission.occurrenceId)
                locationState.value = locationService.currentState()
            }
            scheduleDeadline(mission)
            return
        }
        if (!locationState.value.canStartContinuousTracking) {
            if (locationState.value.isTracking) {
                locationService.stopTracking()
                locationState.value = locationService.currentState()
            }
            return
        }
        if (!locationState.value.isTracking) {
            locationService.startTracking(mission.occurrenceId)
            locationState.value = locationService.currentState()
        }
    }

    private suspend fun refreshRingingAlarmSnapshot() {
        handleAlarmSnapshot(scheduler.scheduledAlarmsAwait())
    }

    private suspend fun handleAlarmSnapshot(alarms: List<IosScheduledAlarmDto>) =
        ringingTransitionMutex.withLock {
            val currentSystemAlarmId = ringingAlarm.value?.systemAlarmId
            val alertingAlarmIds = alarms
                .filter { alarm -> alarm.state == IosScheduledAlarmState.ALERTING }
                .map(IosScheduledAlarmDto::alarmId)
                .distinct()
                .sorted()
                .let { ids ->
                    currentSystemAlarmId
                        ?.takeIf { current -> current in ids }
                        ?.let { current -> listOf(current) + ids.filterNot { it == current } }
                        ?: ids
                }
            for (systemAlarmId in alertingAlarmIds) {
                val resolved = resolveIosRingingAlarm(
                    systemAlarmId = systemAlarmId,
                    dataSource = dataSource,
                    deadlineAlarm = missionCoordinator.deadlineAlarmForSystemId(systemAlarmId),
                )
                if (resolved != null) {
                    cancelRingingHandoff()
                    if (activeMissionFlow.value?.matches(resolved) == true) {
                        if (ringingAlarm.value?.systemAlarmId == resolved.systemAlarmId) {
                            ringingAlarm.value = null
                        }
                        return@withLock
                    }
                    if (ringingAlarm.value?.systemAlarmId != resolved.systemAlarmId) {
                        ringingAlarm.value = resolved
                    }
                    return@withLock
                }
            }

            val startedMission = processPendingMissionEventsOrNull()
            if (startedMission != null) {
                clearRingingIfMissionMatches(startedMission)
                syncTracking()
            }

            val currentRinging = ringingAlarm.value
            if (
                currentRinging != null &&
                activeMissionFlow.value?.matches(currentRinging) == true
            ) {
                cancelRingingHandoff()
                ringingAlarm.value = null
                return@withLock
            }
            if (!isDismissingRingingAlarm && currentRinging != null) {
                scheduleRingingHandoff(currentRinging.systemAlarmId)
            }
        }

    private suspend fun handleMissionEventRecorded() = ringingTransitionMutex.withLock {
        val startedMission = processPendingMissionEventsOrNull()
        startedMission?.let(::clearRingingIfMissionMatches)
        activeMissionFlow.value?.let(::clearRingingIfMissionMatches)
        syncTracking()
        scheduleRetryDeliveryRecoveryIfNeeded()
    }

    private suspend fun processPendingMissionEventsOrNull(): ActiveAlarmMission? = try {
        missionCoordinator.processPendingEvents()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun scheduleRingingHandoff(
        expectedSystemAlarmId: String,
        remainingRetries: Int = MaxRingingHandoffRetries,
    ) {
        if (
            ringingHandoffSystemAlarmId == expectedSystemAlarmId &&
            ringingHandoffJob?.isActive == true
        ) {
            return
        }
        cancelRingingHandoff()
        ringingHandoffSystemAlarmId = expectedSystemAlarmId
        ringingHandoffJob = scope.launch {
            delay(ringingHandoffGraceMillis)
            var shouldRetry = false
            ringingTransitionMutex.withLock {
                if (ringingAlarm.value?.systemAlarmId != expectedSystemAlarmId) {
                    return@withLock
                }
                val stoppedRinging = ringingAlarm.value ?: return@withLock
                val latestAlarms = scheduledAlarmsOrNull()
                when {
                    latestAlarms == null -> shouldRetry = true
                    latestAlarms.any { alarm ->
                        alarm.alarmId == expectedSystemAlarmId &&
                            alarm.state == IosScheduledAlarmState.ALERTING
                    } -> Unit
                    else -> {
                        var startedMission = processPendingMissionEventsOrNull()
                        if (startedMission == null && activeMissionFlow.value == null) {
                            startedMission = recordStoppedRingingAndStartMission(stoppedRinging)
                        }
                        if (startedMission != null || activeMissionFlow.value != null) {
                            startedMission?.let(::clearRingingIfMissionMatches)
                            if (ringingAlarm.value?.systemAlarmId == expectedSystemAlarmId) {
                                ringingAlarm.value = null
                            }
                            syncTracking()
                        } else {
                            shouldRetry = true
                        }
                    }
                }
                ringingHandoffSystemAlarmId = null
                ringingHandoffJob = null
            }
            if (
                shouldRetry &&
                remainingRetries > 0 &&
                !isDismissingRingingAlarm &&
                ringingAlarm.value?.systemAlarmId == expectedSystemAlarmId
            ) {
                scheduleRingingHandoff(expectedSystemAlarmId, remainingRetries - 1)
            }
        }
    }

    private suspend fun scheduledAlarmsOrNull(): List<IosScheduledAlarmDto>? = try {
        scheduler.scheduledAlarmsAwait()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private suspend fun recordStoppedRingingAndStartMission(
        ringing: IosRingingAlarm,
    ): ActiveAlarmMission? = try {
        eventInbox.recordStopEventAwait(
            alarmId = ringing.alarmId,
            occurrenceId = ringing.occurrenceId,
            retryAttempt = ringing.retryAttempt,
        )
        missionCoordinator.processPendingEvents()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun clearRingingIfMissionMatches(mission: ActiveAlarmMission) {
        val current = ringingAlarm.value ?: return
        if (!mission.matches(current)) return
        cancelRingingHandoff()
        ringingAlarm.value = null
    }

    private fun ActiveAlarmMission.matches(ringing: IosRingingAlarm): Boolean =
        if (ringing.occurrenceId != null) {
            occurrenceId == ringing.occurrenceId
        } else {
            alarmId == ringing.alarmId
        }

    private fun cancelRingingHandoff() {
        ringingHandoffJob?.cancel()
        ringingHandoffJob = null
        ringingHandoffSystemAlarmId = null
    }

    private fun scheduleDeadline(mission: ActiveAlarmMission) {
        deadlineJob?.cancel()
        deadlineRecoveryJob?.cancel()
        deadlineRecoveryJob = null
        deadlineJob = scope.launch {
            val delayMillis =
                (mission.expiresAtEpochMillis - Clock.System.now().toEpochMilliseconds())
                    .coerceAtLeast(0L)
            delay(delayMillis)
            handleDeadline(mission.occurrenceId)
        }
    }

    private fun scheduleDeadlineRecovery(mission: ActiveAlarmMission) {
        deadlineRecoveryJob?.cancel()
        deadlineRecoveryJob = scope.launch {
            delay(DeadlineRecoveryDelayMillis)
            handleDeadline(mission.occurrenceId)
        }
    }

    private fun scheduleDeadlineAlarmRecovery() {
        deadlineRecoveryJob?.cancel()
        val recoveryId = NSUUID().UUIDString
        locationService.beginBackgroundRecovery(recoveryId)
        deadlineRecoveryJob = scope.launch {
            var shouldRetry = false
            var ownsRecoverySlot = false
            try {
                delay(DeadlineRecoveryDelayMillis)
                missionCoordinator.ensureDeadlineAlarm()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                shouldRetry = true
            } finally {
                locationService.endBackgroundRecovery(recoveryId)
                ownsRecoverySlot = deadlineRecoveryJob === coroutineContext[Job]
                if (ownsRecoverySlot) deadlineRecoveryJob = null
            }
            if (shouldRetry && ownsRecoverySlot && activeMissionFlow.value != null) {
                scheduleDeadlineAlarmRecovery()
            }
        }
    }

    private fun scheduleTerminalRecovery() {
        if (!missionCoordinator.hasPendingTerminal()) return
        terminalRecoveryJob?.cancel()
        val recoveryId = NSUUID().UUIDString
        locationService.beginBackgroundRecovery(recoveryId)
        terminalRecoveryJob = scope.launch {
            var shouldRetry = false
            var ownsRecoverySlot = false
            try {
                delay(DeadlineRecoveryDelayMillis)
                missionCoordinator.recoverPendingTerminal()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                shouldRetry = true
            } finally {
                locationService.endBackgroundRecovery(recoveryId)
                ownsRecoverySlot = terminalRecoveryJob === coroutineContext[Job]
                if (ownsRecoverySlot) terminalRecoveryJob = null
            }
            if (shouldRetry && ownsRecoverySlot) scheduleTerminalRecovery()
        }
    }

    private fun scheduleRetryDeliveryRecoveryIfNeeded() {
        if (
            activeMissionFlow.value != null ||
            !missionCoordinator.hasPendingDeadlineAlarm() ||
            missionCoordinator.hasPendingTerminal()
        ) {
            retryDeliveryRecoveryJob?.cancel()
            retryDeliveryRecoveryJob = null
            return
        }
        retryDeliveryRecoveryJob?.cancel()
        val recoveryId = NSUUID().UUIDString
        locationService.beginBackgroundRecovery(recoveryId)
        retryDeliveryRecoveryJob = scope.launch {
            var shouldRetry = false
            var ownsRecoverySlot = false
            try {
                delay(RetryDeliveryGraceMillis)
                missionCoordinator.processPendingEventsAfterRetryRecovery()
                if (activeMissionFlow.value == null) {
                    missionCoordinator.recoverPendingRetryDelivery()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                shouldRetry = true
            } finally {
                locationService.endBackgroundRecovery(recoveryId)
                ownsRecoverySlot = retryDeliveryRecoveryJob === coroutineContext[Job]
                if (ownsRecoverySlot) retryDeliveryRecoveryJob = null
            }
            if (ownsRecoverySlot) {
                syncTracking()
                if (shouldRetry) {
                    if (activeMissionFlow.value != null) {
                        scheduleDeadlineAlarmRecovery()
                    } else {
                        scheduleRetryDeliveryRecoveryIfNeeded()
                    }
                }
            }
        }
    }

    private fun stopTracking() {
        if (trackedOccurrenceId != null || locationState.value.isTracking) {
            locationService.stopTracking()
        }
        trackedOccurrenceId = null
        lastAcceptedFix = null
        deadlineJob?.cancel()
        deadlineJob = null
        deadlineRecoveryJob?.cancel()
        deadlineRecoveryJob = null
        currentLocation.value = null
    }

    private suspend fun runStartupStep(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A stored mission must keep tracking even if inbox or AlarmKit recovery fails.
        }
    }

    private suspend fun ensureDeadlineAlarmOrScheduleRecovery() {
        try {
            missionCoordinator.ensureDeadlineAlarm()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (activeMissionFlow.value != null) scheduleDeadlineAlarmRecovery()
        }
    }

    private suspend fun resolveExpiredMissionAfterDeadlineEvidenceWindow() {
        if (missionCoordinator.hasPendingTerminal()) return
        val mission = activeMissionFlow.value ?: return
        val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        if (!mission.isExpiredAt(nowEpochMillis)) return
        val remainingMillis =
            (mission.expiresAtEpochMillis + DeadlineLocationAcquisitionTimeoutMillis -
                nowEpochMillis).coerceAtLeast(0L)
        delay(remainingMillis)
        missionCoordinator.handleDeadline(mission.occurrenceId)
    }
}

fun createIosAlarmRuntime(nativeServices: IosNativeServices): IosAlarmRuntime {
    val dataSource = RoomAlarmDataSource(getRingoutDatabase().alarmDao())
    val analytics = IosAlarmAnalytics(nativeServices.analyticsTracker())
    val scheduler = nativeServices.alarmScheduler()
    val eventInbox = nativeServices.alarmMissionEventInbox()
    return IosAlarmRuntime(
        dataSource = dataSource,
        scheduler = scheduler,
        eventInbox = eventInbox,
        missionCoordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = eventInbox,
            scheduler = scheduler,
            outcomeRecorder = RoomIosMissionOutcomeRecorder(),
            analytics = analytics,
        ),
        reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = nativeServices::normalizeAlarmId,
        ),
        locationService = nativeServices.missionLocationService(),
    )
}

private fun currentElapsedRealtimeNanos(): Long =
    (platform.Foundation.NSProcessInfo.processInfo.systemUptime * NanosPerSecond).toLong()

private const val NanosPerSecond = 1_000_000_000.0
private const val DeadlineRecoveryDelayMillis = 5_000L
private const val RetryDeliveryGraceMillis = 5_000L
private const val DefaultRingingHandoffGraceMillis = 1_500L
private const val MaxRingingHandoffRetries = 2
