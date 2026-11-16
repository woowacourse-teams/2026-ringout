package com.joon.ringout.domain.forest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ForestRepository {
    /** Explicitly refreshes the server snapshot; ordinary reads never initiate a request. */
    suspend fun refreshForest(): ForestState = getForest()

    suspend fun getForest(): ForestState

    fun observeForest(): Flow<ForestState>

    /** Current account's successfully delivered ARRIVED events, including an initial baseline. */
    fun observeSuccessfulArrivalSyncs(): Flow<Int> = flowOf(0)

    suspend fun completeOnboarding(completedAtEpochMillis: Long)

    fun observeOnboardingSyncState(): Flow<ForestOnboardingSyncState> = flowOf(ForestOnboardingSyncState.Idle)

    suspend fun retryOnboardingSync() = Unit

    /** Acknowledges only this reveal; other unacknowledged trees remain pending. */
    suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long)
}
