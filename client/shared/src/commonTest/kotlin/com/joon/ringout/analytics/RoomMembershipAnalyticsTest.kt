package com.joon.ringout.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

internal class MemoryRoomAnalyticsStorage : RoomAnalyticsStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun <T> locked(block: () -> T): T = block()
}

class RoomMembershipAnalyticsTest {
    @Test
    fun `조회하지 않은 계정과 만료된 캐시는 미가입으로 추정하지 않는다`() {
        var now = 0L
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { now })
        assertEquals(RoomMembershipSnapshot(), store.current())
        store.replace("a", 0, setOf(1, 2))
        assertEquals(RoomMembershipSnapshot("joined", 2), store.current())
        now = RoomMembershipAnalytics.MaxAgeMillis + 1
        assertEquals(RoomMembershipSnapshot(), store.current())
        store.update("a", 3, true)
        assertEquals(RoomMembershipSnapshot("joined"), store.current())
    }

    @Test
    fun `가입 변경 이전에 요청한 목록과 상세 응답은 최신 참여 정보를 덮어쓰지 않는다`() {
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { 0 })
        store.replace("a", 0, emptySet())
        val revision = store.revision("a")
        store.update("a", 1, true)
        store.replace("a", revision, emptySet())
        store.update("a", 1, false, revision)
        assertEquals(RoomMembershipSnapshot("joined", 1), store.current())
        store.update("a", 1, false)
        assertEquals(RoomMembershipSnapshot("not_joined", 0), store.current())
    }

    @Test
    fun `계정 변경과 재시작 및 재시도 이후에도 최초 미션의 참여 상태를 유지한다`() {
        var owner: String? = "a"
        val storage = MemoryRoomAnalyticsStorage()
        val first = RoomMembershipAnalytics(storage, { owner }, { 0 })
        first.replace("a", 0, setOf(1, 2))
        assertEquals(RoomMembershipSnapshot("joined", 2), first.startMission(1, true))
        first.update("a", 1, false)
        owner = "b"
        val recreated = RoomMembershipAnalytics(storage, { owner }, { 0 })
        assertEquals(RoomMembershipSnapshot(), recreated.current())
        assertEquals(RoomMembershipSnapshot("joined", 2), recreated.startMission(1, false))
        assertEquals(RoomMembershipSnapshot("joined", 2), recreated.mission(1))
        owner = RoomMembershipAnalytics.GuestOwner
        assertEquals(RoomMembershipSnapshot("not_joined", 0), recreated.startMission(2, true))
        owner = null
        assertEquals(RoomMembershipSnapshot(), recreated.current())
    }

    @Test
    fun `업데이트 이전 미션은 현재 참여 상태로 소급하지 않는다`() {
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { 0 })
        store.replace("a", 0, setOf(1))
        assertEquals(RoomMembershipSnapshot(), store.startMission(1, false))
        assertEquals(RoomMembershipSnapshot(), store.startMission(1, true))
    }

    @Test
    fun `저장소 오류는 기능 실행을 중단시키지 않고 알 수 없음으로 처리한다`() {
        val store = RoomMembershipAnalytics(object : RoomAnalyticsStorage {
            override fun read(key: String): String? = error("unavailable")
            override fun write(key: String, value: String) = error("unavailable")
            override fun <T> locked(block: () -> T) = block()
        }, { "a" })
        store.update("a", 1, true)
        assertEquals(RoomMembershipSnapshot(), store.startMission(1, true))
        assertEquals(RoomMembershipSnapshot(), store.mission(1))
    }
}
