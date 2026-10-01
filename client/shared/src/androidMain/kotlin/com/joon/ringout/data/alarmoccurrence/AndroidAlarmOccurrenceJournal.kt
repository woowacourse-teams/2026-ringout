package com.joon.ringout.data.alarmoccurrence

import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 서비스 종료 전에 디스크에 기록한다. Room 반영 실패 시 부모 울림부터 같은 순서로 재실행한다. */
internal class AndroidAlarmOccurrenceJournal(private val preferences: SharedPreferences) {
    private val flushMutex = Mutex()
    private val lock = Any()

    fun append(event: CapturedAlarmOccurrenceEvent) = synchronized(lock) {
        val key = "event:${event.key()}"
        if (preferences.contains(key)) return@synchronized
        val editor = preferences.edit()
        val captured = if (event is CapturedAlarmOccurrenceEvent.Rang && event.sourceRingingId == null) {
            val ownerKey = "owner:${event.ringingId}"
            // 최초 게스트 상태도 남겨 중복 전달 시 로그인한 계정으로 귀속되지 않게 한다.
            val owner = if (preferences.contains(ownerKey)) preferences.getString(ownerKey, "")
                else event.ownerAccountId.orEmpty().also { editor.putString(ownerKey, it) }
            event.copy(ownerAccountId = owner?.takeIf(String::isNotEmpty))
        } else event
        val sequence = preferences.getLong("sequence", 0) + 1
        check(editor.putLong("sequence", sequence)
            .putString(key, Json.encodeToString(Entry(sequence, captured))).commit()) {
            "알람 이벤트 저널을 저장하지 못했습니다."
        }
    }

    suspend fun drain(record: suspend (CapturedAlarmOccurrenceEvent) -> Unit) = flushMutex.withLock {
        val entries = synchronized(lock) {
            preferences.all.filterKeys { it.startsWith("event:") }
                .map { (key, value) -> key to Json.decodeFromString<Entry>(value as String) }
                .sortedBy { it.second.sequence }
        }
        for ((key, entry) in entries) {
            record(entry.event) // 실패 시 뒤의 해제/종료부터 처리하지 않는다.
            synchronized(lock) {
                check(preferences.edit().remove(key).commit()) { "알람 이벤트 저널을 정리하지 못했습니다." }
            }
        }
    }

    @Serializable
    private data class Entry(val sequence: Long, val event: CapturedAlarmOccurrenceEvent)
}

private fun CapturedAlarmOccurrenceEvent.key(): String = when (this) {
    is CapturedAlarmOccurrenceEvent.Rang -> "ring:$ringingId"
    is CapturedAlarmOccurrenceEvent.Dismissed -> "dismiss:$ringingId"
    is CapturedAlarmOccurrenceEvent.Terminal -> "terminal:$ringingId"
}
