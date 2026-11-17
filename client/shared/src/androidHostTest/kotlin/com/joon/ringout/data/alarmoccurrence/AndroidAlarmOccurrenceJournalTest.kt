package com.joon.ringout.data.alarmoccurrence

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidAlarmOccurrenceJournalTest {
    @Test
    fun `시계가 뒤로 변경되고 저널이 다시 생성되어도 캡처 순서와 최초 값을 복원한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val first = ring("1", 5_000)
        journal.append(first)
        journal.append(first.copy(at = 9_000, ownerAccountId = "2", eventId = "changed"))
        val dismissal = CapturedAlarmOccurrenceEvent.Dismissed("root", 1_000)
        journal.append(dismissal)
        val restored = mutableListOf<CapturedAlarmOccurrenceEvent>()
        AndroidAlarmOccurrenceJournal(preferences).drain { restored += it; true }
        assertEquals(listOf(first, dismissal), restored)
        assertTrue(preferences.all.keys.none { it.startsWith("event:") })
    }

    @Test
    fun `부모 울림 저장이 실패하면 해제도 보류하고 다음 실행에서 함께 재처리한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        journal.append(ring("1", 1_000))
        journal.append(CapturedAlarmOccurrenceEvent.Dismissed("root", 2_000))
        val attempts = mutableListOf<CapturedAlarmOccurrenceEvent>()
        assertFailsWith<IllegalStateException> {
            journal.drain { attempts += it; error("Room 저장 실패") }
        }
        AndroidAlarmOccurrenceJournal(preferences).drain { attempts += it; true }
        assertEquals(3, attempts.size)
        assertEquals(attempts[0], attempts[1])
        assertTrue(attempts[2] is CapturedAlarmOccurrenceEvent.Dismissed)
    }

    @Test
    fun `소유자가 아직 결정되지 않은 체인은 보존하고 다른 체인은 순서대로 처리한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val unresolvedStart = ring(null, 1_000)
        val unrelatedStart = ring("2", 1_100, ringingId = "other")
        val unresolvedDismissal = CapturedAlarmOccurrenceEvent.Dismissed("root", 1_200)
        val unrelatedDismissal = CapturedAlarmOccurrenceEvent.Dismissed("other", 1_300)
        journal.append(unresolvedStart)
        journal.append(unrelatedStart)
        journal.append(unresolvedDismissal)
        journal.append(unrelatedDismissal)

        val attempted = mutableListOf<CapturedAlarmOccurrenceEvent>()
        journal.drain { event ->
            attempted += event
            event != unresolvedStart
        }

        assertEquals(listOf(unresolvedStart, unrelatedStart, unrelatedDismissal), attempted)
        assertEquals(
            setOf("event:ring:root", "event:dismiss:root"),
            preferences.all.keys.filter { it.startsWith("event:") }.toSet(),
        )

        val retried = mutableListOf<CapturedAlarmOccurrenceEvent>()
        AndroidAlarmOccurrenceJournal(preferences).drain { retried += it; true }
        assertEquals(listOf(unresolvedStart, unresolvedDismissal), retried)
    }

    @Test
    fun `재울림이 미해결이면 같은 체인의 종료는 보류하고 다음 체인은 진행한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val root = ring("1", 1_000, ringingId = "root")
        val repeat = ring(null, 1_100, ringingId = "repeat").copy(sourceRingingId = "root")
        val terminal = CapturedAlarmOccurrenceEvent.Terminal("repeat", 1_200, arrived = false)
        val unrelated = ring("2", 1_300, ringingId = "unrelated")
        journal.append(root)
        journal.append(repeat)
        journal.append(terminal)
        journal.append(unrelated)

        val attempted = mutableListOf<CapturedAlarmOccurrenceEvent>()
        journal.drain { event ->
            attempted += event
            event != repeat
        }

        assertEquals(listOf<CapturedAlarmOccurrenceEvent>(root, repeat, unrelated), attempted)
        val retried = mutableListOf<CapturedAlarmOccurrenceEvent>()
        AndroidAlarmOccurrenceJournal(preferences).drain { retried += it; true }
        assertEquals(listOf(repeat, terminal), retried)
    }

    @Test
    fun `로그인 이관은 할당된 알람 ID와 버전이 같은 미확인 최초 울림만 귀속한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val eligible = ring(null, 1_000, ringingId = "eligible").copy(
            alarmId = "legacy-alarm",
            scheduleVersion = 3,
        )
        val wrongVersion = ring(null, 1_100, ringingId = "wrong-version").copy(
            alarmId = "legacy-alarm",
            scheduleVersion = 2,
        )
        val wrongAlarm = ring(null, 1_200, ringingId = "wrong-alarm").copy(
            alarmId = "other-alarm",
            scheduleVersion = 3,
        )
        val alreadyOwned = ring("old-account", 1_300, ringingId = "owned").copy(
            alarmId = "legacy-alarm",
            scheduleVersion = 3,
        )
        val repeat = ring(null, 1_400, ringingId = "repeat").copy(
            alarmId = "legacy-alarm",
            scheduleVersion = 3,
            sourceRingingId = "eligible",
        )
        listOf(eligible, wrongVersion, wrongAlarm, alreadyOwned, repeat).forEach(journal::append)

        assertEquals(
            setOf("legacy-alarm" to 3L, "legacy-alarm" to 2L, "other-alarm" to 3L),
            journal.unownedRootScheduleKeys(),
        )
        assertEquals(1, journal.bindUnownedRoots("42", setOf("legacy-alarm" to 3L)))
        journal.append(eligible.copy(ownerAccountId = "99", at = 9_000))

        val restored = mutableListOf<CapturedAlarmOccurrenceEvent>()
        journal.drain { restored += it; true }
        val ringingById = restored.filterIsInstance<CapturedAlarmOccurrenceEvent.Rang>()
            .associateBy(CapturedAlarmOccurrenceEvent.Rang::ringingId)
        assertEquals("42", ringingById.getValue("eligible").ownerAccountId)
        assertNull(ringingById.getValue("wrong-version").ownerAccountId)
        assertNull(ringingById.getValue("wrong-alarm").ownerAccountId)
        assertEquals("old-account", ringingById.getValue("owned").ownerAccountId)
        assertNull(ringingById.getValue("repeat").ownerAccountId)
    }

    @Test
    fun `Room 저장 후 저널 정리 실패 시 동일한 이벤트 아이디와 시각으로 재전달한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val event = ring("1", 1_000).copy(sourceRingingId = "previous", eventId = "stable-id")
        journal.append(event)
        val attempts = mutableListOf<CapturedAlarmOccurrenceEvent>()
        assertFailsWith<IllegalStateException> {
            journal.drain { attempts += it; preferences.failNextCommit = true; true }
        }
        AndroidAlarmOccurrenceJournal(preferences).drain { attempts += it; true }
        assertEquals(listOf<CapturedAlarmOccurrenceEvent>(event, event), attempts)
    }

    private fun ring(owner: String?, at: Long, ringingId: String = "root") = CapturedAlarmOccurrenceEvent.Rang(
        ringingId, "alarm", 1, 900, at, owner, eventId = "event-$ringingId",
    )
}

private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    var failNextCommit = false
    override fun getAll(): Map<String, *> = values.toMap()
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = error("사용하지 않는 조회")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            if (failNextCommit) { failNextCommit = false; return false }
            if (clear) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
    }
}
