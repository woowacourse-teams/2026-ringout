package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate

interface RoomRepository {
    suspend fun getRooms(): List<RoomSummary>

    suspend fun getRoom(roomId: Long): RoomMembershipDetails

    suspend fun getMembersForManagement(roomId: Long): List<RoomManagementMember>

    suspend fun getMemberMovements(roomId: Long): List<RoomMemberMovement>

    suspend fun kickMember(roomId: Long, userId: Long)
    
    suspend fun getRoomRecords(roomId: Long, date: MissionDate): RoomRecords

    suspend fun createRoom(input: RoomCreateInput): RoomMembershipDetails

    suspend fun joinRoom(roomId: Long): RoomMembershipDetails

    suspend fun updateRoom(roomId: Long, input: RoomUpdateInput): RoomUpdateResult

    suspend fun deleteRoom(roomId: Long)

    suspend fun leaveRoom(roomId: Long)
}
