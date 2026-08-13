@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.destination

import androidx.room3.Room
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoomDestinationDataSourceTest {
    @Test
    fun insertsMultipleDestinationsObservesCreationOrderUpdatesAndDeletes() = runBlocking {
        withDatabase { database ->
            val repository = DefaultDestinationRepository(
                RoomDestinationDataSource(database.destinationDao()),
            )

            val first = repository.save(destination(name = "회사", address = ""))
            val second = repository.save(destination(name = "집"))

            assertNotEquals(0L, first.id)
            assertNotEquals(0L, second.id)
            assertTrue(second.id > first.id)
            assertEquals(
                listOf(first, second),
                repository.observeAll().first(),
            )

            val updatedFirst = repository.save(
                first.copy(
                    name = "새 회사",
                    latitude = 37.5700,
                    longitude = 126.9800,
                ),
            )
            assertEquals(first.id, updatedFirst.id)
            assertEquals(
                listOf(updatedFirst, second),
                repository.observeAll().first(),
            )

            assertTrue(repository.delete(second.id))
            assertFalse(repository.delete(second.id))
            assertEquals(listOf(updatedFirst), repository.observeAll().first())
        }
    }

    @Test
    fun failsAnUpdateWhenTheStableIdDoesNotExist() = runBlocking {
        withDatabase { database ->
            val repository = DefaultDestinationRepository(
                RoomDestinationDataSource(database.destinationDao()),
            )

            assertFailsWith<IllegalStateException> {
                repository.save(destination(id = 999))
            }
            assertFailsWith<IllegalArgumentException> {
                repository.delete(-1)
            }
        }
    }

    @Test
    fun updatesOnlyTheNameAndReturnsWhetherTheDestinationExists() = runBlocking {
        withDatabase { database ->
            val repository = DefaultDestinationRepository(
                RoomDestinationDataSource(database.destinationDao()),
            )
            val destination = repository.save(destination())

            assertTrue(repository.updateName(destination.id, "새 회사"))
            assertEquals(
                destination.copy(name = "새 회사"),
                repository.observeAll().first().single(),
            )
            assertFalse(repository.updateName(999, "없는 목적지"))
            assertEquals(
                destination.copy(name = "새 회사"),
                repository.observeAll().first().single(),
            )
        }
    }

    @Test
    fun keepsFullDestinationUpdatesAndRejectsInvalidNameUpdateArguments() = runBlocking {
        withDatabase { database ->
            val repository = DefaultDestinationRepository(
                RoomDestinationDataSource(database.destinationDao()),
            )
            val destination = repository.save(destination())
            val fullyUpdated = destination.copy(
                name = "새 회사",
                address = "서울특별시 종로구 세종대로 1",
                latitude = 37.5759,
                longitude = 126.9768,
            )

            assertEquals(fullyUpdated, repository.save(fullyUpdated))
            assertEquals(fullyUpdated, repository.observeAll().first().single())
            assertFailsWith<IllegalArgumentException> {
                repository.updateName(-1, "회사")
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateName(destination.id, "   ")
            }
        }
    }

    @Test
    fun keepsSavedDestinationsAfterTheDatabaseIsReopened() = runBlocking {
        val databasePath = temporaryDatabasePath()
        try {
            val savedDestination = buildRingoutDatabase(
                Room.databaseBuilder<RingoutDatabase>(name = databasePath),
            ).let { database ->
                try {
                    DefaultDestinationRepository(
                        RoomDestinationDataSource(database.destinationDao()),
                    ).save(destination(name = "집"))
                } finally {
                    database.close()
                }
            }

            val reopenedDatabase = buildRingoutDatabase(
                Room.databaseBuilder<RingoutDatabase>(name = databasePath),
            )
            try {
                val reopenedRepository = DefaultDestinationRepository(
                    RoomDestinationDataSource(reopenedDatabase.destinationDao()),
                )
                assertEquals(
                    listOf(savedDestination),
                    reopenedRepository.observeAll().first(),
                )
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            deleteDatabaseFiles(databasePath)
        }
    }

    private suspend fun withDatabase(
        block: suspend (RingoutDatabase) -> Unit,
    ) {
        val database = buildRingoutDatabase(Room.inMemoryDatabaseBuilder<RingoutDatabase>())
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun destination(
        id: Long = 0,
        name: String = "회사",
        address: String = "서울특별시 중구 세종대로 110",
    ) = SavedDestination(
        id = id,
        name = name,
        address = address,
        latitude = 37.5665,
        longitude = 126.978,
    )

    private fun temporaryDatabasePath(): String =
        NSTemporaryDirectory() + "ringout-destination-${NSUUID().UUIDString}.db"

    private fun deleteDatabaseFiles(databasePath: String) {
        listOf(databasePath, "$databasePath-shm", "$databasePath-wal").forEach { path ->
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }
    }
}
