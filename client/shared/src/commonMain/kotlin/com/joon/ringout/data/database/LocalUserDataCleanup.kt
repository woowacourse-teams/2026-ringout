package com.joon.ringout.data.database

import com.joon.ringout.data.destination.DestinationSyncSessionGate

/** Removes data owned by the current user while preserving installation-level state. */
fun interface LocalUserDataCleanup {
    suspend fun clear()
}

class RoomLocalUserDataCleanup(
    private val dao: LocalUserDataCleanupDao,
    private val destinationSessionGate: DestinationSyncSessionGate = DestinationSyncSessionGate(),
    private val currentSessionIdentity: () -> Any? = { null },
) : LocalUserDataCleanup {
    override suspend fun clear() = destinationSessionGate.clearForLogout(currentSessionIdentity()) { dao.clear() }
}
