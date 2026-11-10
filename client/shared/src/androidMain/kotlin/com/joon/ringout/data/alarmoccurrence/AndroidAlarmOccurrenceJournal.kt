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

    suspend fun drain(record: suspend (CapturedAlarmOccurrenceEvent) -> Boolean) = flushMutex.withLock {
        val entries = synchronized(lock) {
            preferences.all.filterKeys { it.startsWith("event:") }
                .map { (key, value) -> key to Json.decodeFromString<Entry>(value as String) }
                .sortedBy { it.second.sequence }
        }
        val chainBySequence = chainBySequence(entries)
        val chains = entries.groupBy { (_, entry) -> chainBySequence.getValue(entry.sequence) }
            .values
            .sortedBy { chain -> chain.first().second.sequence }

        for (chain in chains) {
            for ((key, entry) in chain) {
                if (!record(entry.event)) break
                synchronized(lock) {
                    check(preferences.edit().remove(key).commit()) {
                        "알람 이벤트 저널을 정리하지 못했습니다."
                    }
                }
            }
        }
    }

    /** Rebinds only ownerless roots matching a Room schedule selected for legacy migration. */
    suspend fun bindUnownedRoots(
        ownerAccountId: String,
        assignedSchedules: Set<Pair<String, Long>>,
    ): Int {
        require(ownerAccountId.isNotBlank())
        if (assignedSchedules.isEmpty()) return 0

        return flushMutex.withLock {
            synchronized(lock) {
                val editor = preferences.edit()
                var rebound = 0
                preferences.all.forEach { (key, value) ->
                    if (!key.startsWith("event:")) return@forEach
                    val entry = Json.decodeFromString<Entry>(value as String)
                    val root = entry.event as? CapturedAlarmOccurrenceEvent.Rang ?: return@forEach
                    if (root.sourceRingingId != null || root.ownerAccountId != null) return@forEach
                    if ((root.alarmId to root.scheduleVersion) !in assignedSchedules) return@forEach

                    val ownerKey = "owner:${root.ringingId}"
                    if (!preferences.getString(ownerKey, "").isNullOrBlank()) return@forEach
                    editor.putString(
                        key,
                        Json.encodeToString(entry.copy(event = root.copy(ownerAccountId = ownerAccountId))),
                    )
                    editor.putString(ownerKey, ownerAccountId)
                    rebound += 1
                }
                if (rebound > 0) {
                    check(editor.commit()) { "레거시 알람 이벤트 소유자를 저장하지 못했습니다." }
                }
                rebound
            }
        }
    }

    fun unownedRootScheduleKeys(): Set<Pair<String, Long>> = synchronized(lock) {
        preferences.all.asSequence()
            .filter { (key, _) -> key.startsWith("event:") }
            .mapNotNull { (_, value) ->
                val entry = Json.decodeFromString<Entry>(value as String)
                val root = entry.event as? CapturedAlarmOccurrenceEvent.Rang ?: return@mapNotNull null
                if (root.sourceRingingId != null || root.ownerAccountId != null) return@mapNotNull null
                val ownerKey = "owner:${root.ringingId}"
                if (!preferences.getString(ownerKey, "").isNullOrBlank()) return@mapNotNull null
                root.alarmId to root.scheduleVersion
            }
            .toSet()
    }

    /** Joins repeat ringings to their parent chain so one unresolved chain cannot block another. */
    private fun chainBySequence(entries: List<Pair<String, Entry>>): Map<Long, String> {
        val parents = mutableMapOf<String, String>()

        fun find(id: String): String {
            if (id !in parents) parents[id] = id
            val parent = parents.getValue(id)
            if (parent == id) return id
            return find(parent).also { root -> parents[id] = root }
        }

        fun union(first: String, second: String) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parents[secondRoot] = firstRoot
        }

        for ((_, entry) in entries) {
            val event = entry.event
            find(event.ringingId)
            if (event is CapturedAlarmOccurrenceEvent.Rang) {
                event.sourceRingingId?.let { source -> union(event.ringingId, source) }
            }
        }

        return entries.associate { (_, entry) -> entry.sequence to find(entry.event.ringingId) }
    }

    suspend fun clear() = flushMutex.withLock {
        synchronized(lock) {
            check(preferences.edit().clear().commit()) {
                "로그아웃 대기 중인 알람 이벤트를 정리하지 못했습니다."
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
