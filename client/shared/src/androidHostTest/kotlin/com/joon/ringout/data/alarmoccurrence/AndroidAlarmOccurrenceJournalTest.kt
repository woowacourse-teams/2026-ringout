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
        AndroidAlarmOccurrenceJournal(preferences).drain { restored += it }
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
        assertEquals(1, attempts.size)
        AndroidAlarmOccurrenceJournal(preferences).drain { attempts += it }
        assertEquals(3, attempts.size)
        assertEquals(attempts[0], attempts[1])
        assertTrue(attempts[2] is CapturedAlarmOccurrenceEvent.Dismissed)
    }

    @Test
    fun `게스트 울림을 처리한 뒤 로그인과 재시작이 발생해도 중복 울림을 새 계정에 귀속하지 않는다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        journal.append(ring(null, 1_000))
        journal.drain { }
        val recreated = AndroidAlarmOccurrenceJournal(preferences)
        recreated.append(ring("2", 9_000))
        recreated.drain { assertNull((it as CapturedAlarmOccurrenceEvent.Rang).ownerAccountId) }
    }

    @Test
    fun `Room 저장 후 저널 정리 실패 시 동일한 이벤트 아이디와 시각으로 재전달한다`() = runBlocking {
        val preferences = MemoryPreferences()
        val journal = AndroidAlarmOccurrenceJournal(preferences)
        val event = ring("1", 1_000).copy(sourceRingingId = "previous", eventId = "stable-id")
        journal.append(event)
        val attempts = mutableListOf<CapturedAlarmOccurrenceEvent>()
        assertFailsWith<IllegalStateException> {
            journal.drain { attempts += it; preferences.failNextCommit = true }
        }
        AndroidAlarmOccurrenceJournal(preferences).drain { attempts += it }
        assertEquals(listOf<CapturedAlarmOccurrenceEvent>(event, event), attempts)
    }

    private fun ring(owner: String?, at: Long) = CapturedAlarmOccurrenceEvent.Rang(
        "root", "alarm", 1, 900, at, owner, eventId = "event-id",
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
