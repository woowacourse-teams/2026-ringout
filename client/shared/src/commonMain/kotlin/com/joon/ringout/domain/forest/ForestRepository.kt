package com.joon.ringout.domain.forest

import kotlinx.coroutines.flow.Flow

interface ForestRepository {
    suspend fun getForest(): ForestState

    fun observeForest(): Flow<ForestState>

    suspend fun completeOnboarding(completedAtEpochMillis: Long)

    /** Acknowledges only this reveal; other unacknowledged trees remain pending. */
    suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long)
}
