package com.joon.ringout.domain.room

interface RoomRepository {
    suspend fun getRooms(): List<RoomSummary>

    suspend fun getRoom(roomId: Long): RoomMembershipDetails

    suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails

    suspend fun joinRoom(roomId: Long): RoomMembershipDetails

    suspend fun deleteRoom(roomId: Long)

    suspend fun leaveRoom(roomId: Long)
}
