package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
        assertEquals(ForestOnboardingDeliveryStatus.Pending, restored.read("1").onboardingSyncStatus)
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

    @Test
    fun `동기화 필드가 없는 기존 기록을 미전송 상태로 읽고 나무 확인을 유지한다`() = runTest {
        val dataStore = MemoryPreferences()
        dataStore.edit {
            it[stringPreferencesKey("forest_pending_progress_1")] =
                """{"onboardingCompletedAtEpochMillis":100,"acknowledgedTrees":{"1:pine:7:50":200}}"""
        }
        val store = DataStoreForestLocalProgressStore(dataStore)
        val migrated = store.read("1")
        assertEquals(100L, migrated.onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingDeliveryStatus.Pending, migrated.onboardingSyncStatus)
        assertEquals(0, migrated.onboardingAttemptCount)
        assertNull(migrated.onboardingNextRetryAtEpochMillis)
        assertNull(migrated.onboardingLastErrorCode)
        assertEquals(mapOf("1:pine:7:50" to 200L), migrated.acknowledgedTrees)
        assertEquals(migrated, store.observe("1").first())
    }

    @Test
    fun `재시도 정보는 저장소 재생성 후에도 계정별로 유지된다`() = runTest {
        val dataStore = MemoryPreferences()
        val store = DataStoreForestLocalProgressStore(dataStore)
        store.completeOnboarding("1", 100)
        val saved = store.update("1") {
            it.copy(
                onboardingAttemptCount = 4,
                onboardingNextRetryAtEpochMillis = 45_000,
                onboardingLastErrorCode = "NETWORK_ERROR",
            )
        }

        val restored = DataStoreForestLocalProgressStore(dataStore)
        assertEquals(saved, restored.read("1"))
        assertEquals(4, saved.onboardingAttemptCount)
        assertEquals(45_000L, saved.onboardingNextRetryAtEpochMillis)
        assertEquals("NETWORK_ERROR", saved.onboardingLastErrorCode)
        assertEquals(ForestLocalProgress(), restored.read("2"))
    }

    @Test
    fun `완료 버튼을 다시 눌러도 서버 확정 또는 전송 중단 상태와 시각을 바꾸지 않는다`() = runTest {
        val store = DataStoreForestLocalProgressStore(MemoryPreferences())
        for (status in listOf(ForestOnboardingDeliveryStatus.Synced, ForestOnboardingDeliveryStatus.Blocked)) {
            val saved = store.update("1") {
                it.copy(
                    onboardingCompletedAtEpochMillis = 80,
                    onboardingSyncStatus = status,
                    onboardingAttemptCount = 3,
                    onboardingLastErrorCode = if (status == ForestOnboardingDeliveryStatus.Blocked) "FOREST400" else null,
                )
            }
            store.completeOnboarding("1", 500)
            assertEquals(saved, store.read("1"))
        }
    }

    @Test
    fun `서버 완료 시각을 확정해도 나무 확인과 다른 계정의 미전송 기록은 유지된다`() = runTest {
        val dataStore = MemoryPreferences()
        val store = DataStoreForestLocalProgressStore(dataStore)
        val tree = ForestTree(1, ForestTreeKind.Pine, 7, 50)
        store.completeOnboarding("1", 100)
        store.completeOnboarding("2", 300)
        store.acknowledgeTree("1", tree, 200)
        store.update("1") {
            it.copy(
                onboardingCompletedAtEpochMillis = 80,
                onboardingSyncStatus = ForestOnboardingDeliveryStatus.Synced,
                onboardingAttemptCount = 0,
                onboardingNextRetryAtEpochMillis = null,
                onboardingLastErrorCode = null,
            )
        }

        val restored = DataStoreForestLocalProgressStore(dataStore)
        assertEquals(80L, restored.read("1").onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, restored.read("1").onboardingSyncStatus)
        assertEquals(mapOf(tree.localProgressKey() to 200L), restored.read("1").acknowledgedTrees)
        assertEquals(300L, restored.read("2").onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingDeliveryStatus.Pending, restored.read("2").onboardingSyncStatus)
    }

    @Test
    fun `온보딩 관찰은 해당 계정의 미전송 중단 확정 변경만 전달한다`() = runTest {
        val store = DataStoreForestLocalProgressStore(MemoryPreferences())
        val observed = mutableListOf<ForestLocalProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.observe("1").toList(observed)
        }
        assertEquals(listOf(ForestLocalProgress()), observed)
        store.completeOnboarding("2", 100)
        assertEquals(1, observed.size)
        store.completeOnboarding("1", 200)
        store.update("1") {
            it.copy(onboardingSyncStatus = ForestOnboardingDeliveryStatus.Blocked, onboardingLastErrorCode = "FOREST400")
        }
        store.update("1") {
            it.copy(onboardingSyncStatus = ForestOnboardingDeliveryStatus.Synced, onboardingCompletedAtEpochMillis = 150,
                onboardingLastErrorCode = null)
        }
        assertEquals(listOf(null, 200L, 200L, 150L), observed.map { it.onboardingCompletedAtEpochMillis })
        assertEquals(listOf(ForestOnboardingDeliveryStatus.Pending, ForestOnboardingDeliveryStatus.Pending,
            ForestOnboardingDeliveryStatus.Blocked, ForestOnboardingDeliveryStatus.Synced), observed.map { it.onboardingSyncStatus })
    }

    @Test
    fun `동시에 재시도 정보와 나무 확인을 저장해도 서로의 기록을 덮어쓰지 않는다`() = runTest {
        val store = DataStoreForestLocalProgressStore(MemoryPreferences())
        store.completeOnboarding("1", 100)
        val trees = (1..8).map { ForestTree(it, ForestTreeKind.Pine, it - 1, it * 50L) }
        coroutineScope {
            repeat(20) {
                launch { store.update("1") { it.copy(onboardingAttemptCount = it.onboardingAttemptCount + 1) } }
            }
            trees.forEach { tree -> launch { store.acknowledgeTree("1", tree, 200) } }
        }
        val saved = store.read("1")
        assertEquals(20, saved.onboardingAttemptCount)
        assertEquals(100L, saved.onboardingCompletedAtEpochMillis)
        assertEquals(trees.associate { it.localProgressKey() to 200L }, saved.acknowledgedTrees)
    }

    @Test
    fun `영구 저장에 실패하면 온보딩 완료나 미전송 시각을 만들지 않는다`() = runTest {
        val dataStore = MemoryPreferences()
        val store = DataStoreForestLocalProgressStore(dataStore)
        dataStore.failUpdates = true
        assertFailsWith<IllegalStateException> { store.completeOnboarding("1", 100) }
        assertEquals(ForestLocalProgress(), store.read("1"))
        dataStore.failUpdates = false
        store.completeOnboarding("1", 200)
        assertEquals(200L, store.read("1").onboardingCompletedAtEpochMillis)
    }
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    var failUpdates = false
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        check(!failUpdates) { "storage failure" }
        transform(data.value).also { data.value = it }
    }
}
