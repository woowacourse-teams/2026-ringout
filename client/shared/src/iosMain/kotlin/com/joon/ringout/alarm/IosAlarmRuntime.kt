package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.platform.IosNativeServices
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUUID

class IosAlarmRuntime(
    private val missionCoordinator: IosAlarmMissionCoordinator,
    private val reconciler: IosAlarmReconciler,
    private val locationService: IosMissionLocationService,
) {
    private val startMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val currentLocation = MutableStateFlow(missionCoordinator.loadLastLocation())
    private val locationState = MutableStateFlow(locationService.currentState())
    private var trackedOccurrenceId: String? = null
    private var lastAcceptedFix: TrackingLocationFix? = null
    private var deadlineJob: Job? = null
    private var deadlineRecoveryJob: Job? = null
    private var terminalRecoveryJob: Job? = null
    private var retryDeliveryRecoveryJob: Job? = null
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

    suspend fun start() = startMutex.withLock {
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
        runStartupStep { missionCoordinator.processPendingEvents() }
        if (missionCoordinator.hasPendingTerminal()) scheduleTerminalRecovery()
        if (
            activeMissionFlow.value == null &&
            !missionCoordinator.hasPendingDeadlineAlarm()
        ) {
            runStartupStep { reconciler.reconcile() }
        }
        syncTracking()
        ensureDeadlineAlarmOrScheduleRecovery()
        scheduleRetryDeliveryRecoveryIfNeeded()
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
            if (locationState.value.canTrackInBackground) {
                locationService.startTracking(mission.occurrenceId)
                locationState.value = locationService.currentState()
            }
            scheduleDeadline(mission)
            return
        }
        if (
            locationState.value.canTrackInBackground &&
            !locationState.value.isTracking
        ) {
            locationService.startTracking(mission.occurrenceId)
            locationState.value = locationService.currentState()
        }
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
    return IosAlarmRuntime(
        missionCoordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = nativeServices.alarmMissionEventInbox(),
            scheduler = nativeServices.alarmScheduler(),
            outcomeRecorder = RoomIosMissionOutcomeRecorder(),
        ),
        reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = nativeServices.alarmScheduler(),
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
