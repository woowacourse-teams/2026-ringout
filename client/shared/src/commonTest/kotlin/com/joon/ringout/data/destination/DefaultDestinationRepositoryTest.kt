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

    @Test
    fun `비로그인 상태에서는 원격 API를 호출하지 않고 로컬 목적지 별명을 수정한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = false)
        val repository = DefaultDestinationRepository(local, remote)
        val saved = local.save(destination())

        val updated = repository.updateName(saved.id, "회사")

        assertTrue(updated)
        assertEquals("회사", local.observeAll().first().single().name)
        assertEquals(1, local.updateNameCallCount)
        assertEquals(0, remote.updateNameCallCount)
    }

    @Test
    fun `비로그인 상태에서는 원격 API를 호출하지 않고 로컬 목적지를 삭제한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = false)
        val repository = DefaultDestinationRepository(local, remote)
        val saved = local.save(destination())

        val deleted = repository.delete(saved.id)

        assertTrue(deleted)
        assertTrue(local.observeAll().first().isEmpty())
        assertEquals(1, local.deleteCallCount)
        assertEquals(0, remote.deleteCallCount)
    }

    @Test
    fun `로그인 상태에서는 목적지 별명 수정 API를 호출한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = true)
        val repository = DefaultDestinationRepository(local, remote)

        val updated = repository.updateName(id = 101L, name = "회사")

        assertTrue(updated)
        assertEquals(0, local.updateNameCallCount)
        assertEquals(1, remote.updateNameCallCount)
    }

    @Test
    fun `로그인 상태에서는 목적지 삭제 API를 호출한다`() = runTest {
        val local = FakeDestinationDataSource()
        val remote = FakeDestinationRemoteDataSource(hasAccessToken = true)
        val repository = DefaultDestinationRepository(local, remote)

        val deleted = repository.delete(id = 101L)

        assertTrue(deleted)
        assertEquals(0, local.deleteCallCount)
        assertEquals(1, remote.deleteCallCount)
    }
}

private class FakeDestinationDataSource : DestinationDataSource {
    private val destinations = MutableStateFlow(emptyList<SavedDestination>())
    var saveCallCount = 0
        private set
    var updateNameCallCount = 0
        private set
    var deleteCallCount = 0
        private set

    override fun observeAll(): Flow<List<SavedDestination>> = destinations

    override suspend fun save(destination: SavedDestination): SavedDestination {
        saveCallCount += 1
        return destination.copy(id = 1L).also { saved ->
            destinations.value = destinations.value + saved
        }
    }

    override suspend fun updateName(id: Long, name: String): Boolean {
        updateNameCallCount += 1
        val destination = destinations.value.firstOrNull { it.id == id } ?: return false
        destinations.value = destinations.value.map {
            if (it.id == id) destination.copy(name = name) else it
        }
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        deleteCallCount += 1
        val containsDestination = destinations.value.any { it.id == id }
        destinations.value = destinations.value.filterNot { it.id == id }
        return containsDestination
    }
}

private class FakeDestinationRemoteDataSource(
    private val hasAccessToken: Boolean,
) : DestinationRemoteDataSource {
    var createCallCount = 0
        private set
    var updateNameCallCount = 0
        private set
    var deleteCallCount = 0
        private set

    override suspend fun hasAccessToken(): Boolean = hasAccessToken

    override suspend fun fetchAll(): List<SavedDestination> = emptyList()

    override suspend fun sync(destinations: List<SavedDestination>): List<SavedDestination> = destinations

    override suspend fun create(destination: SavedDestination): SavedDestination {
        createCallCount += 1
        return destination.copy(id = 101L)
    }

    override suspend fun updateName(id: Long, name: String): Boolean {
        updateNameCallCount += 1
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        deleteCallCount += 1
        return true
    }
}

private fun destination(): SavedDestination = SavedDestination(
    name = "집",
    address = "서울특별시 강남구",
    latitude = 37.4979,
    longitude = 127.0276,
)
