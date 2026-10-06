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
internal data class RoomMembershipSnapshot(
    val state: String = "unknown",
    val count: Int? = null,
    val listCheckedAt: Long? = null,
    val observedAt: Long? = null,
) {
    fun parameters(): Map<AnalyticsParameterName, AnalyticsParameterValue> = buildMap {
        put(AnalyticsParameterName.RoomMembershipState, AnalyticsParameterValue.Text(state))
        count?.let { put(AnalyticsParameterName.JoinedRoomCount, AnalyticsParameterValue.Number(it.toLong())) }
        listCheckedAt?.let { put(AnalyticsParameterName.RoomListCheckedAtMillis, AnalyticsParameterValue.Number(it)) }
        observedAt?.let { put(AnalyticsParameterName.RoomMembershipObservedAtMillis, AnalyticsParameterValue.Number(it)) }
    }
}

/** 계정별 마지막 확인 상태를 유지하고, 미션 스냅샷은 첫 시작 이후 변경하지 않는다. */
internal class RoomMembershipAnalytics(
    private val storage: RoomAnalyticsStorage,
    private val currentOwner: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun revision(owner: String): Long = safely(0L) { cache(owner)?.revision ?: 0L }

    fun replace(owner: String, expectedRevision: Long, joinedIds: Set<Long>) = safely(Unit) {
        val previous = cache(owner)
        if ((previous?.revision ?: 0L) != expectedRevision) return@safely
        val observedAt = now()
        save(owner, MembershipCache(
            ids = joinedIds,
            complete = true,
            revision = expectedRevision + 1,
            listCheckedAt = observedAt,
            observedAt = observedAt,
        ))
    }

    fun update(owner: String, roomId: Long, joined: Boolean, expectedRevision: Long? = null) = safely(Unit) {
        val previous = cache(owner)
        if (expectedRevision != null && (previous?.revision ?: 0L) != expectedRevision) return@safely
        val ids = previous?.ids.orEmpty().let { if (joined) it + roomId else it - roomId }
        // 부분 응답으로 전체 목록의 확인 시각을 갱신하지 않는다.
        save(owner, MembershipCache(
            ids = ids,
            complete = previous?.complete == true,
            revision = (previous?.revision ?: 0) + 1,
            listCheckedAt = previous?.fullListCheckedAt(),
            observedAt = now(),
        ))
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
        val cached = cache(owner) ?: return RoomMembershipSnapshot()
        return when {
            cached.complete -> RoomMembershipSnapshot(
                state = if (cached.ids.isEmpty()) "not_joined" else "joined",
                count = cached.ids.size,
                listCheckedAt = cached.fullListCheckedAt(),
                observedAt = cached.observedAt,
            )
            cached.ids.isNotEmpty() -> RoomMembershipSnapshot("joined", observedAt = cached.observedAt)
            else -> RoomMembershipSnapshot()
        }
    }

    private fun cache(owner: String) = read<MembershipCache>("account:$owner")
    private fun save(owner: String, cache: MembershipCache) = storage.write("account:$owner", Json.encodeToString(cache))
    private inline fun <reified T> read(key: String): T? = storage.read(key)?.let { Json.decodeFromString<T>(it) }
    private inline fun <T> safely(fallback: T, crossinline block: () -> T): T =
        runCatching { storage.locked { block() } }.getOrDefault(fallback)

    companion object {
        const val GuestOwner = "guest"
    }
}

@Serializable
private data class MembershipCache(
    val ids: Set<Long>,
    val complete: Boolean,
    // 이전 저장 형식 호환용. 부분 캐시의 checkedAt은 마지막 확인 시각이 아니므로 사용하지 않는다.
    val checkedAt: Long? = null,
    val revision: Long,
    val listCheckedAt: Long? = null,
    val observedAt: Long? = null,
) {
    fun fullListCheckedAt(): Long? = if (complete) listCheckedAt ?: checkedAt else null
}
