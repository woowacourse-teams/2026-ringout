package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    fun presentationMigrationReschedulesIdleScheduledAlarmOnlyOnce() = runBlocking {
        val migrationState = LifecyclePresentationMigrationState()
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler(
            alarms = listOf(IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.SCHEDULED)),
        )
        val reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = ::canonicalUuid,
            presentationMigrationState = migrationState,
        )

        val firstReport = reconciler.reconcile()

        assertTrue(firstReport.isSuccess)
        assertEquals(listOf("schedule:$CanonicalUuid"), scheduler.events)
        assertTrue(
            CanonicalUuid in migrationState.migratedAlarmIds(
                CurrentAlarmPresentationVersionForTest,
            ),
        )
        assertTrue(migrationState.isMigrationComplete(CurrentAlarmPresentationVersionForTest))

        scheduler.events.clear()
        val secondReport = reconciler.reconcile()

        assertTrue(secondReport.isSuccess)
        assertTrue(scheduler.events.isEmpty())
    }

    @Test
    fun presentationMigrationSkipsActiveStatesAndPastOneTimeAlarms() = runBlocking {
        val countdownId = "2B2D336B-0AD7-4B4C-9200-FB8B2B143EE0"
        val pausedId = "3B2D336B-0AD7-4B4C-9200-FB8B2B143EE0"
        val pastOneTimeId = "4B2D336B-0AD7-4B4C-9200-FB8B2B143EE0"
        val migrationState = LifecyclePresentationMigrationState()
        val dataSource = LifecycleAlarmDataSource(
            listOf(
                savedAlarm(id = CanonicalUuid),
                savedAlarm(id = countdownId),
                savedAlarm(id = pausedId),
                savedAlarm(
                    id = pastOneTimeId,
                    repeatEnabled = false,
                    selectedDays = emptyList(),
                ),
            ),
        )
        val scheduler = LifecycleScheduler(
            alarms = listOf(
                IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.ALERTING),
                IosScheduledAlarmDto(countdownId, IosScheduledAlarmState.COUNTDOWN),
                IosScheduledAlarmDto(pausedId, IosScheduledAlarmState.PAUSED),
                IosScheduledAlarmDto(pastOneTimeId, IosScheduledAlarmState.SCHEDULED),
            ),
        )

        val reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = ::canonicalUuid,
            presentationMigrationState = migrationState,
            currentMinuteOfDay = { 23 * 60 + 59 },
        )

        val report = reconciler.reconcile()

        assertTrue(report.isSuccess)
        assertTrue(scheduler.events.isEmpty())
        assertTrue(migrationState.migrated.isEmpty())
        assertFalse(migrationState.isMigrationComplete(CurrentAlarmPresentationVersionForTest))

        scheduler.emitSnapshot(
            listOf(
                IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.SCHEDULED),
                IosScheduledAlarmDto(countdownId, IosScheduledAlarmState.SCHEDULED),
                IosScheduledAlarmDto(pausedId, IosScheduledAlarmState.SCHEDULED),
                IosScheduledAlarmDto(pastOneTimeId, IosScheduledAlarmState.SCHEDULED),
            ),
        )
        scheduler.events.clear()

        val recoveredReport = reconciler.reconcile()

        assertTrue(recoveredReport.isSuccess)
        assertEquals(
            listOf(
                "schedule:$CanonicalUuid",
                "schedule:$countdownId",
                "schedule:$pausedId",
            ),
            scheduler.events,
        )
        assertTrue(migrationState.isMigrationComplete(CurrentAlarmPresentationVersionForTest))
    }

    @Test
    fun presentationMigrationRetriesAfterScheduleFailureAndMarksRecoveredSchedule() = runBlocking {
        val migrationState = LifecyclePresentationMigrationState()
        val dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid)))
        val scheduler = LifecycleScheduler(
            alarms = listOf(IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.SCHEDULED)),
        )
        scheduler.failNextSchedules()
        val reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = ::canonicalUuid,
            presentationMigrationState = migrationState,
        )

        val failedReport = reconciler.reconcile()

        assertFalse(failedReport.isSuccess)
        assertEquals(listOf("schedule:$CanonicalUuid"), scheduler.events)
        assertTrue(scheduler.hasScheduledAlarm(CanonicalUuid))
        assertFalse(
            CanonicalUuid in migrationState.migratedAlarmIds(
                CurrentAlarmPresentationVersionForTest,
            ),
        )

        scheduler.events.clear()
        val recoveredReport = reconciler.reconcile()

        assertTrue(recoveredReport.isSuccess)
        assertEquals(listOf("schedule:$CanonicalUuid"), scheduler.events)
        assertTrue(
            CanonicalUuid in migrationState.migratedAlarmIds(
                CurrentAlarmPresentationVersionForTest,
            ),
        )
        assertTrue(migrationState.isMigrationComplete(CurrentAlarmPresentationVersionForTest))
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
        assertEquals(DefaultArrivalRadiusMeters, mission.arrivalRadiusMeters)
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
    fun locationOutsideDefaultArrivalRadiusDoesNotCompleteMission() = runBlocking {
        val event = missionEvent()
        val store = LifecycleMissionStore()
        val outcomes = LifecycleOutcomeRecorder()
        val coordinator = coordinator(event, store, outcomes)
        coordinator.processPendingEvents()

        assertFalse(
            coordinator.onLocationUpdated(
                occurrenceId = event.occurrenceId,
                location = ActiveAlarmMissionLocation(37.5005, 127.0, 5f, 2_000L),
                evaluatedAtEpochMillis = 2_000L,
            ),
        )

        assertNotNull(coordinator.activeMissionFlow.value)
        assertTrue(outcomes.successes.isEmpty())
    }

    @Test
    fun restoredLegacyMissionUsesDefaultArrivalRadius() {
        val store = LifecycleMissionStore().apply {
            mission = ActiveAlarmMission(
                alarmId = CanonicalUuid,
                destinationName = "회사",
                limitMinutes = 12,
                expiresAtEpochMillis = 721_000L,
                occurrenceId = "$CanonicalUuid:legacy",
                alarmTime = "07:05",
                startedAtEpochMillis = 1_000L,
                destinationLatitude = 37.5,
                destinationLongitude = 127.0,
                arrivalRadiusMeters = 1_200.0,
            )
        }

        val coordinator = IosAlarmMissionCoordinator(
            dataSource = LifecycleAlarmDataSource(listOf(savedAlarm(id = CanonicalUuid))),
            inbox = LifecycleInbox(emptyList()),
            scheduler = LifecycleScheduler(),
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )

        assertEquals(DefaultArrivalRadiusMeters, coordinator.activeMissionFlow.value?.arrivalRadiusMeters)
        assertEquals(DefaultArrivalRadiusMeters, store.loadActiveMission()?.arrivalRadiusMeters)
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
        store.saveDeadlineAlarm(
            retry.copy(
                missionSeed = retry.missionSeed!!.copy(arrivalRadiusMeters = 1_200.0),
            ),
        )
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
        assertEquals(DefaultArrivalRadiusMeters, retriedMission.arrivalRadiusMeters)
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
        val inbox = LifecycleInbox(emptyList())
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = scheduler,
            outcomeRecorder = outcomes,
            missionStore = store,
        )
        val runtime = IosAlarmRuntime(
            dataSource = dataSource,
            scheduler = scheduler,
            eventInbox = inbox,
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
        val inbox = LifecycleInbox(emptyList())
        val runtime = IosAlarmRuntime(
            dataSource = dataSource,
            scheduler = scheduler,
            eventInbox = inbox,
            missionCoordinator = IosAlarmMissionCoordinator(
                dataSource = dataSource,
                inbox = inbox,
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
        val inbox = LifecycleInbox(
            pending = emptyList(),
            resultCode = IosAlarmOperationCode.SDK_ERROR,
        )
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = scheduler,
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )
        val runtime = IosAlarmRuntime(
            dataSource = dataSource,
            scheduler = scheduler,
            eventInbox = inbox,
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

    @Test
    fun alertingSnapshotPresentsSavedAlarmAsRinging() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()

        val ringing = fixture.presentRingingAlarm()

        assertEquals(CanonicalUuid, ringing.systemAlarmId)
        assertEquals(CanonicalUuid, ringing.alarmId)
        assertEquals("07:05", ringing.alarmTime)
        assertEquals("회사", ringing.destinationName)
        assertEquals(12, ringing.limitMinutes)
        assertNull(ringing.occurrenceId)
        assertEquals(0, ringing.retryAttempt)
    }

    @Test
    fun repeatedSnapshotForSameAlarmRetainsRingingInstanceAndStartedAt() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()
        val first = fixture.presentRingingAlarm()
        delay(10)

        fixture.scheduler.emitSnapshot(
            listOf(IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.ALERTING)),
        )
        fixture.runtime.start()

        val retained = fixture.runtime.ringingAlarmFlow.value
        assertSame(first, retained)
        assertEquals(first.startedAtEpochMillis, retained?.startedAtEpochMillis)
    }

    @Test
    fun multipleAlertingAlarmsKeepCurrentlyPresentedAlarmAheadOfSortedIds() = runBlocking {
        val fixture = ringingRuntimeFixture(
            alarmIds = listOf(CanonicalUuid, EarlierSortingUuid),
        )
        fixture.runtime.start()
        val current = fixture.presentRingingAlarm(CanonicalUuid)
        delay(10)

        fixture.scheduler.emitSnapshot(
            listOf(
                IosScheduledAlarmDto(EarlierSortingUuid, IosScheduledAlarmState.ALERTING),
                IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.ALERTING),
            ),
        )
        fixture.runtime.start()

        assertSame(current, fixture.runtime.ringingAlarmFlow.value)
        assertEquals(CanonicalUuid, fixture.runtime.ringingAlarmFlow.value?.systemAlarmId)
    }

    @Test
    fun disappearingAlertingAlarmFallsBackToMissionWhenNativeStopEventIsMissing() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()
        val ringing = fixture.presentRingingAlarm()

        fixture.scheduler.emitSnapshot(emptyList())
        fixture.runtime.start()

        assertSame(ringing, fixture.runtime.ringingAlarmFlow.value)
        val mission = fixture.awaitActiveMission()
        fixture.awaitRingingCleared()

        assertNull(fixture.runtime.ringingAlarmFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        assertEquals(
            1,
            fixture.inbox.events.count { event -> event.startsWith("stop:$CanonicalUuid:") },
        )
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun failedStopFallbackKeepsRingingScreenInsteadOfSilentlyEndingAlarm() = runBlocking {
        val fixture = ringingRuntimeFixture(
            recordStopCode = IosAlarmOperationCode.SDK_ERROR,
        )
        fixture.runtime.start()
        val ringing = fixture.presentRingingAlarm()

        fixture.scheduler.emitSnapshot(emptyList())
        fixture.runtime.start()
        fixture.awaitInboxEvent("stop:$CanonicalUuid:")

        assertSame(ringing, fixture.runtime.ringingAlarmFlow.value)
        assertNull(fixture.runtime.activeMissionFlow.value)
    }

    @Test
    fun stopEventRecordedAfterAlertDisappearsStartsMissionAndClearsRinging() = runBlocking {
        val fixture = ringingRuntimeFixture(handoffGraceMillis = 100L)
        fixture.runtime.start()
        val ringing = fixture.presentRingingAlarm()

        fixture.scheduler.emitSnapshot(emptyList())
        fixture.runtime.start()
        assertSame(ringing, fixture.runtime.ringingAlarmFlow.value)

        fixture.inbox.enqueue(currentStopEvent(), notifyListener = true)
        val mission = fixture.awaitActiveMission()

        assertEquals(CanonicalUuid, mission.alarmId)
        assertNull(fixture.runtime.ringingAlarmFlow.value)
        assertEquals(
            1,
            fixture.store.events.count { event -> event.startsWith("save:") },
        )
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun stopEventRecordedBeforeAlertDisappearsStartsMissionOnlyOnce() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()
        fixture.presentRingingAlarm()

        val event = currentStopEvent()
        fixture.inbox.enqueue(event, notifyListener = true)
        val mission = fixture.awaitActiveMission()
        fixture.inbox.enqueue(
            event.copy(eventId = "event-runtime-duplicate"),
            notifyListener = true,
        )
        delay(10)

        assertNull(fixture.runtime.ringingAlarmFlow.value)
        assertEquals(
            1,
            fixture.store.events.count { stored -> stored.startsWith("save:") },
        )
        fixture.scheduler.emitSnapshot(
            listOf(IosScheduledAlarmDto(CanonicalUuid, IosScheduledAlarmState.ALERTING)),
        )
        fixture.runtime.start()
        assertNull(fixture.runtime.ringingAlarmFlow.value)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun retryStopEventPreservesOccurrenceAndAttemptWhenStartingMission() = runBlocking {
        val retryOccurrenceId = "$CanonicalUuid:retry-2"
        val fixture = ringingRuntimeFixture(
            pendingEvents = listOf(
                currentStopEvent().copy(
                    eventId = "retry-stop",
                    occurrenceId = retryOccurrenceId,
                    retryAttempt = 2,
                ),
            ),
        )

        fixture.runtime.start()

        val mission = assertNotNull(fixture.runtime.activeMissionFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        assertEquals(retryOccurrenceId, mission.occurrenceId)
        assertEquals(2, mission.retryAttempt)
        assertEquals(
            1,
            fixture.store.events.count { event -> event == "save:$retryOccurrenceId" },
        )
        assertEquals(listOf(retryOccurrenceId), fixture.locationService.startedOccurrences)
        assertEquals(retryOccurrenceId, fixture.store.loadDeadlineAlarm()?.sourceOccurrenceId)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun pendingStopEventOnColdStartRestoresMissionWithoutRingingScreen() = runBlocking {
        val fixture = ringingRuntimeFixture(pendingEvents = listOf(currentStopEvent()))

        fixture.runtime.start()

        val mission = assertNotNull(fixture.runtime.activeMissionFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        assertNull(fixture.runtime.ringingAlarmFlow.value)
        assertEquals(listOf(mission.occurrenceId), fixture.locationService.startedOccurrences)
        assertEquals(mission.occurrenceId, fixture.store.loadDeadlineAlarm()?.sourceOccurrenceId)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun ringingOneTimeAlarmDefersReconciliationUntilLateStopEventStartsMission() = runBlocking {
        val fixture = ringingRuntimeFixture(
            repeatEnabled = false,
            selectedDays = emptyList(),
            currentMinuteOfDay = { 23 * 60 + 59 },
            handoffGraceMillis = 100L,
        )
        fixture.presentRingingAlarm()

        fixture.scheduler.emitSnapshot(emptyList())
        fixture.runtime.start()
        assertTrue(assertNotNull(fixture.dataSource.getById(CanonicalUuid)).enabled)

        fixture.inbox.enqueue(currentStopEvent(), notifyListener = true)
        val mission = fixture.awaitActiveMission()

        assertEquals(CanonicalUuid, mission.alarmId)
        assertNull(fixture.runtime.ringingAlarmFlow.value)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun deliveredStopEventStartsMissionAfterOneTimeSourceWasDisabled() = runBlocking {
        val fixture = ringingRuntimeFixture(
            repeatEnabled = false,
            selectedDays = emptyList(),
        )
        fixture.dataSource.setEnabled(CanonicalUuid, false)
        fixture.inbox.enqueue(currentStopEvent(), notifyListener = true)

        fixture.runtime.start()

        val mission = assertNotNull(fixture.runtime.activeMissionFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun deliveredStopEventForDisabledRepeatingAlarmIsIgnored() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.dataSource.setEnabled(CanonicalUuid, false)
        fixture.inbox.enqueue(currentStopEvent(), notifyListener = true)

        fixture.runtime.start()

        assertNull(fixture.runtime.activeMissionFlow.value)
        assertTrue(
            fixture.inbox.events.any { event -> event == "consume:event-runtime" },
        )
    }

    @Test
    fun expectedDismissStopsAlarmCreatesOneMissionAndIsIdempotent() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()
        fixture.presentRingingAlarm()

        assertTrue(fixture.runtime.dismissRingingAlarm(CanonicalUuid))

        val mission = assertNotNull(fixture.runtime.activeMissionFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        assertEquals(
            1,
            fixture.scheduler.events.count { event -> event == "stop:$CanonicalUuid" },
        )
        assertEquals(
            1,
            fixture.inbox.events.count { event -> event.startsWith("open:$CanonicalUuid:") },
        )
        assertEquals(
            1,
            fixture.store.events.count { event -> event.startsWith("save:") },
        )
        assertNull(fixture.runtime.ringingAlarmFlow.value)

        assertTrue(fixture.runtime.dismissRingingAlarm(CanonicalUuid))
        assertEquals(
            1,
            fixture.scheduler.events.count { event -> event == "stop:$CanonicalUuid" },
        )
        assertEquals(
            1,
            fixture.inbox.events.count { event -> event.startsWith("open:$CanonicalUuid:") },
        )
        assertEquals(
            1,
            fixture.store.events.count { event -> event.startsWith("save:") },
        )
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun alertingReplacementEmittedBeforeStopCallbackSurvivesDismissCompletion() = runBlocking {
        val fixture = ringingRuntimeFixture(
            alarmIds = listOf(CanonicalUuid, EarlierSortingUuid),
        )
        fixture.runtime.start()
        fixture.presentRingingAlarm(CanonicalUuid)
        fixture.scheduler.deferNextStopAfterEmitting(
            listOf(
                IosScheduledAlarmDto(EarlierSortingUuid, IosScheduledAlarmState.ALERTING),
            ),
        )

        val dismissal = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.runtime.dismissRingingAlarm(CanonicalUuid)
        }
        fixture.runtime.start()
        val replacement = assertNotNull(fixture.runtime.ringingAlarmFlow.value)
        assertEquals(EarlierSortingUuid, replacement.systemAlarmId)
        fixture.scheduler.completePendingStop()

        assertTrue(dismissal.await())
        assertSame(replacement, fixture.runtime.ringingAlarmFlow.value)
        assertEquals(EarlierSortingUuid, fixture.runtime.ringingAlarmFlow.value?.systemAlarmId)
        val mission = assertNotNull(fixture.runtime.activeMissionFlow.value)
        assertEquals(CanonicalUuid, mission.alarmId)
        fixture.runtime.forceEndActiveMission(mission.occurrenceId)
    }

    @Test
    fun staleDismissIdDoesNotStopAlarmOrChangeRingingState() = runBlocking {
        val fixture = ringingRuntimeFixture()
        fixture.runtime.start()
        val ringing = fixture.presentRingingAlarm()

        assertFalse(fixture.runtime.dismissRingingAlarm("stale-alarm-id"))

        assertSame(ringing, fixture.runtime.ringingAlarmFlow.value)
        assertTrue(fixture.scheduler.events.none { event -> event.startsWith("stop:") })
        assertTrue(fixture.inbox.events.none { event -> event.startsWith("open:") })
        assertNull(fixture.runtime.activeMissionFlow.value)
    }

    @Test
    fun openEventFailurePreservesRingingStateAndDoesNotCreateMission() = runBlocking {
        val fixture = ringingRuntimeFixture(
            recordOpenCode = IosAlarmOperationCode.SDK_ERROR,
        )
        fixture.runtime.start()
        val ringing = fixture.presentRingingAlarm()

        assertFalse(fixture.runtime.dismissRingingAlarm(CanonicalUuid))

        assertSame(ringing, fixture.runtime.ringingAlarmFlow.value)
        assertEquals(
            1,
            fixture.scheduler.events.count { event -> event == "stop:$CanonicalUuid" },
        )
        assertEquals(
            1,
            fixture.inbox.events.count { event -> event.startsWith("open:$CanonicalUuid:") },
        )
        assertNull(fixture.runtime.activeMissionFlow.value)
    }

    private fun ringingRuntimeFixture(
        alarmIds: List<String> = listOf(CanonicalUuid),
        recordStopCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
        recordOpenCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
        pendingEvents: List<IosAlarmMissionEventDto> = emptyList(),
        handoffGraceMillis: Long = 20L,
        repeatEnabled: Boolean = true,
        selectedDays: List<String> = listOf("월"),
        currentMinuteOfDay: () -> Int = { 0 },
    ): RingingRuntimeFixture {
        val dataSource = LifecycleAlarmDataSource(
            alarmIds.map { id ->
                savedAlarm(
                    id = id,
                    repeatEnabled = repeatEnabled,
                    selectedDays = selectedDays,
                )
            },
        )
        val scheduler = LifecycleScheduler()
        val inbox = LifecycleInbox(
            pending = pendingEvents,
            recordStopCode = recordStopCode,
            recordOpenCode = recordOpenCode,
        )
        val store = LifecycleMissionStore()
        val locationService = LifecycleLocationService()
        val coordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = inbox,
            scheduler = scheduler,
            outcomeRecorder = LifecycleOutcomeRecorder(),
            missionStore = store,
        )
        return RingingRuntimeFixture(
            runtime = IosAlarmRuntime(
                dataSource = dataSource,
                scheduler = scheduler,
                eventInbox = inbox,
                missionCoordinator = coordinator,
                reconciler = IosAlarmReconciler(
                    dataSource = dataSource,
                    scheduler = scheduler,
                    normalizeAlarmId = { id -> id },
                    currentMinuteOfDay = currentMinuteOfDay,
                ),
                locationService = locationService,
                ringingHandoffGraceMillis = handoffGraceMillis,
                runtimeDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ),
            scheduler = scheduler,
            inbox = inbox,
            store = store,
            dataSource = dataSource,
            locationService = locationService,
        )
    }

    private suspend fun RingingRuntimeFixture.presentRingingAlarm(
        systemAlarmId: String = CanonicalUuid,
    ): IosRingingAlarm {
        scheduler.emitSnapshot(
            listOf(IosScheduledAlarmDto(systemAlarmId, IosScheduledAlarmState.ALERTING)),
        )
        runtime.start()
        return assertNotNull(runtime.ringingAlarmFlow.value)
            .also { ringing -> assertEquals(systemAlarmId, ringing.systemAlarmId) }
    }

    private suspend fun RingingRuntimeFixture.awaitActiveMission(): ActiveAlarmMission {
        repeat(100) {
            runtime.activeMissionFlow.value?.let { mission -> return mission }
            delay(1)
        }
        return assertNotNull(runtime.activeMissionFlow.value)
    }

    private suspend fun RingingRuntimeFixture.awaitRingingCleared() {
        repeat(100) {
            if (runtime.ringingAlarmFlow.value == null) return
            delay(1)
        }
        assertNull(runtime.ringingAlarmFlow.value)
    }

    private suspend fun RingingRuntimeFixture.awaitInboxEvent(prefix: String) {
        repeat(100) {
            if (inbox.events.any { event -> event.startsWith(prefix) }) return
            delay(1)
        }
        assertTrue(inbox.events.any { event -> event.startsWith(prefix) })
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

    private fun currentStopEvent() = missionEvent().copy(
        occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
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
        const val EarlierSortingUuid = "A08814F5-0B28-4454-91F0-F6931224EBD6"
        const val CanonicalUuid = "D08814F5-0B28-4454-91F0-F6931224EBD6"
        fun canonicalUuid(id: String): String? = id.takeIf { it.length == CanonicalUuid.length }
    }

    private data class RingingRuntimeFixture(
        val runtime: IosAlarmRuntime,
        val scheduler: LifecycleScheduler,
        val inbox: LifecycleInbox,
        val store: LifecycleMissionStore,
        val dataSource: LifecycleAlarmDataSource,
        val locationService: LifecycleLocationService,
    )
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
    private var remainingScheduleFailures = 0
    private var remainingRetryScheduleFailures = retryScheduleFailures
    private var remainingCancelFailures = 0
    private var stateListener: IosAlarmStateListener? = null
    private var nextStopSnapshot: List<IosScheduledAlarmDto>? = null
    private var pendingStopCompletion: (() -> Unit)? = null
    val events = mutableListOf<String>()
    val retryAlarmKitIds = mutableListOf<String>()

    fun removeScheduledAlarm(alarmKitId: String) {
        scheduledAlarms.remove(alarmKitId)
    }

    fun hasScheduledAlarm(alarmKitId: String) = alarmKitId in scheduledAlarms

    fun failNextRetrySchedules(count: Int = 1) {
        remainingRetryScheduleFailures += count
    }

    fun failNextSchedules(count: Int = 1) {
        remainingScheduleFailures += count
    }

    fun failNextCancellations(count: Int = 1) {
        remainingCancelFailures += count
    }

    fun emitSnapshot(alarms: List<IosScheduledAlarmDto>) {
        scheduledAlarms.clear()
        scheduledAlarms.putAll(alarms.associateBy(IosScheduledAlarmDto::alarmId))
        stateListener?.onAlarmsChanged(alarms)
    }

    fun deferNextStopAfterEmitting(alarms: List<IosScheduledAlarmDto>) {
        check(pendingStopCompletion == null)
        nextStopSnapshot = alarms
    }

    fun completePendingStop() {
        val completion = checkNotNull(pendingStopCompletion)
        pendingStopCompletion = null
        completion()
    }

    override fun authorizationState() = IosAlarmAuthorizationState.AUTHORIZED
    override fun requestAuthorization(callback: (IosAlarmAuthorizationResult) -> Unit) =
        callback(IosAlarmAuthorizationResult(IosAlarmAuthorizationState.AUTHORIZED))
    override fun schedule(request: IosAlarmScheduleDto, callback: (IosAlarmOperationResult) -> Unit) {
        events += "schedule:${request.alarmId}"
        if (remainingScheduleFailures > 0) {
            remainingScheduleFailures -= 1
            callback(IosAlarmOperationResult(IosAlarmOperationCode.SDK_ERROR))
            return
        }
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
    override fun stop(alarmId: String, callback: (IosAlarmOperationResult) -> Unit) {
        events += "stop:$alarmId"
        scheduledAlarms.remove(alarmId)
        nextStopSnapshot?.let { snapshot ->
            nextStopSnapshot = null
            emitSnapshot(snapshot)
            pendingStopCompletion = {
                callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
            }
            return
        }
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun scheduledAlarms(callback: (IosScheduledAlarmsResult) -> Unit) =
        callback(
            IosScheduledAlarmsResult(
                alarms = scheduledAlarms.values.toList(),
                code = snapshotCode,
            ),
        )
    override fun setStateListener(listener: IosAlarmStateListener?) {
        stateListener = listener
    }
}

private class LifecyclePresentationMigrationState : IosAlarmPresentationMigrationState {
    val migrated = linkedMapOf<Int, MutableSet<String>>()
    private val completedVersions = mutableSetOf<Int>()

    override fun isMigrationComplete(version: Int): Boolean = version in completedVersions

    override fun migratedAlarmIds(version: Int): Set<String> = migrated[version].orEmpty()

    override fun markAlarmMigrated(alarmId: String, version: Int) {
        migrated.getOrPut(version, ::linkedSetOf) += alarmId
    }

    override fun markMigrationComplete(version: Int) {
        completedVersions += version
    }
}

private const val CurrentAlarmPresentationVersionForTest = 2

private class LifecycleInbox(
    pending: List<IosAlarmMissionEventDto>,
    private val resultCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    private val recordStopCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    private val recordOpenCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
) :
    IosAlarmMissionEventInbox {
    private val pending = pending.toMutableList()
    private var listener: IosAlarmMissionEventListener? = null
    val events = mutableListOf<String>()

    fun enqueue(event: IosAlarmMissionEventDto, notifyListener: Boolean = false) {
        pending += event
        if (notifyListener) listener?.onEventRecorded()
    }

    override fun setEventListener(listener: IosAlarmMissionEventListener?) {
        this.listener = listener
    }

    override fun recordStopEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        events += "stop:$alarmId:${occurrenceId.orEmpty()}:$retryAttempt"
        if (recordStopCode != IosAlarmOperationCode.SUCCESS) {
            callback(IosAlarmOperationResult(recordStopCode))
            return
        }
        val eventOrdinal = events.count { event -> event.startsWith("stop:") }
        pending += IosAlarmMissionEventDto(
            eventId = "stop-event-$eventOrdinal",
            alarmId = alarmId,
            occurrenceId = occurrenceId ?: "$alarmId:stop-$eventOrdinal",
            action = IosAlarmMissionAction.STOP,
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            retryAttempt = retryAttempt,
        )
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
        listener?.onEventRecorded()
    }

    override fun recordOpenEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        events += "open:$alarmId:${occurrenceId.orEmpty()}:$retryAttempt"
        if (recordOpenCode != IosAlarmOperationCode.SUCCESS) {
            callback(IosAlarmOperationResult(recordOpenCode))
            return
        }
        val eventOrdinal = events.count { event -> event.startsWith("open:") }
        pending += IosAlarmMissionEventDto(
            eventId = "open-event-$eventOrdinal",
            alarmId = alarmId,
            occurrenceId = occurrenceId ?: "$alarmId:open-$eventOrdinal",
            action = IosAlarmMissionAction.OPEN_APP,
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            retryAttempt = retryAttempt,
        )
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
        listener?.onEventRecorded()
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
