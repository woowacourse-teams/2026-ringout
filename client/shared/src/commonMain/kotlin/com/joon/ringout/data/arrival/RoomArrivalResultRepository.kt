package com.joon.ringout.data.arrival

import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomArrivalResultRepository(
    private val dao: ArrivalResultDao,
) : ArrivalResultRepository {
    override suspend fun getResult(occurrenceId: String): ArrivalResult? = dao.getResult(occurrenceId)?.toDomain()

    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> =
        dao.observeLatestUnacknowledgedResult().map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) {
        require(occurrenceId.isNotBlank()) { "Arrival occurrence ID must not be blank." }
        dao.acknowledgeThrough(occurrenceId, acknowledgedAtEpochMillis)
    }

    override suspend fun getGrowth(): ArrivalGrowth = ArrivalGrowth(dao.getSuccessfulDayCount())

    override fun observeGrowth(): Flow<ArrivalGrowth> =
        dao.observeSuccessfulDayCount().map(::ArrivalGrowth).distinctUntilChanged()
}
