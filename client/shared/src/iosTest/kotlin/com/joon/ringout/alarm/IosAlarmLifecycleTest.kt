package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosAlarmLifecycleTest {
    @Test
    fun legacyIdSchedulesUuidBeforeAtomicRoomMigration() = runBlocking {
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = "alarm-legacy")))
        val scheduler = LifecycleScheduler()
        val reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = ::canonicalUuid,
            newAlarmId = { CanonicalUuid },
            currentMinuteOfDay = { 0 },
        )

        val report = reconciler.reconcile()

        assertTrue(report.isSuccess)
        assertNull(dataSource.getById("alarm-legacy"))
        assertEquals(CanonicalUuid, dataSource.getAll().single().request.id)
        assertEquals("schedule:$CanonicalUuid", scheduler.events.first())
        assertEquals(listOf("migrate:alarm-legacy:$CanonicalUuid"), dataSource.events)
    }

    @Test
    fun reconciliationCancelsOrphanAndDisablesPastOneTimeWithoutTouchingAlerting() = runBlocking {
        val past = savedAlarm(id = CanonicalUuid, repeatEnabled = false, selectedDays = emptyList())
        val alertingId = "1F3E70A8-A0EC-4F3E-9311-C1859A277257"
        val alerting = savedAlarm(id = alertingId)
        val orphanId = "A4CE02F0-3010-48EF-9B70-AAB7F1061248"
        val alertingOrphanId = "B674D76A-F2CA-4A9A-9964-4551DFF59E49"
        val dataSource = LifecycleAlarmDataSource(listOf(past, alerting))
        val scheduler = LifecycleScheduler(
            alarms = listOf(
                IosScheduledAlarmDto(alertingId, IosScheduledAlarmState.ALERTING),
                IosScheduledAlarmDto(orphanId, IosScheduledAlarmState.SCHEDULED),
                IosScheduledAlarmDto(alertingOrphanId, IosScheduledAlarmState.ALERTING),
            ),
        )

        val report = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = ::canonicalUuid,
            currentMinuteOfDay = { 23 * 60 + 59 },
        ).reconcile()

        assertTrue(report.isSuccess)
        assertFalse(dataSource.getById(CanonicalUuid)!!.enabled)
        assertTrue(dataSource.getById(alertingId)!!.enabled)
        assertEquals(listOf("cancel:$orphanId"), scheduler.events)
    }

    @Test
    fun reconciliationAbortsWithoutMutationWhenAlarmKitSnapshotFails() = runBlocking {
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler(snapshotCode = IosAlarmOperationCode.SDK_ERROR)

        assertFailsWith<IosAlarmOperationException> {
            IosAlarmReconciler(
                dataSource = dataSource,
                scheduler = scheduler,
                normalizeAlarmId = ::canonicalUuid,
            ).reconcile()
        }

        assertTrue(dataSource.getById(CanonicalUuid)!!.enabled)
        assertTrue(scheduler.events.isEmpty())
    }

    @Test
    fun pendingEventPersistsMissionBeforeConsumeAndDuplicateIsIdempotent() = runBlocking {
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val event = IosAlarmMissionEventDto(
            eventId = "event-1",
            alarmId = CanonicalUuid,
            occurrenceId = "$CanonicalUuid:occurrence-1",
            action = IosAlarmMissionAction.STOP,
            occurredAtEpochMillis = 1_000L,
        )
        val inbox = LifecycleInbox(listOf(event, event.copy(eventId = "event-2")))
        val missionStore = LifecycleMissionStore()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = LifecycleScheduler(),
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = missionStore,
        )

        coordinator.processPendingEvents()

        val mission = coordinator.activeMissionFlow.value!!
        assertEquals(CanonicalUuid, mission.alarmId)
        assertEquals(event.occurrenceId, mission.occurrenceId)
        assertEquals(721_000L, mission.expiresAtEpochMillis)
        assertEquals(listOf("save:${event.occurrenceId}", "consume:event-1", "consume:event-2"),
            missionStore.events + inbox.events)
    }

    @Test
    fun inboxReadFailureDoesNotBecomeAnEmptySuccessfulRead() = runBlocking {
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
            inbox = LifecycleInbox(emptyList(), resultCode = IosAlarmOperationCode.SDK_ERROR),
            scheduler = LifecycleScheduler(),
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = LifecycleMissionStore(),
        )

        assertFailsWith<IosAlarmOperationException> { coordinator.processPendingEvents() }
        assertNull(coordinator.activeMissionFlow.value)
    }

    @Test
    fun deadlineRegistrationSurvivesScheduleFailureAndReusesTheSameAlarmKitId() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val scheduler = LifecycleScheduler(retryScheduleFailures = 1)
        val coordinator = coordinator(
            event = event,
            store = store,
            outcomes = LifecycleOutcomeRecorder(),
            scheduler = scheduler,
        )

        assertFailsWith<IosAlarmOperationException> { coordinator.processPendingEvents() }
        val registeredAlarmKitId = store.loadDeadlineAlarm()!!.alarmKitId

        coordinator.ensureDeadlineAlarm()

        assertEquals(
            listOf(registeredAlarmKitId, registeredAlarmKitId),
            scheduler.retryAlarmKitIds,
        )
    }

    @Test
    fun deadlineDoesNotDuplicateAConfirmedRetryThatAlreadyFired() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val scheduler = LifecycleScheduler()
        val coordinator = coordinator(
            event = event,
            store = store,
            outcomes = LifecycleOutcomeRecorder(),
            scheduler = scheduler,
        )
        coordinator.processPendingEvents()
        val registration = store.loadDeadlineAlarm()!!
        scheduler.removeScheduledAlarm(registration.alarmKitId)

        coordinator.handleDeadline(event.occurrenceId)

        assertNull(coordinator.activeMissionFlow.value)
        assertEquals(registration, store.loadDeadlineAlarm())
        assertEquals(listOf(registration.alarmKitId), scheduler.retryAlarmKitIds)
    }

    @Test
    fun missingDeliveredEventAndAlarmAreRecoveredAfterDeadlineGrace() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val scheduler = LifecycleScheduler()
        val coordinator = coordinator(
            event = event,
            store = store,
            outcomes = LifecycleOutcomeRecorder(),
            scheduler = scheduler,
        )
        coordinator.processPendingEvents()
        val registration = store.loadDeadlineAlarm()!!
        scheduler.removeScheduledAlarm(registration.alarmKitId)
        coordinator.handleDeadline(event.occurrenceId)

        assertTrue(coordinator.recoverPendingRetryDelivery())

        assertEquals(
            listOf(registration.alarmKitId, registration.alarmKitId),
            scheduler.retryAlarmKitIds,
        )
    }

    @Test
    fun arrivalRecordsSuccessOnceAndClearsPersistedMissionAndLocation() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = coordinator(event, store, outcomes)
        coordinator.processPendingEvents()

        assertTrue(
            coordinator.onLocationUpdated(
                occurrenceId = event.occurrenceId,
                location = ActiveAlarmMissionLocation(37.5, 127.0, 5f, 2_000L),
                evaluatedAtEpochMillis = 2_000L,
            ),
        )

        assertEquals(listOf(event.occurrenceId), outcomes.successes)
        assertTrue(outcomes.failures.isEmpty())
        assertNull(coordinator.activeMissionFlow.value)
        assertNull(store.loadLastLocation())

        assertFalse(
            coordinator.onLocationUpdated(
                occurrenceId = event.occurrenceId,
                location = ActiveAlarmMissionLocation(37.5, 127.0, 5f, 2_100L),
                evaluatedAtEpochMillis = 2_100L,
            ),
        )
        assertEquals(listOf(event.occurrenceId), outcomes.successes)
    }

    @Test
    fun locationCapturedAfterDeadlineCannotCompleteMission() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = coordinator(event, store, outcomes)
        coordinator.processPendingEvents()

        assertFalse(
            coordinator.onLocationUpdated(
                occurrenceId = event.occurrenceId,
                location = ActiveAlarmMissionLocation(37.5, 127.0, 5f, 730_000L),
                evaluatedAtEpochMillis = 730_000L,
            ),
        )
        coordinator.handleDeadline(event.occurrenceId)

        assertTrue(outcomes.successes.isEmpty())
        assertTrue(outcomes.failures.isEmpty())
        assertNull(coordinator.activeMissionFlow.value)
    }

    @Test
    fun deliveredRetryUsesStoredDeadlineArrivalEvidenceBeforeStartingANewMission() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val inbox = LifecycleInbox(listOf(event))
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
            inbox = inbox,
            scheduler = LifecycleScheduler(),
            outcomeRecorder = outcomes,
            missionStore = store,
        )
        coordinator.processPendingEvents()
        val retry = store.loadDeadlineAlarm()!!
        assertFalse(
            coordinator.onLocationUpdated(
                occurrenceId = event.occurrenceId,
                location = ActiveAlarmMissionLocation(37.5, 127.0, 40f, 720_000L),
                evaluatedAtEpochMillis = 722_000L,
            ),
        )
        inbox.enqueue(
            IosAlarmMissionEventDto(
                eventId = "delivered-retry",
                alarmId = CanonicalUuid,
                occurrenceId = retry.retryOccurrenceId,
                action = IosAlarmMissionAction.STOP,
                occurredAtEpochMillis = 721_000L,
                retryAttempt = retry.retryAttempt,
            ),
        )

        coordinator.processPendingEvents()

        assertEquals(listOf(event.occurrenceId), outcomes.successes)
        assertNull(coordinator.activeMissionFlow.value)
        assertTrue(store.isConsumed(retry.retryOccurrenceId))
    }

    @Test
    fun retryEventWaitsForSourceDeadlineEvidenceWindow() = runBlocking {
        val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        val event = missionEvent().copy(occurredAtEpochMillis = nowEpochMillis - 61_000L)
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val inbox = LifecycleInbox(listOf(event))
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(
                listOf(savedAlarm(id = CanonicalUuid, limitMinutes = 1)),
            ),
            inbox = inbox,
            scheduler = LifecycleScheduler(),
            outcomeRecorder = outcomes,
            missionStore = store,
        )
        coordinator.processPendingEvents()
        val retry = store.loadDeadlineAlarm()!!
        inbox.enqueue(
            IosAlarmMissionEventDto(
                eventId = "retry-inside-deadline-window",
                alarmId = CanonicalUuid,
                occurrenceId = retry.retryOccurrenceId,
                action = IosAlarmMissionAction.STOP,
                occurredAtEpochMillis = nowEpochMillis,
                retryAttempt = retry.retryAttempt,
            ),
        )

        coordinator.processPendingEvents()

        assertEquals(event.occurrenceId, coordinator.activeMissionFlow.value?.occurrenceId)
        assertFalse(store.isConsumed(retry.retryOccurrenceId))

        coordinator.onLocationUpdated(
            occurrenceId = event.occurrenceId,
            location = ActiveAlarmMissionLocation(
                37.5,
                127.0,
                5f,
                nowEpochMillis - 1_000L,
            ),
            evaluatedAtEpochMillis = nowEpochMillis,
        )
        coordinator.handleDeadline(event.occurrenceId)

        assertEquals(listOf(event.occurrenceId), outcomes.successes)
        assertNull(coordinator.activeMissionFlow.value)
    }

    @Test
    fun latestLocationAndBestDeadlineEvidenceArePersistedSeparately() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val coordinator = coordinator(event, store, LifecycleOutcomeRecorder())
        coordinator.processPendingEvents()
        val accurateOlder = ActiveAlarmMissionLocation(37.6, 127.0, 5f, 700_000L)
        val newer = ActiveAlarmMissionLocation(37.7, 127.0, 10f, 720_000L)

        coordinator.onLocationUpdated(event.occurrenceId, accurateOlder, evaluatedAtEpochMillis = 700_000L)
        coordinator.onLocationUpdated(event.occurrenceId, newer, evaluatedAtEpochMillis = 720_000L)

        assertEquals(newer, store.loadLastLocation())
        assertEquals(accurateOlder, store.loadDeadlineLocation())
    }

    @Test
    fun successfulArrivalSuppressesAnAlreadyDeliveredRetryOccurrence() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val coordinator = coordinator(event, store, LifecycleOutcomeRecorder())
        coordinator.processPendingEvents()
        val retryOccurrenceId = store.loadDeadlineAlarm()!!.retryOccurrenceId

        coordinator.onLocationUpdated(
            occurrenceId = event.occurrenceId,
            location = ActiveAlarmMissionLocation(37.5, 127.0, 5f, 2_000L),
            evaluatedAtEpochMillis = 2_000L,
        )

        assertTrue(store.isConsumed(retryOccurrenceId))
    }

    @Test
    fun deadlineSchedulesImmediateRetryWithoutRecordingFailure() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val scheduler = LifecycleScheduler()
        val coordinator = coordinator(event, store, outcomes, scheduler)
        coordinator.processPendingEvents()

        coordinator.handleDeadline(event.occurrenceId)

        assertTrue(scheduler.events.single().startsWith("retry:"))
        assertTrue(outcomes.successes.isEmpty())
        assertTrue(outcomes.failures.isEmpty())
        assertNull(coordinator.activeMissionFlow.value)
    }

    @Test
    fun retryStopStartsSnapshotMissionAfterSourceScheduleWasDeleted() = runBlocking {
        val firstEvent = missionEvent()
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val inbox = LifecycleInbox(listOf(firstEvent))
        val store = LifecycleMissionStore()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = LifecycleScheduler(),
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )
        coordinator.processPendingEvents()
        val retry = store.loadDeadlineAlarm()!!
        coordinator.handleDeadline(firstEvent.occurrenceId)
        dataSource.delete(CanonicalUuid)
        val retriedAtEpochMillis = 800_000L
        inbox.enqueue(
            IosAlarmMissionEventDto(
                eventId = "event-retry-1",
                alarmId = CanonicalUuid,
                occurrenceId = retry.retryOccurrenceId,
                action = IosAlarmMissionAction.STOP,
                occurredAtEpochMillis = retriedAtEpochMillis,
                retryAttempt = retry.retryAttempt,
            ),
        )

        coordinator.processPendingEvents()

        val retriedMission = coordinator.activeMissionFlow.value!!
        assertEquals(retry.retryOccurrenceId, retriedMission.occurrenceId)
        assertEquals(1, retriedMission.retryAttempt)
        assertEquals(12, retriedMission.limitMinutes)
        assertEquals("회사", retriedMission.destinationName)
        assertEquals(37.5, retriedMission.destinationLatitude)
        assertEquals(127.0, retriedMission.destinationLongitude)
        assertEquals(retriedAtEpochMillis + 720_000L, retriedMission.expiresAtEpochMillis)
        assertEquals(2, store.loadDeadlineAlarm()!!.retryAttempt)
    }

    @Test
    fun unrelatedAlarmCannotReplaceAnUnfinishedMissionChain() = runBlocking {
        val firstEvent = missionEvent()
        val secondAlarmId = "2DC28F30-D514-4AC8-90E4-0DD69E2B69B8"
        val dataSource = LifecycleAlarmDataSource(
            listOf(savedAlarm(id = CanonicalUuid), savedAlarm(id = secondAlarmId)),
        )
        val inbox = LifecycleInbox(listOf(firstEvent))
        val store = LifecycleMissionStore()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = LifecycleScheduler(),
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )
        coordinator.processPendingEvents()
        val unrelatedOccurrence = "$secondAlarmId:occurrence"
        inbox.enqueue(
            IosAlarmMissionEventDto(
                eventId = "unrelated-event",
                alarmId = secondAlarmId,
                occurrenceId = unrelatedOccurrence,
                action = IosAlarmMissionAction.STOP,
                occurredAtEpochMillis = 800_000L,
            ),
        )

        coordinator.processPendingEvents()

        assertEquals(firstEvent.occurrenceId, coordinator.activeMissionFlow.value?.occurrenceId)
        assertTrue(store.isConsumed(unrelatedOccurrence))
    }

    @Test
    fun forceEndRecordsFailureAndStopsTheCurrentOccurrence() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = coordinator(event, store, outcomes)
        coordinator.processPendingEvents()
        val retryOccurrenceId = store.loadDeadlineAlarm()!!.retryOccurrenceId

        coordinator.clearActiveMission(event.occurrenceId)

        assertEquals(listOf(event.occurrenceId), outcomes.failures)
        assertTrue(outcomes.successes.isEmpty())
        assertNull(coordinator.activeMissionFlow.value)
        assertTrue(store.isConsumed(retryOccurrenceId))

        coordinator.clearActiveMission(event.occurrenceId)
        assertEquals(listOf(event.occurrenceId), outcomes.failures)
    }

    @Test
    fun restoredPendingTerminalHidesAndClearsTheSamePersistedActiveMission() = runBlocking {
        val occurrenceId = "$CanonicalUuid:terminal-recovery"
        val mission = ActiveAlarmMission(
            alarmId = CanonicalUuid,
            destinationName = "회사",
            limitMinutes = 12,
            expiresAtEpochMillis = 720_000L,
            occurrenceId = occurrenceId,
            startedAtEpochMillis = 0L,
            destinationLatitude = 37.5,
            destinationLongitude = 127.0,
        )
        val store = LifecycleMissionStore().apply {
            saveActiveMission(mission)
            savePendingTerminal(
                IosPendingMissionTerminal(
                    occurrenceId = occurrenceId,
                    outcome = IosPendingMissionOutcome.SUCCESS,
                    completedAtEpochMillis = 500_000L,
                    completedAt = "2026-08-05",
                ),
            )
        }
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
            inbox = LifecycleInbox(emptyList()),
            scheduler = LifecycleScheduler(),
            outcomeRecorder = outcomes,
            missionStore = store,
        )

        assertNull(coordinator.activeMissionFlow.value)

        coordinator.recoverPendingTerminal()

        assertNull(store.loadActiveMission())
        assertNull(store.loadPendingTerminal())
        assertEquals(listOf(occurrenceId), outcomes.successes)
        assertEquals(listOf("2026-08-05"), outcomes.successDates)
    }

    @Test
    fun terminalOutcomeRecoversAfterProcessStopsBetweenStateTransitionAndRoomWrite() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val scheduler = LifecycleScheduler()
        val outcomes = FailOnceLifecycleOutcomeRecorder()
        val firstCoordinator = coordinator(event, store, outcomes, scheduler)
        firstCoordinator.processPendingEvents()

        assertFailsWith<IllegalStateException> {
            firstCoordinator.clearActiveMission(event.occurrenceId)
        }
        assertNull(firstCoordinator.activeMissionFlow.value)
        assertEquals(event.occurrenceId, store.loadPendingTerminal()?.occurrenceId)

        val restoredCoordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
            inbox = LifecycleInbox(emptyList()),
            scheduler = scheduler,
            outcomeRecorder = outcomes,
            missionStore = store,
        )
        restoredCoordinator.processPendingEvents()

        assertEquals(listOf(event.occurrenceId), outcomes.recordedFailures)
        assertNull(store.loadPendingTerminal())
        assertNull(restoredCoordinator.activeMissionFlow.value)
    }

    @Test
    fun terminalRecoveryRetriesInProcessAfterDeadlineCancellationFails() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val scheduler = LifecycleScheduler()
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = coordinator(event, store, outcomes, scheduler)
        coordinator.processPendingEvents()
        scheduler.failNextCancellations()

        assertFailsWith<IosAlarmOperationException> {
            coordinator.clearActiveMission(event.occurrenceId)
        }

        assertNull(coordinator.activeMissionFlow.value)
        assertEquals(event.occurrenceId, store.loadPendingTerminal()?.occurrenceId)
        coordinator.recoverPendingTerminal()
        assertNull(store.loadPendingTerminal())
        assertNull(store.loadDeadlineAlarm())
        assertEquals(listOf(event.occurrenceId), outcomes.failures)
    }

    @Test
    fun restoredMissionRestartsTrackingAndForceEndStopsIt() = runBlocking {
        val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        val occurrenceId = "$CanonicalUuid:restored"
        val store = LifecycleMissionStore().apply {
            saveActiveMission(
                ActiveAlarmMission(
                    alarmId = CanonicalUuid,
                    destinationName = "회사",
                    limitMinutes = 12,
                    expiresAtEpochMillis = nowEpochMillis + 720_000L,
                    occurrenceId = occurrenceId,
                    startedAtEpochMillis = nowEpochMillis,
                    destinationLatitude = 37.5,
                    destinationLongitude = 127.0,
                ),
            )
        }
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler()
        val outcomes = LifecycleOutcomeRecorder()
        val locationService = LifecycleLocationService()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = LifecycleInbox(emptyList()),
            scheduler = scheduler,
            outcomeRecorder = outcomes,
            missionStore = store,
        )
        val runtime = IosAlarmRuntime(
            missionCoordinator = coordinator,
            reconciler = IosAlarmReconciler(
                dataSource = dataSource,
                scheduler = scheduler,
                normalizeAlarmId = { it },
            ),
            locationService = locationService,
        )

        runtime.start()
        runtime.forceEndActiveMission(occurrenceId)

        assertEquals(listOf(occurrenceId), locationService.startedOccurrences)
        assertEquals(1, locationService.stopCount)
        assertEquals(listOf(occurrenceId), outcomes.failures)
        assertNull(runtime.activeMissionFlow.value)
    }

    @Test
    fun restoredMissionTracksWithWhenInUseAndStopsWhenAuthorizationIsLost() = runBlocking {
        val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        val occurrenceId = "$CanonicalUuid:when-in-use"
        val store = LifecycleMissionStore().apply {
            saveActiveMission(
                ActiveAlarmMission(
                    alarmId = CanonicalUuid,
                    destinationName = "회사",
                    limitMinutes = 12,
                    expiresAtEpochMillis = nowEpochMillis + 720_000L,
                    occurrenceId = occurrenceId,
                    startedAtEpochMillis = nowEpochMillis,
                    destinationLatitude = 37.5,
                    destinationLongitude = 127.0,
                ),
            )
        }
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler()
        val locationService = LifecycleLocationService(
            initialAuthorization = MissionLocationAuthorizationState.WHEN_IN_USE,
        )
        val runtime = IosAlarmRuntime(
            missionCoordinator = IosAlarmMissionCoordinator(
                dataSource = dataSource,
                inbox = LifecycleInbox(emptyList()),
                scheduler = scheduler,
                outcomeRecorder = LifecycleOutcomeRecorder(),
                missionStore = store,
            ),
            reconciler = IosAlarmReconciler(
                dataSource = dataSource,
                scheduler = scheduler,
                normalizeAlarmId = { it },
            ),
            locationService = locationService,
        )

        runtime.start()
        assertEquals(listOf(occurrenceId), locationService.startedOccurrences)

        locationService.updateAuthorization(MissionLocationAuthorizationState.DENIED)
        runtime.start()

        assertEquals(1, locationService.stopCount)
        assertFalse(locationService.currentState().isTracking)

        locationService.updateAuthorization(MissionLocationAuthorizationState.WHEN_IN_USE)
        runtime.start()

        assertEquals(listOf(occurrenceId, occurrenceId), locationService.startedOccurrences)
        assertTrue(locationService.currentState().isTracking)
        runtime.forceEndActiveMission(occurrenceId)
    }

    @Test
    fun restoredMissionStillTracksWhenInboxAndDeadlineRecoveryFail() = runBlocking {
        val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        val occurrenceId = "$CanonicalUuid:recovery-failure"
        val store = LifecycleMissionStore().apply {
            saveActiveMission(
                ActiveAlarmMission(
                    alarmId = CanonicalUuid,
                    destinationName = "회사",
                    limitMinutes = 12,
                    expiresAtEpochMillis = nowEpochMillis + 720_000L,
                    occurrenceId = occurrenceId,
                    startedAtEpochMillis = nowEpochMillis,
                    destinationLatitude = 37.5,
                    destinationLongitude = 127.0,
                ),
            )
            saveDeadlineAlarm(
                IosMissionDeadlineAlarm(
                    alarmKitId = "6D54E949-27AB-4B3D-9B3D-F72E6796076A",
                    sourceOccurrenceId = occurrenceId,
                    retryOccurrenceId = "$occurrenceId:retry-1",
                    retryAttempt = 1,
                ),
            )
        }
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler(snapshotCode = IosAlarmOperationCode.SDK_ERROR)
        val locationService = LifecycleLocationService()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = LifecycleInbox(
                pending = emptyList(),
                resultCode = IosAlarmOperationCode.SDK_ERROR,
            ),
            scheduler = scheduler,
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )
        val runtime = IosAlarmRuntime(
            missionCoordinator = coordinator,
            reconciler = IosAlarmReconciler(
                dataSource = dataSource,
                scheduler = scheduler,
                normalizeAlarmId = { it },
            ),
            locationService = locationService,
        )

        runtime.start()

        assertEquals(listOf(occurrenceId), locationService.startedOccurrences)
        assertEquals(occurrenceId, runtime.activeMissionFlow.value?.occurrenceId)
        runtime.forceEndActiveMission(occurrenceId)
    }

    private fun coordinator(
        event: IosAlarmMissionEventDto,
        store: LifecycleMissionStore,
        outcomes: IosMissionOutcomeRecorder,
        scheduler: LifecycleScheduler = LifecycleScheduler(),
    ) = IosAlarmMissionCoordinator(
        dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
        inbox = LifecycleInbox(listOf(event)),
        scheduler = scheduler,
        outcomeRecorder = outcomes,
        missionStore = store,
    )

    private fun missionEvent() = IosAlarmMissionEventDto(
        eventId = "event-runtime",
        alarmId = CanonicalUuid,
        occurrenceId = "$CanonicalUuid:occurrence-runtime",
        action = IosAlarmMissionAction.STOP,
        occurredAtEpochMillis = 1_000L,
    )

    private fun savedAlarm(
        id: String,
        repeatEnabled: Boolean = true,
        selectedDays: List<String> = listOf("월"),
        limitMinutes: Int = 12,
    ) = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = id,
            time = "07:05",
            selectedDays = selectedDays,
            repeatEnabled = repeatEnabled,
            limitMinutes = limitMinutes,
            destinationName = "회사",
            destinationAddress = "주소",
            destinationLatitude = 37.5,
            destinationLongitude = 127.0,
            alarmSoundName = "기본",
            alarmSoundUri = null,
        ),
        enabled = true,
    )

    private companion object {
        const val CanonicalUuid = "D08814F5-0B28-4454-91F0-F6931224EBD6"
        fun canonicalUuid(id: String): String? = id.takeIf { it.length == CanonicalUuid.length }
    }
}

