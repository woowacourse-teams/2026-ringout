package com.joon.ringout.data.room

import com.joon.ringout.analytics.RoomMembershipAnalytics
import com.joon.ringout.data.alarmoccurrence.alarmOccurrenceTokenOwner
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomRepository
import kotlinx.coroutines.CancellationException

/** 기존 서비스 조회 결과만 관찰한다. 분석을 위한 추가 네트워크 요청은 하지 않는다. */
internal class AnalyticsRoomRepository(
    private val delegate: RoomRepository,
    private val analytics: RoomMembershipAnalytics,
    private val tokens: SecureTokenStorage,
    private val session: AuthSession,
) : RoomRepository by delegate {
    private data class Owner(val id: String, val identity: Any, val revision: Long)
    private suspend fun capture(): Owner? = try {
        val identity = session.identity.value
        if (identity == null || session.state.value != AuthSessionState.Authenticated) null
        else tokens.read()?.accessToken?.let(::alarmOccurrenceTokenOwner)?.takeIf {
            session.identity.value === identity && session.state.value == AuthSessionState.Authenticated
        }?.let { Owner(it, identity, analytics.revision(it)) }
    } catch (error: CancellationException) { throw error } catch (_: Exception) { null }

    private suspend fun apply(owner: Owner?, block: (String) -> Unit) {
        if (owner == null) return
        val current = capture() ?: return
        if (current.id == owner.id && current.identity === owner.identity) block(owner.id)
    }

    override suspend fun getRooms() = capture().let { owner ->
        delegate.getRooms().also { rooms ->
            apply(owner) { analytics.replace(it, checkNotNull(owner).revision, rooms.filter { room -> room.isJoined }.map { room -> room.id }.toSet()) }
        }
    }
    override suspend fun getRoom(roomId: Long) = capture().let { owner ->
        delegate.getRoom(roomId).also { details -> apply(owner) { analytics.update(it, roomId, details.room.isJoined, checkNotNull(owner).revision) } }
    }
    override suspend fun createRoom(input: RoomCreateInput) = capture().let { owner ->
        delegate.createRoom(input).also { details -> apply(owner) { analytics.update(it, details.room.id, true) } }
    }
    override suspend fun joinRoom(roomId: Long) = capture().let { owner ->
        delegate.joinRoom(roomId).also { apply(owner) { analytics.update(it, roomId, true) } }
    }
    override suspend fun leaveRoom(roomId: Long) {
        val owner = capture()
        delegate.leaveRoom(roomId)
        apply(owner) { analytics.update(it, roomId, false) }
    }
    override suspend fun deleteRoom(roomId: Long) {
        val owner = capture()
        delegate.deleteRoom(roomId)
        apply(owner) { analytics.update(it, roomId, false) }
    }
}
