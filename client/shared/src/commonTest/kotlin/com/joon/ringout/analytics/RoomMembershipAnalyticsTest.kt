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
    fun `조회하지 않은 계정은 알 수 없지만 확인한 가입 상태는 하루 이후에도 유지한다`() {
        var now = 0L
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { now })
        assertEquals(RoomMembershipSnapshot(), store.current())
        store.replace("a", 0, setOf(1, 2))
        assertEquals(RoomMembershipSnapshot("joined", 2, 0, 0), store.current())
        now = 25 * 60 * 60 * 1_000L
        assertEquals(RoomMembershipSnapshot("joined", 2, 0, 0), store.current())
        store.update("a", 3, true)
        assertEquals(RoomMembershipSnapshot("joined", 3, 0, now), store.current())
    }

    @Test
    fun `가입 변경 이전에 요청한 목록과 상세 응답은 최신 참여 정보를 덮어쓰지 않는다`() {
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { 0 })
        store.replace("a", 0, emptySet())
        val revision = store.revision("a")
        store.update("a", 1, true)
        store.replace("a", revision, emptySet())
        store.update("a", 1, false, revision)
        assertEquals(RoomMembershipSnapshot("joined", 1, 0, 0), store.current())
        store.update("a", 1, false)
        assertEquals(RoomMembershipSnapshot("not_joined", 0, 0, 0), store.current())
    }

    @Test
    fun `계정 변경과 재시작 및 재시도 이후에도 최초 미션의 참여 상태를 유지한다`() {
        var owner: String? = "a"
        val storage = MemoryRoomAnalyticsStorage()
        val first = RoomMembershipAnalytics(storage, { owner }, { 0 })
        first.replace("a", 0, setOf(1, 2))
        assertEquals(RoomMembershipSnapshot("joined", 2, 0, 0), first.startMission(1, true))
        first.update("a", 1, false)
        owner = "b"
        val recreated = RoomMembershipAnalytics(storage, { owner }, { 0 })
        assertEquals(RoomMembershipSnapshot(), recreated.current())
        assertEquals(RoomMembershipSnapshot("joined", 2, 0, 0), recreated.startMission(1, false))
        assertEquals(RoomMembershipSnapshot("joined", 2, 0, 0), recreated.mission(1))
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
    fun `미가입 상태도 장기간 경과와 재시작 후 유지하고 전체 조회로 다시 갱신한다`() {
        var now = 100L
        val storage = MemoryRoomAnalyticsStorage()
        val store = RoomMembershipAnalytics(storage, { "a" }, { now })
        store.replace("a", 0, emptySet())
        now += 30L * 24 * 60 * 60 * 1_000
        val recreated = RoomMembershipAnalytics(storage, { "a" }, { now })
        assertEquals(RoomMembershipSnapshot("not_joined", 0, 100, 100), recreated.current())
        recreated.replace("a", recreated.revision("a"), setOf(2))
        assertEquals(RoomMembershipSnapshot("joined", 1, now, now), recreated.current())
    }

    @Test
    fun `부분 응답은 전체 확인 시각을 보존하고 중복 가입을 합산하지 않는다`() {
        var now = 100L
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { now })
        store.replace("a", 0, setOf(1))
        now = 200
        store.update("a", 2, true)
        store.update("a", 2, true)
        assertEquals(RoomMembershipSnapshot("joined", 2, 100, 200), store.current())
        val original = store.startMission(1, true)
        now = 300
        store.update("a", 1, false)
        assertEquals(RoomMembershipSnapshot("joined", 1, 100, 300), store.current())
        assertEquals(original, store.startMission(1, true))
        assertEquals(original, store.mission(1))
    }

    @Test
    fun `일부 가입만 확인하면 전체 가입 수와 전체 확인 시각을 추정하지 않는다`() {
        val store = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { 100 })
        store.update("a", 1, true)
        assertEquals(RoomMembershipSnapshot("joined", observedAt = 100), store.current())
        store.update("a", 1, false)
        assertEquals(RoomMembershipSnapshot(), store.current())
    }

    @Test
    fun `기존 전체 캐시의 확인 시각은 복원하지만 마지막 부분 확인 시각을 추정하지 않는다`() {
        val storage = MemoryRoomAnalyticsStorage()
        storage.write("account:a", """{"ids":[1],"complete":true,"checkedAt":100,"revision":1}""")
        val store = RoomMembershipAnalytics(storage, { "a" }, { 200 })
        assertEquals(RoomMembershipSnapshot("joined", 1, 100), store.current())
        store.update("a", 2, true)
        assertEquals(RoomMembershipSnapshot("joined", 2, 100, 200), store.current())
        assertEquals(store.current(), RoomMembershipAnalytics(storage, { "a" }).current())
    }

    @Test
    fun `기존 부분 캐시와 미션에는 확인 시각을 소급해서 넣지 않는다`() {
        val storage = MemoryRoomAnalyticsStorage()
        storage.write("account:a", """{"ids":[1],"complete":false,"checkedAt":100,"revision":1}""")
        storage.write("mission:1", """{"state":"joined","count":2}""")
        val store = RoomMembershipAnalytics(storage, { "a" }, { 200 })
        assertEquals(RoomMembershipSnapshot("joined"), store.current())
        store.replace("a", 1, setOf(1, 2, 3))
        assertEquals(RoomMembershipSnapshot("joined", 2), store.startMission(1, true))
        assertEquals(RoomMembershipSnapshot("joined", 2), store.mission(1))
    }

    @Test
    fun `시각이 없는 저장 정보와 손상된 저장 정보는 임의 시각으로 보완하지 않는다`() {
        val storage = MemoryRoomAnalyticsStorage()
        val store = RoomMembershipAnalytics(storage, { "a" }, { 200 })
        storage.write("account:a", """{"ids":[],"complete":true,"revision":1}""")
        assertEquals(RoomMembershipSnapshot("not_joined", 0), store.current())
        storage.write("account:a", "broken")
        assertEquals(RoomMembershipSnapshot(), store.current())
    }

    @Test
    fun `확인 시각은 밀리초 정수로 전송하고 미확인 시각은 생략한다`() {
        val parameters = RoomMembershipSnapshot("joined", 2, 100, 200).parameters()
        assertEquals(AnalyticsParameterValue.Number(100), parameters[AnalyticsParameterName.RoomListCheckedAtMillis])
        assertEquals(AnalyticsParameterValue.Number(200), parameters[AnalyticsParameterName.RoomMembershipObservedAtMillis])
        assertEquals(1, RoomMembershipSnapshot().parameters().size)
        assertEquals(2, RoomMembershipSnapshot("joined", 2).parameters().size)
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
