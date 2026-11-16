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

/** Account-owned onboarding delivery state and device-local tree confirmations. */
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
data class ForestLocalProgress(
    val onboardingCompletedAtEpochMillis: Long? = null,
    val acknowledgedTrees: Map<String, Long> = emptyMap(),
    // Existing account-keyed records have no delivery fields and are restored as pending.
    val onboardingSyncStatus: ForestOnboardingDeliveryStatus = ForestOnboardingDeliveryStatus.Pending,
    val onboardingAttemptCount: Int = 0,
    val onboardingNextRetryAtEpochMillis: Long? = null,
    val onboardingLastErrorCode: String? = null,
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
            progress.copy(acknowledgedTrees = progress.acknowledgedTrees +
                (identity to (progress.acknowledgedTrees[identity] ?: acknowledgedAtEpochMillis)))
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
