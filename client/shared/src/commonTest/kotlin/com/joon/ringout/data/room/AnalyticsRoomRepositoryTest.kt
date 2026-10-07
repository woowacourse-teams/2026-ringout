package com.joon.ringout.data.room

import com.joon.ringout.analytics.MemoryRoomAnalyticsStorage
import com.joon.ringout.analytics.RoomMembershipAnalytics
import com.joon.ringout.analytics.RoomMembershipSnapshot
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsRoomRepositoryTest {
    @Test
    fun `탈퇴 전에 요청한 목록의 늦은 응답은 탈퇴 상태를 되돌리지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val tokens = RoomTokens()
        val cache = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "1" }, { 0 })
        cache.replace("1", 0, setOf(7))
        val response = CompletableDeferred<List<RoomSummary>>()
        val repository = AnalyticsRoomRepository(object : UnusedRoomRepository() {
            override suspend fun getRooms() = response.await()
            override suspend fun leaveRoom(roomId: Long) = Unit
        }, cache, tokens, session)
        val loading = async { repository.getRooms() }
        runCurrent()
        repository.leaveRoom(7)
        response.complete(listOf(summary()))
        loading.await()
        assertEquals(RoomMembershipSnapshot("not_joined", 0, 0, 0), cache.current())
    }

    @Test
    fun `계정이 변경된 동안 도착한 목록 응답은 어느 계정에도 저장하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val tokens = RoomTokens()
        var owner = "1"
        val cache = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { owner }, { 0 })
        val response = CompletableDeferred<List<RoomSummary>>()
        val repository = AnalyticsRoomRepository(object : UnusedRoomRepository() {
            override suspend fun getRooms() = response.await()
        }, cache, tokens, session)
        val loading = async { repository.getRooms() }
        runCurrent()
        tokens.save(roomTokens(2))
        owner = "2"
        session.startNewSession()
        response.complete(listOf(summary()))
        loading.await()
        assertEquals(RoomMembershipSnapshot(), cache.current())
        owner = "1"
        assertEquals(RoomMembershipSnapshot(), cache.current())
    }
}

private class RoomTokens : SecureTokenStorage {
    private var value: AuthTokens? = roomTokens(1)
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}

private fun roomTokens(id: Int) = AuthTokens("header." +
    Base64.UrlSafe.encode("""{"sub":"$id","userId":$id,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".signature", "refresh")

private fun summary() = RoomSummary(7, "room", null, null, listOf("MON"), "07:00", 1, true, "2026-10-01")

private abstract class UnusedRoomRepository : RoomRepository {
    override suspend fun getRooms(): List<RoomSummary> = error("unused")
    override suspend fun getRoom(roomId: Long): RoomMembershipDetails = error("unused")
    override suspend fun getMembersForManagement(roomId: Long): List<RoomManagementMember> = error("unused")
    override suspend fun getMemberMovements(roomId: Long): List<RoomMemberMovement> = error("unused")
    override suspend fun kickMember(roomId: Long, userId: Long): Unit = error("unused")
    override suspend fun getRoomRecords(roomId: Long, date: MissionDate): RoomRecords = error("unused")
    override suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails = error("unused")
    override suspend fun joinRoom(roomId: Long): RoomMembershipDetails = error("unused")
    override suspend fun updateRoom(roomId: Long, input: RoomUpdateInput): RoomUpdateResult = error("unused")
    override suspend fun deleteRoom(roomId: Long): Unit = error("unused")
    override suspend fun leaveRoom(roomId: Long): Unit = error("unused")
}
