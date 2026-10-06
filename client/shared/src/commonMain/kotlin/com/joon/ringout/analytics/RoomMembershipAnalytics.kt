package com.joon.ringout.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal interface RoomAnalyticsStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun <T> locked(block: () -> T): T
}

@Serializable
internal data class RoomMembershipSnapshot(val state: String = "unknown", val count: Int? = null) {
    fun parameters(): Map<AnalyticsParameterName, AnalyticsParameterValue> = buildMap {
        put(AnalyticsParameterName.RoomMembershipState, AnalyticsParameterValue.Text(state))
        count?.let { put(AnalyticsParameterName.JoinedRoomCount, AnalyticsParameterValue.Number(it.toLong())) }
    }
}

/** 계정 캐시는 24시간까지만 사용하고, 미션 스냅샷은 첫 시작 이후 변경하지 않는다. */
internal class RoomMembershipAnalytics(
    private val storage: RoomAnalyticsStorage,
    private val currentOwner: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun revision(owner: String): Long = safely(0L) { cache(owner)?.revision ?: 0L }

    fun replace(owner: String, expectedRevision: Long, joinedIds: Set<Long>) = safely(Unit) {
        val previous = cache(owner)
        if ((previous?.revision ?: 0L) != expectedRevision) return@safely
        save(owner, MembershipCache(joinedIds, true, now(), expectedRevision + 1))
    }

    fun update(owner: String, roomId: Long, joined: Boolean, expectedRevision: Long? = null) = safely(Unit) {
        val previous = cache(owner)
        if (expectedRevision != null && (previous?.revision ?: 0L) != expectedRevision) return@safely
        val fresh = previous?.takeIf { isFresh(it.checkedAt) }
        val ids = fresh?.ids.orEmpty().let { if (joined) it + roomId else it - roomId }
        // 부분 변경은 전체 목록의 확인 시각을 갱신하지 않는다.
        save(owner, MembershipCache(ids, fresh?.complete == true, fresh?.checkedAt ?: now(), (previous?.revision ?: 0) + 1))
    }

    fun current(): RoomMembershipSnapshot = safely(RoomMembershipSnapshot()) { currentUnlocked() }

    fun startMission(useIndex: Long, mayCaptureCurrent: Boolean): RoomMembershipSnapshot = safely(RoomMembershipSnapshot()) {
        val key = "mission:$useIndex"
        read<RoomMembershipSnapshot>(key)?.let { return@safely it }
        val snapshot = if (mayCaptureCurrent) currentUnlocked() else RoomMembershipSnapshot()
        storage.write(key, Json.encodeToString(snapshot))
        snapshot
    }

    fun mission(useIndex: Long): RoomMembershipSnapshot = safely(RoomMembershipSnapshot()) {
        read<RoomMembershipSnapshot>("mission:$useIndex") ?: RoomMembershipSnapshot()
    }

    private fun currentUnlocked(): RoomMembershipSnapshot {
        val owner = currentOwner() ?: return RoomMembershipSnapshot()
        if (owner == GuestOwner) return RoomMembershipSnapshot("not_joined", 0)
        val cached = cache(owner)?.takeIf { isFresh(it.checkedAt) } ?: return RoomMembershipSnapshot()
        return when {
            cached.complete -> RoomMembershipSnapshot(if (cached.ids.isEmpty()) "not_joined" else "joined", cached.ids.size)
            cached.ids.isNotEmpty() -> RoomMembershipSnapshot("joined")
            else -> RoomMembershipSnapshot()
        }
    }

    private fun isFresh(at: Long) = now() - at in 0..MaxAgeMillis
    private fun cache(owner: String) = read<MembershipCache>("account:$owner")
    private fun save(owner: String, cache: MembershipCache) = storage.write("account:$owner", Json.encodeToString(cache))
    private inline fun <reified T> read(key: String): T? = storage.read(key)?.let { Json.decodeFromString<T>(it) }
    private inline fun <T> safely(fallback: T, crossinline block: () -> T): T =
        runCatching { storage.locked { block() } }.getOrDefault(fallback)

    companion object {
        const val GuestOwner = "guest"
        const val MaxAgeMillis = 24 * 60 * 60 * 1_000L
    }
}

@Serializable
private data class MembershipCache(val ids: Set<Long>, val complete: Boolean, val checkedAt: Long, val revision: Long)
