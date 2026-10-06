package com.joon.ringout.alarm

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

class IosMissionScheduleVersionTest {
    @Test
    fun `미션 저장소를 다시 열어도 진행 중인 실행과 재울림의 원래 설정 버전을 유지한다`() {
        val name = "ringout-version-test-${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = name))
        try {
            val store = UserDefaultsIosActiveAlarmMissionStore(defaults)
            store.saveActiveMission(ActiveAlarmMission(
                alarmId = "alarm", occurrenceId = "one", destinationName = "회사", limitMinutes = 5,
                startedAtEpochMillis = 1_000, expiresAtEpochMillis = 301_000,
                destinationLatitude = 37.5, destinationLongitude = 127.0, scheduleVersion = 4,
            ))
            store.saveDeadlineAlarm(IosMissionDeadlineAlarm(
                alarmKitId = "system", sourceOccurrenceId = "one", retryOccurrenceId = "one:retry-1", retryAttempt = 1,
                missionSeed = IosRetryMissionSeed(
                    alarmId = "alarm", destinationName = "회사", limitMinutes = 5, alarmTime = "07:00",
                    destinationLatitude = 37.5, destinationLongitude = 127.0, arrivalRadiusMeters = 30.0,
                    alarmSoundUri = null, hasAlarmSoundUri = false, scheduleVersion = 4,
                ),
            ))

            val restored = UserDefaultsIosActiveAlarmMissionStore(requireNotNull(NSUserDefaults(suiteName = name)))
            assertEquals(4L, restored.loadActiveMission()?.scheduleVersion)
            assertEquals(4L, restored.loadDeadlineAlarm()?.missionSeed?.scheduleVersion)
        } finally {
            defaults.removePersistentDomainForName(name)
        }
    }
}
