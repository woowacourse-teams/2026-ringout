package com.joon.ringout.alarm

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosMissionArrivalNotificationTest {
    @Test
    fun `기존 두개부터 다섯개 필드 pending은 소급 알림 대상에서 제외한다`() {
        withDefaults { defaults ->
            val fields = listOf("occurrence", "SUCCESS", "1000", "2026-10-23", "chain")
            for (size in 2..5) {
                defaults.setObject(fields.take(size), forKey = PendingKey)
                val pending = assertNotNull(UserDefaultsIosActiveAlarmMissionStore(defaults).loadPendingTerminal())
                assertNull(pending.arrivalDestinationName)
                assertFalse(pending.arrivalFallbackAttempted)
                assertEquals(IosPendingMissionOutcome.SUCCESS, pending.outcome)
                assertEquals(if (size == 5) "chain" else "occurrence", pending.chainId)
                UserDefaultsIosActiveAlarmMissionStore(defaults).savePendingTerminal(pending)
                assertNull(UserDefaultsIosActiveAlarmMissionStore(defaults).loadPendingTerminal()?.arrivalDestinationName)
            }
        }
    }

    @Test
    fun `도착 알림 fallback 선점과 목적지는 저장소를 다시 열어도 유지한다`() {
        withDefaults { defaults ->
            val original = IosPendingMissionTerminal("occurrence", "chain", IosPendingMissionOutcome.SUCCESS,
                1_000, "2026-10-23", "완료 당시 목적지", true)
            UserDefaultsIosActiveAlarmMissionStore(defaults).savePendingTerminal(original)
            val pending = assertNotNull(UserDefaultsIosActiveAlarmMissionStore(defaults).loadPendingTerminal())
            assertEquals(original, pending)
            assertTrue(pending.arrivalFallbackAttempted)
            assertEquals(listOf("occurrence", "SUCCESS", "1000", "2026-10-23", "chain"),
                defaults.stringArrayForKey(PendingKey)?.take(5))
        }
    }

    private fun withDefaults(block: (NSUserDefaults) -> Unit) {
        val name = "ringout-arrival-test-${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = name))
        try { block(defaults) } finally { defaults.removePersistentDomainForName(name) }
    }

    private companion object {
        const val PendingKey = "ios.activeMission.pendingTerminal.v1"
    }
}
