package com.joon.ringout.domain.room

interface RoomRepository {
    suspend fun getRooms(): List<RoomSummary>
}
