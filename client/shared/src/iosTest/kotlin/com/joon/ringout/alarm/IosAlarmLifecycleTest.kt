package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
        val coordinator = IosAlarmMissionCoordinator(dataSource, inbox, missionStore)

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
            missionStore = LifecycleMissionStore(),
        )

        assertFailsWith<IosAlarmOperationException> { coordinator.processPendingEvents() }
        assertNull(coordinator.activeMissionFlow.value)
    }

    private fun savedAlarm(
        id: String,
        repeatEnabled: Boolean = true,
        selectedDays: List<String> = listOf("월"),
    ) = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = id,
            time = "07:05",
            selectedDays = selectedDays,
            repeatEnabled = repeatEnabled,
            limitMinutes = 12,
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
    private val alarms: List<IosScheduledAlarmDto> = emptyList(),
    private val snapshotCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
) : IosAlarmScheduler {
    val events = mutableListOf<String>()
    override fun authorizationState() = IosAlarmAuthorizationState.AUTHORIZED
    override fun requestAuthorization(callback: (IosAlarmAuthorizationResult) -> Unit) =
        callback(IosAlarmAuthorizationResult(IosAlarmAuthorizationState.AUTHORIZED))
    override fun schedule(request: IosAlarmScheduleDto, callback: (IosAlarmOperationResult) -> Unit) {
        events += "schedule:${request.alarmId}"
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun cancel(alarmId: String, callback: (IosAlarmOperationResult) -> Unit) {
        events += "cancel:$alarmId"
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }
    override fun scheduledAlarms(callback: (IosScheduledAlarmsResult) -> Unit) =
        callback(IosScheduledAlarmsResult(alarms = alarms, code = snapshotCode))
}

private class LifecycleInbox(
    private val pending: List<IosAlarmMissionEventDto>,
    private val resultCode: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
) :
    IosAlarmMissionEventInbox {
    val events = mutableListOf<String>()
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
    val events = mutableListOf<String>()
    override fun loadActiveMission() = mission
    override fun saveActiveMission(mission: ActiveAlarmMission) {
        this.mission = mission
        events += "save:${mission.occurrenceId}"
    }
    override fun clearActiveMission() { mission = null }
    override fun isConsumed(occurrenceId: String) = occurrenceId in consumed
    override fun markConsumed(occurrenceId: String) { consumed += occurrenceId }
}
