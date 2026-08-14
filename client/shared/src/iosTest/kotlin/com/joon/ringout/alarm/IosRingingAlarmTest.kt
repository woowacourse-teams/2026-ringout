package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosRingingAlarmTest {
    @Test
    fun normalAlarmResolvesFromSavedSchedule() = runBlocking {
        val savedAlarm = savedAlarm(id = NormalAlarmId)

        val ringing = resolveIosRingingAlarm(
            systemAlarmId = NormalAlarmId,
            dataSource = RingingAlarmDataSource(listOf(savedAlarm)),
            deadlineAlarm = null,
            startedAtEpochMillis = StartedAtEpochMillis,
        )

        assertEquals(
            IosRingingAlarm(
                systemAlarmId = NormalAlarmId,
                alarmId = NormalAlarmId,
                occurrenceId = null,
                retryAttempt = 0,
                alarmTime = "07:05",
                destinationName = "회사",
                limitMinutes = 12,
                startedAtEpochMillis = StartedAtEpochMillis,
            ),
            ringing,
        )
    }

    @Test
    fun retryAlarmResolvesFromMatchingDeadlineRegistration() = runBlocking {
        val registration = IosMissionDeadlineAlarm(
            alarmKitId = RetryAlarmKitId,
            sourceOccurrenceId = "$NormalAlarmId:occurrence-1",
            retryOccurrenceId = "$NormalAlarmId:occurrence-1:retry-1",
            retryAttempt = 1,
            isScheduleConfirmed = true,
            missionSeed = IosRetryMissionSeed(
                alarmId = NormalAlarmId,
                destinationName = "서울역",
                limitMinutes = 8,
                alarmTime = "08:30",
                destinationLatitude = 37.5547,
                destinationLongitude = 126.9707,
                arrivalRadiusMeters = 100.0,
                alarmSoundUri = null,
                hasAlarmSoundUri = false,
            ),
        )

        val ringing = resolveIosRingingAlarm(
            systemAlarmId = RetryAlarmKitId,
            dataSource = RingingAlarmDataSource(emptyList()),
            deadlineAlarm = registration,
            startedAtEpochMillis = StartedAtEpochMillis,
        )

        assertEquals(
            IosRingingAlarm(
                systemAlarmId = RetryAlarmKitId,
                alarmId = NormalAlarmId,
                occurrenceId = registration.retryOccurrenceId,
                retryAttempt = 1,
                alarmTime = "08:30",
                destinationName = "서울역",
                limitMinutes = 8,
                startedAtEpochMillis = StartedAtEpochMillis,
            ),
            ringing,
        )
    }

    @Test
    fun unknownSystemAlarmIdDoesNotResolve() = runBlocking {
        assertNull(
            resolveIosRingingAlarm(
                systemAlarmId = "unknown",
                dataSource = RingingAlarmDataSource(listOf(savedAlarm(id = NormalAlarmId))),
                deadlineAlarm = null,
                startedAtEpochMillis = StartedAtEpochMillis,
            ),
        )
    }

    @Test
    fun alarmDateTextUsesDeterministicKoreanFormat() {
        assertEquals(
            "2024년 1월 1일 월요일",
            iosAlarmDateText(epochMillis = 1_704_110_400_000L),
        )
    }

    private fun savedAlarm(id: String) = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = id,
            time = "07:05",
            selectedDays = listOf("월", "금"),
            repeatEnabled = true,
            limitMinutes = 12,
            destinationName = "회사",
            destinationAddress = "서울특별시 중구 세종대로 110",
            destinationLatitude = 37.5665,
            destinationLongitude = 126.978,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        ),
        enabled = true,
    )

    private companion object {
        const val NormalAlarmId = "D08814F5-0B28-4454-91F0-F6931224EBD6"
        const val RetryAlarmKitId = "6D54E949-27AB-4B3D-9B3D-F72E6796076A"
        const val StartedAtEpochMillis = 1_723_615_200_000L
    }
}

private class RingingAlarmDataSource(initial: List<SavedAlarmSchedule>) : AlarmDataSource {
    private val alarms = initial.associateByTo(linkedMapOf()) { alarm -> alarm.request.id }
    private val observed = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<SavedAlarmSchedule>> = observed

    override suspend fun getAll(): List<SavedAlarmSchedule> = alarms.values.toList()

    override suspend fun getEnabled(): List<SavedAlarmSchedule> =
        alarms.values.filter(SavedAlarmSchedule::enabled)

    override suspend fun getById(id: String): SavedAlarmSchedule? = alarms[id]

    override suspend fun replace(alarm: SavedAlarmSchedule) {
        alarms[alarm.request.id] = alarm
        observed.value = alarms.values.toList()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val alarm = alarms[id] ?: return false
        alarms[id] = alarm.copy(enabled = enabled)
        observed.value = alarms.values.toList()
        return true
    }

    override suspend fun delete(id: String): Boolean {
        val removed = alarms.remove(id) != null
        if (removed) observed.value = alarms.values.toList()
        return removed
    }

    override suspend fun hasStorageMigration(id: String): Boolean = false

    override suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean = false
}
