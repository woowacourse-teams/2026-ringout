package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultDestinationRepositoryTest {
    @Test
    fun `로그인 상태와 관계없이 목적지를 Room에 먼저 저장한다`() = runTest {
        val local = FakeDestinationDataSource()
        val repository = DefaultDestinationRepository(local)

        val saved = repository.save(destination())

        assertEquals(1L, saved.id)
        assertEquals(1, local.saveCallCount)
        assertEquals(null, local.lastOwnerAccountId)
    }

    @Test
    fun `목적지 화면에서 관찰하는 목록은 로컬 데이터 소스이다`() = runTest {
        val local = FakeDestinationDataSource()
        val repository = DefaultDestinationRepository(local)
        val saved = repository.save(destination())

        assertEquals(listOf(saved), repository.observeAll().first())
        assertEquals(1, local.observeAllCallCount)
    }

    @Test
    fun `삭제 결과는 Room의 로컬 행을 기준으로 반환한다`() = runTest {
        val local = FakeDestinationDataSource()
        val repository = DefaultDestinationRepository(local)
        val saved = repository.save(destination())

        assertTrue(repository.delete(saved.id))
        assertTrue(repository.observeAll().first().isEmpty())
        assertEquals(1, local.deleteCallCount)
    }

    @Test
    fun `로그인 계정에 귀속된 목록만 구독한다`() = runTest {
        val local = FakeDestinationDataSource()
        val accountProvider = DestinationAccountProvider(
            authSession = com.joon.ringout.domain.auth.AuthSession().apply { markAuthenticated() },
            currentVerifiedAccountId = { "account-42" },
        )
        val repository = DefaultDestinationRepository(local, accountProvider)

        repository.observeAll().first()

        assertEquals("account-42", local.lastObservedAccountId)
    }
}

private class FakeDestinationDataSource : DestinationDataSource {
    private val destinations = MutableStateFlow(emptyList<SavedDestination>())
    var observeAllCallCount = 0
        private set
    var saveCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var lastObservedAccountId: String? = null
        private set
    var lastOwnerAccountId: String? = null
        private set

    override fun observeAll(ownerAccountId: String?): Flow<List<SavedDestination>> {
        observeAllCallCount += 1
        lastObservedAccountId = ownerAccountId
        return destinations
    }

    override suspend fun save(destination: SavedDestination, ownerAccountId: String?): SavedDestination {
        saveCallCount += 1
        lastOwnerAccountId = ownerAccountId
        return destination.copy(id = 1L).also { saved ->
            destinations.value = destinations.value + saved
        }
    }

    override suspend fun delete(id: Long, ownerAccountId: String?): Boolean {
        deleteCallCount += 1
        lastOwnerAccountId = ownerAccountId
        val containsDestination = destinations.value.any { it.id == id }
        destinations.value = destinations.value.filterNot { it.id == id }
        return containsDestination
    }
}

private fun destination(): SavedDestination = SavedDestination(
    label = "집",
    latitude = 37.4979,
    longitude = 127.0276,
)
