package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Account-owned progress and durable delivery state; legacy tree confirmations remain local. */
interface ForestLocalProgressStore {
    suspend fun read(ownerAccountId: String): ForestLocalProgress
    fun observe(ownerAccountId: String): Flow<ForestLocalProgress>
    suspend fun update(
        ownerAccountId: String,
        transform: (ForestLocalProgress) -> ForestLocalProgress,
    ): ForestLocalProgress
    suspend fun completeOnboarding(ownerAccountId: String, completedAtEpochMillis: Long)
    suspend fun acknowledgeTree(ownerAccountId: String, tree: ForestTree, acknowledgedAtEpochMillis: Long)
}

@Serializable
enum class ForestOnboardingDeliveryStatus { Pending, Synced, Blocked }

@Serializable
enum class ForestTreeAcknowledgementDeliveryStatus { Pending, NeedsOnboarding, Blocked, Synced }

@Serializable
data class ForestTreeAcknowledgementDelivery(
    val growthCycle: Int,
    val kind: String,
    val slotIndex: Int,
    val plantedAtEpochMillis: Long,
    val acknowledgedAtEpochMillis: Long,
    val status: ForestTreeAcknowledgementDeliveryStatus = ForestTreeAcknowledgementDeliveryStatus.Pending,
    val attemptCount: Int = 0,
    val nextRetryAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
    val onboardingRepairCompleted: Boolean = false,
) {
    fun localProgressKey(): String = "$growthCycle:$kind:$slotIndex:$plantedAtEpochMillis"

    fun matches(tree: ForestTree): Boolean = localProgressKey() == tree.localProgressKey()
}

@Serializable
data class ForestLocalProgress(
    val onboardingCompletedAtEpochMillis: Long? = null,
    val acknowledgedTrees: Map<String, Long> = emptyMap(),
    // Existing account-keyed records have no delivery fields and are restored as pending.
    val onboardingSyncStatus: ForestOnboardingDeliveryStatus = ForestOnboardingDeliveryStatus.Pending,
    val onboardingAttemptCount: Int = 0,
    val onboardingNextRetryAtEpochMillis: Long? = null,
    val onboardingLastErrorCode: String? = null,
    // Absence of tree metadata means a legacy local confirmation, never an implicit pending PATCH.
    val treeAcknowledgementDeliveries: Map<String, ForestTreeAcknowledgementDelivery> = emptyMap(),
    val arrivalPlantingCheckpoints: Map<String, ForestArrivalPlantingCheckpoint> = emptyMap(),
    val treePlantingDeliveries: Map<Int, ForestTreePlantingDelivery> = emptyMap(),
)

class DataStoreForestLocalProgressStore(
    private val dataStore: DataStore<Preferences>,
) : ForestLocalProgressStore {
    override suspend fun read(ownerAccountId: String): ForestLocalProgress =
        decode(dataStore.data.first()[key(ownerAccountId)])

    override fun observe(ownerAccountId: String): Flow<ForestLocalProgress> =
        dataStore.data.map { decode(it[key(ownerAccountId)]) }.distinctUntilChanged()

    override suspend fun completeOnboarding(ownerAccountId: String, completedAtEpochMillis: Long) {
        update(ownerAccountId) { progress ->
            if (progress.onboardingCompletedAtEpochMillis != null) progress
            else progress.copy(
                onboardingCompletedAtEpochMillis = completedAtEpochMillis,
                onboardingSyncStatus = ForestOnboardingDeliveryStatus.Pending,
                onboardingAttemptCount = 0,
                onboardingNextRetryAtEpochMillis = null,
                onboardingLastErrorCode = null,
            )
        }
    }

    override suspend fun acknowledgeTree(ownerAccountId: String, tree: ForestTree, acknowledgedAtEpochMillis: Long) {
        update(ownerAccountId) { progress ->
            val identity = tree.localProgressKey()
            if (identity in progress.acknowledgedTrees) progress
            else {
                val delivery = ForestTreeAcknowledgementDelivery(
                    growthCycle = tree.growthCycle,
                    kind = tree.kind.persistedValue,
                    slotIndex = tree.slotIndex,
                    plantedAtEpochMillis = tree.plantedAtEpochMillis,
                    acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
                )
                progress.copy(
                    acknowledgedTrees = progress.acknowledgedTrees + (identity to acknowledgedAtEpochMillis),
                    treeAcknowledgementDeliveries = progress.treeAcknowledgementDeliveries + (identity to delivery),
                )
            }
        }
    }

    override suspend fun update(
        ownerAccountId: String,
        transform: (ForestLocalProgress) -> ForestLocalProgress,
    ): ForestLocalProgress {
        val key = key(ownerAccountId)
        val saved = dataStore.edit { preferences ->
            preferences[key] = ApiJson.encodeToString(transform(decode(preferences[key])))
        }
        return decode(saved[key])
    }

    private fun key(ownerAccountId: String) = stringPreferencesKey("forest_pending_progress_$ownerAccountId")
    private fun decode(value: String?): ForestLocalProgress =
        value?.let { ApiJson.decodeFromString<ForestLocalProgress>(it) } ?: ForestLocalProgress()
}

internal fun ForestTree.localProgressKey(): String =
    "$growthCycle:${kind.persistedValue}:$slotIndex:$plantedAtEpochMillis"

internal fun ForestState.withLocalProgress(progress: ForestLocalProgress): ForestState = copy(
    onboardingCompletedAtEpochMillis = onboardingCompletedAtEpochMillis ?: progress.onboardingCompletedAtEpochMillis,
    trees = trees.map { tree ->
        tree.copy(acknowledgedAtEpochMillis = tree.acknowledgedAtEpochMillis ?: progress.acknowledgedTrees[tree.localProgressKey()])
    },
)
