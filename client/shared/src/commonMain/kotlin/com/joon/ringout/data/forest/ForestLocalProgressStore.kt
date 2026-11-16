package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Pending, device-local UI confirmations until the corresponding write APIs are connected. */
interface ForestLocalProgressStore {
    suspend fun read(ownerAccountId: String): ForestLocalProgress
    suspend fun completeOnboarding(ownerAccountId: String, completedAtEpochMillis: Long)
    suspend fun acknowledgeTree(ownerAccountId: String, tree: ForestTree, acknowledgedAtEpochMillis: Long)
}

@Serializable
data class ForestLocalProgress(
    val onboardingCompletedAtEpochMillis: Long? = null,
    val acknowledgedTrees: Map<String, Long> = emptyMap(),
)

class DataStoreForestLocalProgressStore(
    private val dataStore: DataStore<Preferences>,
) : ForestLocalProgressStore {
    override suspend fun read(ownerAccountId: String): ForestLocalProgress =
        decode(dataStore.data.first()[key(ownerAccountId)])

    override suspend fun completeOnboarding(ownerAccountId: String, completedAtEpochMillis: Long) {
        update(ownerAccountId) { progress ->
            progress.copy(onboardingCompletedAtEpochMillis =
                progress.onboardingCompletedAtEpochMillis ?: completedAtEpochMillis)
        }
    }

    override suspend fun acknowledgeTree(ownerAccountId: String, tree: ForestTree, acknowledgedAtEpochMillis: Long) {
        update(ownerAccountId) { progress ->
            val identity = tree.localProgressKey()
            progress.copy(acknowledgedTrees = progress.acknowledgedTrees +
                (identity to (progress.acknowledgedTrees[identity] ?: acknowledgedAtEpochMillis)))
        }
    }

    private suspend fun update(ownerAccountId: String, transform: (ForestLocalProgress) -> ForestLocalProgress) {
        val key = key(ownerAccountId)
        dataStore.edit { preferences ->
            preferences[key] = ApiJson.encodeToString(transform(decode(preferences[key])))
        }
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
