package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

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
    private val missionStore: IosActiveAlarmMissionStore = UserDefaultsIosActiveAlarmMissionStore(),
) {
    private val mutex = Mutex()
    private val activeMission = MutableStateFlow(missionStore.loadActiveMission())

    val activeMissionFlow: StateFlow<ActiveAlarmMission?> = activeMission.asStateFlow()

    suspend fun processPendingEvents(): ActiveAlarmMission? = mutex.withLock {
        val events = inbox.pendingEventsAwait()
            .sortedBy(IosAlarmMissionEventDto::occurredAtEpochMillis)
        var startedMission: ActiveAlarmMission? = null
        for (event in events) {
            startedMission = processEventLocked(event) ?: startedMission
        }
        startedMission
    }

    private suspend fun processEventLocked(event: IosAlarmMissionEventDto): ActiveAlarmMission? {
        if (missionStore.isConsumed(event.occurrenceId)) {
            inbox.markConsumedAwait(event.eventId)
            return null
        }
        activeMission.value?.let { current ->
            return if (current.occurrenceId == event.occurrenceId) {
                inbox.markConsumedAwait(event.eventId)
                current
            } else {
                missionStore.markConsumed(event.occurrenceId)
                inbox.markConsumedAwait(event.eventId)
                null
            }
        }

        val savedAlarm = dataSource.getById(event.alarmId)
        if (savedAlarm == null || !savedAlarm.enabled) {
            missionStore.markConsumed(event.occurrenceId)
            inbox.markConsumedAwait(event.eventId)
            return null
        }

        val mission = savedAlarm.request.toActiveAlarmMission(event)
        missionStore.saveActiveMission(mission)
        activeMission.value = mission
        missionStore.markConsumed(event.occurrenceId)
        inbox.markConsumedAwait(event.eventId)
        return mission
    }

    suspend fun clearActiveMission(occurrenceId: String? = null) = mutex.withLock {
        val current = activeMission.value ?: return@withLock
        if (occurrenceId != null && current.occurrenceId != occurrenceId) return@withLock
        missionStore.clearActiveMission()
        activeMission.value = null
    }
}

interface IosActiveAlarmMissionStore {
    fun loadActiveMission(): ActiveAlarmMission?
    fun saveActiveMission(mission: ActiveAlarmMission)
    fun clearActiveMission()
    fun isConsumed(occurrenceId: String): Boolean
    fun markConsumed(occurrenceId: String)
}

internal class UserDefaultsIosActiveAlarmMissionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : IosActiveAlarmMissionStore {
    override fun loadActiveMission(): ActiveAlarmMission? {
        val rawRecord = defaults.stringArrayForKey(KeyActiveMission) ?: return null
        if (rawRecord.size != ActiveMissionRecordSize) return null
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

    private fun consumedOccurrenceIds(): List<String> =
        defaults.stringForKey(KeyConsumedOccurrences)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.toList()
            .orEmpty()

    private companion object {
        const val KeyActiveMission = "ios.activeMission.record.v2"
        const val KeyConsumedOccurrences = "ios.activeMission.consumedOccurrences"
        const val MaxConsumedOccurrences = 200
        const val ActiveMissionRecordSize = 12
    }
}

private fun AlarmScheduleRequest.toActiveAlarmMission(
    event: IosAlarmMissionEventDto,
): ActiveAlarmMission =
    ActiveAlarmMission(
        alarmId = id,
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        expiresAtEpochMillis = event.occurredAtEpochMillis + limitMinutes * MillisPerMinute,
        occurrenceId = event.occurrenceId,
        alarmTime = time,
        startedAtEpochMillis = event.occurredAtEpochMillis,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        arrivalRadiusMeters = targetDistanceKm * MetersPerKilometer,
        alarmSoundUri = alarmSoundUri,
        hasAlarmSoundUri = alarmSoundUri != null,
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
