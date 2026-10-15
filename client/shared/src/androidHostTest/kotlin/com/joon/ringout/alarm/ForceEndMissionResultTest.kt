package com.joon.ringout.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForceEndMissionResultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ActiveAlarmMissionStore(context)

    @Before
    fun prepare() { store.clear() }

    @Test
    fun `강제 종료를 요청하면 확인 콜백 없이 활성 미션이 종료 상태로 전환된다`() {
        saveMission("fresh")
        assertEquals(ForceEndMissionResult.Completed, AlarmMissionCoordinator(context).forceEnd("fresh"))
        assertNull(store.read())
    }

    @Test
    fun `종료 확정 후 기록 저장이 대기 중이어도 다시 요청하면 완료 결과를 반환한다`() {
        saveMission()
        store.beginTerminalTransition("mission", AlarmMissionPhase.FailurePendingPersistence, "2026-10-15", 100L)
        val coordinator = AlarmMissionCoordinator(context)
        assertEquals(ForceEndMissionResult.Completed, coordinator.forceEnd("mission"))
        assertEquals(ForceEndMissionResult.Completed, coordinator.forceEnd("mission"))
        assertNull(store.read())
        assertEquals(100L, store.readStoredMission()?.terminalCompletedAtEpochMillis)
    }

    @Test
    fun `도착 처리가 먼저 확정된 미션은 강제 종료로 덮어쓰지 않는다`() {
        saveMission()
        store.beginTerminalTransition("mission", AlarmMissionPhase.SuccessPendingNotification, "2026-10-15", 100L)
        assertEquals(ForceEndMissionResult.NoLongerActive, AlarmMissionCoordinator(context).forceEnd("mission"))
        assertEquals(AlarmMissionPhase.SuccessPendingNotification, store.readStoredMission()?.phase)
    }

    @Test
    fun `만료 후 재울림 대기 중인 미션과 다른 회차는 강제 종료 완료로 반환하지 않는다`() {
        saveMission()
        store.beginTerminalTransition("mission", AlarmMissionPhase.RetryPendingRing)
        val coordinator = AlarmMissionCoordinator(context)
        assertEquals(ForceEndMissionResult.NoLongerActive, coordinator.forceEnd("mission"))
        assertEquals(ForceEndMissionResult.NoLongerActive, coordinator.forceEnd("old"))
        assertEquals(AlarmMissionPhase.RetryPendingRing, store.readStoredMission()?.phase)
    }

    private fun saveMission(id: String = "mission") {
        store.saveFrom(Intent().putExtra(AlarmRuntime.EXTRA_ALARM_ID, "alarm")
            .putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, id))
    }
}
