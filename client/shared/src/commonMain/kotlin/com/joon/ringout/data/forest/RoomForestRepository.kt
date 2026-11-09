package com.joon.ringout.data.forest

import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomForestRepository(private val dao: ForestDao) : ForestRepository {
    override suspend fun getForest(): ForestState = dao.getSnapshot().toDomain()

    override fun observeForest(): Flow<ForestState> = dao.observeSnapshot().map { it.toDomain() }.distinctUntilChanged()

    override suspend fun completeOnboarding(completedAtEpochMillis: Long) {
        dao.completeOnboarding(completedAtEpochMillis)
    }

    override suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long) {
        require(growthCycle in 1..ForestGrowth.MaxTrees)
        dao.acknowledgeTree(growthCycle, acknowledgedAtEpochMillis)
    }
}

private fun List<ForestSnapshotRow>.toDomain(): ForestState {
    val state = checkNotNull(firstOrNull()) { "Forest state must be initialized when the database opens." }
    return ForestState(
        growth = ForestGrowth(state.successfulDayCount),
        trees = mapNotNull { it.tree?.toDomain() },
        onboardingCompletedAtEpochMillis = state.onboardingCompletedAtEpochMillis,
    )
}
