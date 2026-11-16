package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreForestLocalProgressStoreTest {
    @Test
    fun `계정별 확인 상태를 저장하고 저장소를 다시 만들어도 첫 시각을 보존한다`() = runTest {
        val dataStore = MemoryPreferences()
        val store = DataStoreForestLocalProgressStore(dataStore)
        val tree = ForestTree(1, ForestTreeKind.Pine, 7, 50)
        store.completeOnboarding("1", 100)
        store.acknowledgeTree("1", tree, 200)
        store.completeOnboarding("1", 300)
        store.acknowledgeTree("1", tree, 400)

        val restored = DataStoreForestLocalProgressStore(dataStore)
        assertEquals(100L, restored.read("1").onboardingCompletedAtEpochMillis)
        assertEquals(200L, restored.read("1").acknowledgedTrees[tree.localProgressKey()])
        assertNull(restored.read("2").onboardingCompletedAtEpochMillis)
        assertTrue(restored.read("2").acknowledgedTrees.isEmpty())
    }

    @Test
    fun `다른 나무의 확인 상태는 각각 독립적으로 저장한다`() = runTest {
        val store = DataStoreForestLocalProgressStore(MemoryPreferences())
        val first = ForestTree(1, ForestTreeKind.Pine, 7, 50)
        val second = ForestTree(2, ForestTreeKind.Oak, 12, 150)
        store.acknowledgeTree("1", first, 200)
        store.acknowledgeTree("1", second, 300)
        assertEquals(mapOf(first.localProgressKey() to 200L, second.localProgressKey() to 300L),
            store.read("1").acknowledgedTrees)
    }
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}
