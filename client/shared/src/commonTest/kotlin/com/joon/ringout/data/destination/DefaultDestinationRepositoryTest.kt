package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDestinationRepositoryTest {
    @Test
    fun `비로그인 상태에서는 원격 API를 호출하지 않고 Room에 목적지를 저장한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = false)
        val repository = DefaultDestinationRepository(local, remote)

        val saved = repository.save(destination())

        assertEquals(1L, saved.id)
        assertEquals(1, local.saveCallCount)
        assertEquals(0, remote.createCallCount)
    }

    @Test
    fun `로그인 상태에서는 목적지 저장 API를 호출한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = true)
        val repository = DefaultDestinationRepository(local, remote)

        val saved = repository.save(destination())

        assertEquals(101L, saved.id)
        assertEquals(0, local.saveCallCount)
        assertEquals(1, remote.createCallCount)
    }
}

private class FakeDestinationDataSource : DestinationDataSource {
    private val destinations = MutableStateFlow(emptyList<SavedDestination>())
    var saveCallCount = 0
        private set

    override fun observeAll(): Flow<List<SavedDestination>> = destinations

    override suspend fun save(destination: SavedDestination): SavedDestination {
        saveCallCount += 1
        return destination.copy(id = 1L).also { saved ->
            destinations.value = destinations.value + saved
        }
    }

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private class FakeDestinationRemoteDataSource(
    private val hasAccessToken: Boolean,
) : DestinationRemoteDataSource {
    var createCallCount = 0
        private set

    override suspend fun hasAccessToken(): Boolean = hasAccessToken

    override suspend fun fetchAll(): List<SavedDestination> = emptyList()

    override suspend fun sync(destinations: List<SavedDestination>): List<SavedDestination> = destinations

    override suspend fun create(destination: SavedDestination): SavedDestination {
        createCallCount += 1
        return destination.copy(id = 101L)
    }

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private fun destination(): SavedDestination = SavedDestination(
    name = "집",
    address = "서울특별시 강남구",
    latitude = 37.4979,
    longitude = 127.0276,
)
