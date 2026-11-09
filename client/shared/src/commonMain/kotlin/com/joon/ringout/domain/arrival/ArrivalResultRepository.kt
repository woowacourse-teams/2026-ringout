package com.joon.ringout.domain.arrival

import kotlinx.coroutines.flow.Flow

/** Results are created atomically with local mission history, never by the presentation layer. */
interface ArrivalResultRepository {
    suspend fun getResult(occurrenceId: String): ArrivalResult?

    fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?>

    /** Acknowledges the selected result and earlier pending results without deleting growth history. */
    suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long)

    suspend fun getGrowth(): ArrivalGrowth

    fun observeGrowth(): Flow<ArrivalGrowth>
}
