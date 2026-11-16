package com.joon.ringout.data.forest

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Durable handoff between successful arrival upload and forest acquisition evaluation. */
interface ForestArrivalPlanting {
    suspend fun recordConfirmedArrival(account: AlarmOccurrenceAccount, occurrenceId: String, arrivedAtEpochMillis: Long)
    suspend fun resolvePendingArrivals(account: AlarmOccurrenceAccount, now: () -> Long): ForestArrivalPlantingGate
    fun observeArrivalPlantingWork(): Flow<Unit>

    companion object {
        val None: ForestArrivalPlanting = object : ForestArrivalPlanting {
            override suspend fun recordConfirmedArrival(account: AlarmOccurrenceAccount, occurrenceId: String, arrivedAtEpochMillis: Long) = Unit
            override suspend fun resolvePendingArrivals(account: AlarmOccurrenceAccount, now: () -> Long) = ForestArrivalPlantingGate(true)
            override fun observeArrivalPlantingWork(): Flow<Unit> = flowOf(Unit)
        }
    }
}

data class ForestArrivalPlantingGate(val maySendArrival: Boolean, val nextRetryAtEpochMillis: Long? = null)
