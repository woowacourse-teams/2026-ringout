package com.joon.ringout.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmUpdateDeferralTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val missionStore = ActiveAlarmMissionStore(application)

    @Before
    @After
    fun reset() {
        missionStore.clear()
        AlarmRingingRuntime.ringingOccurrenceId.value?.let(AlarmRingingRuntime::clearIfCurrent)
        application.getSharedPreferences("ringout_ringing_alarm_session", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `추적 중이거나 재울림을 기다리는 미션은 업데이트 차단을 유예한다`() {
        assertFalse(missionStore.hasPendingMission())
        saveMission()
        assertTrue(missionStore.hasPendingMission())

        missionStore.beginTerminalTransition("mission", AlarmMissionPhase.RetryPendingRing)

        assertNull(missionStore.read())
        assertTrue(missionStore.hasPendingMission())
    }

    @Test
    fun `도착이나 강제 종료 이후 기록 저장 대기만으로 업데이트 차단을 유예하지 않는다`() {
        listOf(AlarmMissionPhase.SuccessPendingNotification, AlarmMissionPhase.FailurePendingPersistence)
            .forEach { terminalPhase ->
                missionStore.clear()
                saveMission()
                missionStore.beginTerminalTransition("mission", terminalPhase, "2026-10-29", 100L)

                assertNotNull(missionStore.readStoredMission())
                assertFalse(missionStore.hasPendingMission())
            }
    }

    @Test
    fun `재울림이 시작되어 미션 기록을 지워도 울림이 끝날 때까지 유예 상태가 유지된다`() {
        saveMission()
        missionStore.beginTerminalTransition("mission", AlarmMissionPhase.RetryPendingRing)
        AlarmRingingRuntime.markRinging(ringingIntent("retry"))

        missionStore.completeTerminalTransition("mission", AlarmMissionPhase.RetryPendingRing)

        assertFalse(missionStore.hasPendingMission())
        assertEquals("retry", AlarmRingingRuntime.ringingOccurrenceId.value)
        AlarmRingingRuntime.clearIfCurrent("retry")
        assertNull(AlarmRingingRuntime.ringingOccurrenceId.value)
        assertFalse(AlarmRingingRuntime.openRingingActivity(application))
    }

    @Test
    fun `이전 울림의 종료가 새로 시작된 울림을 해제하지 않는다`() {
        AlarmRingingRuntime.markRinging(ringingIntent("previous"))
        AlarmRingingRuntime.markRinging(ringingIntent("current"))

        AlarmRingingRuntime.clearIfCurrent("previous")

        assertEquals("current", AlarmRingingRuntime.ringingOccurrenceId.value)
    }

    @Test
    fun `남아 있는 저장 세션은 실제 울림으로 간주하지 않는다`() {
        AlarmRingingSessionStore(application).markRinging("stale")

        assertNull(AlarmRingingRuntime.ringingOccurrenceId.value)
        assertFalse(AlarmRingingRuntime.openRingingActivity(application))
    }

    @Test
    fun `울림 화면을 다시 열 때 원본 인텐트 변경과 무관하게 현재 울림 정보를 사용한다`() {
        val original = ringingIntent("current").putExtra(AlarmRuntime.EXTRA_DESTINATION_NAME, "학교")
        AlarmRingingRuntime.markRinging(original)
        original.putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, "tampered")

        assertTrue(AlarmRingingRuntime.openRingingActivity(application))

        val launched = assertNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(AlarmRingingActivity::class.java.name, launched.component?.className)
        assertEquals("current", launched.getStringExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID))
        assertEquals("학교", launched.getStringExtra(AlarmRuntime.EXTRA_DESTINATION_NAME))
    }

    private fun saveMission() {
        assertNotNull(missionStore.saveFrom(ringingIntent("mission")))
    }

    private fun ringingIntent(occurrenceId: String): Intent = Intent(AlarmRuntime.ACTION_RING)
        .putExtra(AlarmRuntime.EXTRA_ALARM_ID, "alarm")
        .putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, occurrenceId)
}