private class LifecycleAlarmDataSource(initial: List<SavedAlarmSchedule>) : AlarmDataSource {
    private val alarms = initial.associateByTo(linkedMapOf()) { it.request.id }
    private val observed = MutableStateFlow(initial)
    val events = mutableListOf<String>()
    override fun observeAll(): Flow<List<SavedAlarmSchedule>> = observed
    override suspend fun getAll() = alarms.values.toList()
    override suspend fun getEnabled() = alarms.values.filter { it.enabled }
    override suspend fun getById(id: String) = alarms[id]
    override suspend fun replace(alarm: SavedAlarmSchedule) { alarms[alarm.request.id] = alarm }
    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val alarm = alarms[id] ?: return false
        alarms[id] = alarm.copy(enabled = enabled)
        return true
    }
    override suspend fun delete(id: String) = alarms.remove(id) != null
    override suspend fun migrateId(oldId: String, newId: String): Boolean {
        val alarm = alarms.remove(oldId) ?: return false
        alarms[newId] = alarm.copy(request = alarm.request.copy(id = newId))
        events += "migrate:$oldId:$newId"
        return true
    }
    override suspend fun hasStorageMigration(id: String) = false
    override suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>, migrationId: String, completedAtEpochMillis: Long,
    ) = false
}

private class LifecycleScheduler(
    alarms: List<IosScheduledAlarmDto> = emptyList(),
    private val snapshotCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    retryScheduleFailures: Int = 0,
) : IosAlarmScheduler {
    private val scheduledAlarms = alarms.associateByTo(linkedMapOf()) { it.alarmId }
    private var remainingRetryScheduleFailures = retryScheduleFailures
    private var remainingCancelFailures = 0
    val events = mutableListOf<String>()
    val retryAlarmKitIds = mutableListOf<String>()

    fun removeScheduledAlarm(alarmKitId: String) {
        scheduledAlarms.remove(alarmKitId)
    }

    fun failNextRetrySchedules(count: Int = 1) {
        remainingRetryScheduleFailures += count
    }

    fun failNextCancellations(count: Int = 1) {
        remainingCancelFailures += count
    }

    override fun authorizationState() = IosAlarmAuthorizationState.AUTHORIZED
    override fun requestAuthorization(callback: (IosAlarmAuthorizationResult) -> Unit) =
        callback(IosAlarmAuthorizationResult(IosAlarmAuthorizationState.AUTHORIZED))
    override fun schedule(request: IosAlarmScheduleDto, callback: (IosAlarmOperationResult) -> Unit) {
        events += "schedule:${request.alarmId}"
        scheduledAlarms[request.alarmId] =
            IosScheduledAlarmDto(request.alarmId, IosScheduledAlarmState.SCHEDULED)
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun scheduleRetry(
        request: IosAlarmRetryScheduleDto,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        events += "retry:${request.occurrenceId}"
        retryAlarmKitIds += request.alarmKitId
        if (remainingRetryScheduleFailures > 0) {
            remainingRetryScheduleFailures -= 1
            callback(IosAlarmOperationResult(IosAlarmOperationCode.SDK_ERROR))
            return
        }
        scheduledAlarms[request.alarmKitId] =
            IosScheduledAlarmDto(request.alarmKitId, IosScheduledAlarmState.SCHEDULED)
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun cancel(alarmId: String, callback: (IosAlarmOperationResult) -> Unit) {
        events += "cancel:$alarmId"
        if (remainingCancelFailures > 0) {
            remainingCancelFailures -= 1
            callback(IosAlarmOperationResult(IosAlarmOperationCode.SDK_ERROR))
            return
        }
        scheduledAlarms.remove(alarmId)
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun scheduledAlarms(callback: (IosScheduledAlarmsResult) -> Unit) =
        callback(
            IosScheduledAlarmsResult(
                alarms = scheduledAlarms.values.toList(),
                code = snapshotCode,
            ),
        )
}

private class LifecycleInbox(
    pending: List<IosAlarmMissionEventDto>,
    private val resultCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
) :
    IosAlarmMissionEventInbox {
    private val pending = pending.toMutableList()
    val events = mutableListOf<String>()

    fun enqueue(event: IosAlarmMissionEventDto) {
        pending += event
    }

    override fun pendingEvents(callback: (IosAlarmMissionEventsResult) -> Unit) =
        callback(IosAlarmMissionEventsResult(events = pending, code = resultCode))
    override fun markConsumed(eventId: String, callback: (IosAlarmOperationResult) -> Unit) {
        events += "consume:$eventId"
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
}

private class LifecycleMissionStore : IosActiveAlarmMissionStore {
    var mission: ActiveAlarmMission? = null
    private val consumed = mutableSetOf<String>()
    private var lastLocation: ActiveAlarmMissionLocation? = null
    private var deadlineLocation: ActiveAlarmMissionLocation? = null
    private var deadlineAlarm: IosMissionDeadlineAlarm? = null
    private var pendingTerminal: IosPendingMissionTerminal? = null
    val events = mutableListOf<String>()
    override fun loadActiveMission() = mission
    override fun saveActiveMission(mission: ActiveAlarmMission) {
        this.mission = mission
        events += "save:${mission.occurrenceId}"
    }
    override fun clearActiveMission() { mission = null }
    override fun isConsumed(occurrenceId: String) = occurrenceId in consumed
    override fun markConsumed(occurrenceId: String) { consumed += occurrenceId }
    override fun loadLastLocation() = lastLocation
    override fun saveLastLocation(location: ActiveAlarmMissionLocation) {
        lastLocation = location
    }
    override fun clearLastLocation() { lastLocation = null }
    override fun loadDeadlineLocation() = deadlineLocation
    override fun saveDeadlineLocation(location: ActiveAlarmMissionLocation) {
        deadlineLocation = location
    }
    override fun clearDeadlineLocation() { deadlineLocation = null }
    override fun loadDeadlineAlarm() = deadlineAlarm
    override fun saveDeadlineAlarm(registration: IosMissionDeadlineAlarm) {
        deadlineAlarm = registration
    }
    override fun clearDeadlineAlarm() { deadlineAlarm = null }
    override fun loadPendingTerminal() = pendingTerminal
    override fun savePendingTerminal(pending: IosPendingMissionTerminal) {
        pendingTerminal = pending
    }
    override fun clearPendingTerminal() { pendingTerminal = null }
}

private class LifecycleOutcomeRecorder : IosMissionOutcomeRecorder {
    val successes = mutableListOf<String>()
    val failures = mutableListOf<String>()
    val successDates = mutableListOf<String>()
    val failureDates = mutableListOf<String>()
    override suspend fun recordSuccess(occurrenceId: String, completedAt: String) {
        successes += occurrenceId
        successDates += completedAt
    }
    override suspend fun recordFailure(occurrenceId: String, completedAt: String) {
        failures += occurrenceId
        failureDates += completedAt
    }
}

private class FailOnceLifecycleOutcomeRecorder : IosMissionOutcomeRecorder {
    private var shouldFail = true
    val recordedFailures = mutableListOf<String>()

    override suspend fun recordSuccess(occurrenceId: String, completedAt: String) = Unit

    override suspend fun recordFailure(occurrenceId: String, completedAt: String) {
        if (shouldFail) {
            shouldFail = false
            error("simulated process interruption")
        }
        recordedFailures += occurrenceId
    }
}

private class LifecycleLocationService(
    initialAuthorization: MissionLocationAuthorizationState =
        MissionLocationAuthorizationState.ALWAYS,
) : IosMissionLocationService {
    private var state = MissionLocationState(
        services = MissionLocationServicesState.ENABLED,
        authorization = initialAuthorization,
        accuracy = MissionLocationAccuracyState.FULL,
    )
    private var listener: IosMissionLocationListener? = null
    val startedOccurrences = mutableListOf<String>()
    var stopCount = 0

    override fun currentState() = state

    override fun setListener(listener: IosMissionLocationListener?) {
        this.listener = listener
    }

    override fun requestWhenInUseAuthorization() = Unit

    override fun requestAlwaysAuthorization() = Unit

    override fun confirmAlwaysAuthorizationResult() = Unit

    override fun requestTemporaryFullAccuracyAuthorization(purposeKey: String) = Unit

    override fun startTracking(occurrenceId: String) {
        startedOccurrences += occurrenceId
        state = state.copy(isTracking = true)
    }

    override fun stopTracking() {
        stopCount += 1
        state = state.copy(isTracking = false)
    }

    override fun beginBackgroundRecovery(recoveryId: String) = Unit

    override fun endBackgroundRecovery(recoveryId: String) = Unit

    fun updateAuthorization(authorization: MissionLocationAuthorizationState) {
        state = state.copy(
            authorization = authorization,
            revision = state.revision + 1L,
        )
        listener?.onStateChanged(state)
    }
}
