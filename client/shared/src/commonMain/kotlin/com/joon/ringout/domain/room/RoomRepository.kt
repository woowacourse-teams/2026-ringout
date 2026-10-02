package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate

interface RoomRepository {
    suspend fun getRooms(): List<RoomSummary>

    suspend fun getRoom(roomId: Long): RoomMembershipDetails

    suspend fun getRoomRecords(roomId: Long, date: MissionDate): RoomRecords

    suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails

    suspend fun joinRoom(roomId: Long): RoomMembershipDetails

    suspend fun deleteRoom(roomId: Long)

    suspend fun leaveRoom(roomId: Long)
}
