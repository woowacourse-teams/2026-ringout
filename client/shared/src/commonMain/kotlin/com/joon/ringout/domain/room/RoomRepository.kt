package com.joon.ringout.domain.room

interface RoomRepository {
    suspend fun getRooms(): List<RoomSummary>

    suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails

    suspend fun joinRoom(roomId: Long): RoomMembershipDetails
}
