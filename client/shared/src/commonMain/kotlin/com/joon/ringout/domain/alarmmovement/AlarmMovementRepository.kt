package com.joon.ringout.domain.alarmmovement

import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId

enum class AlarmMovementAction { START_MOVEMENT, ARRIVE, GIVE_UP }

interface AlarmMovementRepository {
    suspend fun getJoinedRoomIds(): List<Long>
    suspend fun changeMovement(roomId: Long, occurrenceId: AlarmOccurrenceId, action: AlarmMovementAction)
}
