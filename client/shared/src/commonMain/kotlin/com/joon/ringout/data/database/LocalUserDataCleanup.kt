package com.joon.ringout.data.database

/** Removes data owned by the current user while preserving installation-level state. */
fun interface LocalUserDataCleanup {
    suspend fun clear()
}

class RoomLocalUserDataCleanup(
    private val dao: LocalUserDataCleanupDao,
) : LocalUserDataCleanup {
    override suspend fun clear() = dao.clear()
}
