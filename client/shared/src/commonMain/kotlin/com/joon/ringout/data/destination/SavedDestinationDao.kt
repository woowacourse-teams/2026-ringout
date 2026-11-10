package com.joon.ringout.data.destination

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
abstract class SavedDestinationDao {
    @Query("""
        SELECT * FROM saved_destinations
        WHERE (:ownerAccountId IS NULL AND owner_account_id IS NULL)
           OR (:ownerAccountId IS NOT NULL AND owner_account_id = :ownerAccountId)
        ORDER BY id ASC
    """)
    abstract fun observeAll(ownerAccountId: String?): Flow<List<SavedDestinationEntity>>

    @Query("SELECT * FROM saved_destinations WHERE id = :id LIMIT 1")
    protected abstract suspend fun getById(id: Long): SavedDestinationEntity?

    @Query("SELECT * FROM saved_destinations WHERE owner_account_id = :ownerAccountId ORDER BY id ASC")
    protected abstract suspend fun getAccountDestinations(ownerAccountId: String): List<SavedDestinationEntity>

    @Query("""
        SELECT * FROM saved_destinations
        WHERE owner_account_id = :ownerAccountId AND remote_destination_id = :remoteDestinationId
        LIMIT 1
    """)
    protected abstract suspend fun getByRemoteId(
        ownerAccountId: String,
        remoteDestinationId: String,
    ): SavedDestinationEntity?

    @Insert
    protected abstract suspend fun insertDestination(destination: SavedDestinationEntity): Long

    @Update
    protected abstract suspend fun updateDestination(destination: SavedDestinationEntity)

    @Query("DELETE FROM saved_destinations WHERE id = :id")
    protected abstract suspend fun deleteDestinationRow(id: Long): Int

    @Insert
    protected abstract suspend fun insertOutbox(operation: DestinationOutboxEntity): Long

    @Query("SELECT * FROM destination_outbox WHERE id = :id AND owner_account_id = :ownerAccountId LIMIT 1")
    protected abstract suspend fun getOutboxById(id: Long, ownerAccountId: String): DestinationOutboxEntity?

    @Query("""
        SELECT * FROM destination_outbox
        WHERE owner_account_id = :ownerAccountId AND remote_destination_id = :remoteDestinationId
        ORDER BY id ASC
    """)
    protected abstract suspend fun getOutboxForRemoteId(
        ownerAccountId: String,
        remoteDestinationId: String,
    ): List<DestinationOutboxEntity>

    @Query("DELETE FROM destination_outbox WHERE id = :id AND owner_account_id = :ownerAccountId")
    protected abstract suspend fun deleteOutbox(id: Long, ownerAccountId: String): Int

    @Update
    protected abstract suspend fun updateOutbox(operation: DestinationOutboxEntity)

    @Query("SELECT * FROM destination_outbox ORDER BY id ASC")
    abstract fun observeOutbox(): Flow<List<DestinationOutboxEntity>>

    @Query("""
        SELECT o.* FROM destination_outbox o
        WHERE o.owner_account_id = :ownerAccountId
          AND o.state = 'PENDING'
          AND (o.next_attempt_at IS NULL OR o.next_attempt_at <= :nowEpochMillis)
          AND NOT EXISTS (
              SELECT 1 FROM destination_outbox earlier
              WHERE earlier.owner_account_id = o.owner_account_id
                AND earlier.remote_destination_id = o.remote_destination_id
                AND earlier.id < o.id
          )
        ORDER BY o.id ASC
    """)
    abstract suspend fun getDueOutbox(
        ownerAccountId: String,
        nowEpochMillis: Long,
    ): List<DestinationOutboxEntity>

    @Query("""
        SELECT MIN(o.next_attempt_at) FROM destination_outbox o
        WHERE o.owner_account_id = :ownerAccountId
          AND o.state = 'PENDING'
          AND o.next_attempt_at IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM destination_outbox earlier
              WHERE earlier.owner_account_id = o.owner_account_id
                AND earlier.remote_destination_id = o.remote_destination_id
                AND earlier.id < o.id
          )
    """)
    abstract suspend fun getNextOutboxRetryAt(ownerAccountId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putSyncState(state: DestinationSyncStateEntity)

    @Query("SELECT * FROM destination_sync_state WHERE owner_account_id = :ownerAccountId LIMIT 1")
    abstract suspend fun getSyncState(ownerAccountId: String): DestinationSyncStateEntity?

    @Query("SELECT * FROM destination_sync_state ORDER BY owner_account_id")
    abstract fun observeSyncStates(): Flow<List<DestinationSyncStateEntity>>

    @Transaction
    open suspend fun saveNew(
        destination: SavedDestinationEntity,
        ownerAccountId: String?,
    ): SavedDestinationEntity {
        require(destination.id == 0L) { "목적지는 생성 후 수정할 수 없어요." }
        val withRemoteIdentity = destination.copy(
            ownerAccountId = ownerAccountId,
            remoteDestinationId = Uuid.random().toString(),
            destinationVersion = 1L,
        )
        val saved = withRemoteIdentity.copy(id = insertDestination(withRemoteIdentity))
        if (ownerAccountId != null) insertUpsert(saved, ownerAccountId)
        return saved
    }

    @Transaction
    open suspend fun deleteLocal(id: Long, currentAccountId: String?): Boolean {
        require(id >= 0L) { "Saved destination id must not be negative: $id" }
        var destination = getById(id) ?: return false
        if (destination.ownerAccountId != null && destination.ownerAccountId != currentAccountId) return false

        if (destination.ownerAccountId == null && currentAccountId != null) {
            destination = destination.copy(
                ownerAccountId = currentAccountId,
                remoteDestinationId = destination.remoteDestinationId ?: Uuid.random().toString(),
                destinationVersion = if (destination.remoteDestinationId == null) 1L else destination.destinationVersion,
            )
            updateDestination(destination)
            insertUpsert(destination, currentAccountId)
        }

        val owner = destination.ownerAccountId
        val remoteId = destination.remoteDestinationId
        if (owner != null && remoteId != null) {
            val operations = getOutboxForRemoteId(owner, remoteId)
            val unattemptedUpsert = operations.firstOrNull {
                it.operation == DestinationOutboxOperation.UPSERT &&
                    it.attemptCount == 0 && it.state == DestinationOutboxState.PENDING
            }
            if (unattemptedUpsert != null) {
                deleteOutbox(unattemptedUpsert.id, owner)
            } else if (operations.none { it.operation == DestinationOutboxOperation.DELETE }) {
                insertOutbox(
                    DestinationOutboxEntity(
                        ownerAccountId = owner,
                        localDestinationId = destination.id,
                        remoteDestinationId = remoteId,
                        operation = DestinationOutboxOperation.DELETE,
                    ),
                )
            }
        }
        return deleteDestinationRow(id) == 1
    }

    /** Associates guest destinations with the first authenticated account without guessing old server IDs. */
    @Transaction
    open suspend fun prepareAccount(ownerAccountId: String) {
        require(ownerAccountId.isNotBlank())
        val legacy = getUnownedDestinations()
        legacy.forEach { old ->
            val destination = old.copy(
                ownerAccountId = ownerAccountId,
                remoteDestinationId = old.remoteDestinationId ?: Uuid.random().toString(),
                destinationVersion = if (old.remoteDestinationId == null) 1L else old.destinationVersion,
            )
            updateDestination(destination)
            if (getOutboxForRemoteId(ownerAccountId, checkNotNull(destination.remoteDestinationId))
                    .none { it.operation == DestinationOutboxOperation.UPSERT }
            ) {
                insertUpsert(destination, ownerAccountId)
            }
        }
        if (getSyncState(ownerAccountId) == null) {
            putSyncState(DestinationSyncStateEntity(ownerAccountId = ownerAccountId))
        }
    }

    @Query("SELECT * FROM saved_destinations WHERE owner_account_id IS NULL ORDER BY id ASC")
    protected abstract suspend fun getUnownedDestinations(): List<SavedDestinationEntity>

    @Transaction
    open suspend fun applyRemoteSnapshot(
        ownerAccountId: String,
        remoteDestinations: List<RemoteSavedDestination>,
    ) {
        require(ownerAccountId.isNotBlank())
        require(remoteDestinations.map { it.id }.toSet().size == remoteDestinations.size) {
            "서버 목적지 응답에 중복 ID가 있습니다."
        }
        val localDestinations = getAccountDestinations(ownerAccountId)
        val serverIds = remoteDestinations.mapTo(mutableSetOf()) { it.id }
        val localByRemoteId = localDestinations.mapNotNull { local ->
            local.remoteDestinationId?.let { it to local }
        }.toMap()

        remoteDestinations.forEach { remote ->
            val queued = getOutboxForRemoteId(ownerAccountId, remote.id).isNotEmpty()
            if (queued) return@forEach
            val existing = localByRemoteId[remote.id]
            if (existing == null) {
                insertDestination(remote.toEntity(ownerAccountId))
            } else {
                updateDestination(
                    existing.copy(
                        label = remote.alias,
                        latitude = remote.latitude,
                        longitude = remote.longitude,
                        destinationVersion = remote.version,
                    ),
                )
            }
        }

        localDestinations.forEach { local ->
            val remoteId = local.remoteDestinationId ?: return@forEach
            if (remoteId !in serverIds && getOutboxForRemoteId(ownerAccountId, remoteId).isEmpty()) {
                deleteDestinationRow(local.id)
            }
        }

        val state = getSyncState(ownerAccountId) ?: DestinationSyncStateEntity(ownerAccountId)
        putSyncState(
            state.copy(
                restoreComplete = true,
                restoreNextAttemptAtEpochMillis = null,
                lastRestoreErrorCode = null,
                lastRestoreErrorMessage = null,
            ),
        )
    }

    @Transaction
    open suspend fun recordRestoreAttempt(ownerAccountId: String): Int {
        val state = getSyncState(ownerAccountId) ?: DestinationSyncStateEntity(ownerAccountId)
        val attempted = state.copy(restoreAttemptCount = state.restoreAttemptCount + 1)
        putSyncState(attempted)
        return attempted.restoreAttemptCount
    }

    @Transaction
    open suspend fun recordRestoreFailure(
        ownerAccountId: String,
        nextAttemptAtEpochMillis: Long?,
        errorCode: String?,
        errorMessage: String,
    ) {
        val state = getSyncState(ownerAccountId) ?: DestinationSyncStateEntity(ownerAccountId)
        putSyncState(
            state.copy(
                restoreNextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                lastRestoreErrorCode = errorCode,
                lastRestoreErrorMessage = errorMessage,
            ),
        )
    }

    @Transaction
    open suspend fun recordOutboxAttempt(ownerAccountId: String, id: Long): DestinationOutboxEntity? {
        val operation = getOutboxById(id, ownerAccountId) ?: return null
        val attempted = operation.copy(
            attemptCount = (operation.attemptCount + 1).coerceAtMost(Int.MAX_VALUE),
            nextAttemptAtEpochMillis = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
        updateOutbox(attempted)
        return attempted
    }

    @Transaction
    open suspend fun retryOutbox(
        ownerAccountId: String,
        id: Long,
        nextAttemptAtEpochMillis: Long,
        errorCode: String?,
        errorMessage: String,
        resetAttemptCount: Boolean = false,
    ) {
        getOutboxById(id, ownerAccountId)?.let { operation ->
            updateOutbox(
                operation.copy(
                    attemptCount = if (resetAttemptCount) 0 else operation.attemptCount,
                    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                    lastErrorCode = errorCode,
                    lastErrorMessage = errorMessage,
                ),
            )
        }
    }

    @Transaction
    open suspend fun blockOutbox(ownerAccountId: String, id: Long, errorCode: String?, errorMessage: String) {
        getOutboxById(id, ownerAccountId)?.let { operation ->
            updateOutbox(
                operation.copy(
                    state = DestinationOutboxState.BLOCKED,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = errorCode,
                    lastErrorMessage = errorMessage,
                ),
            )
        }
    }

    suspend fun acknowledgeOutbox(ownerAccountId: String, id: Long) {
        deleteOutbox(id, ownerAccountId)
    }

    @Transaction
    open suspend fun acknowledgeCreatedOutbox(
        ownerAccountId: String,
        outboxId: Long,
        temporaryRemoteId: String,
        createdDestination: RemoteSavedDestination,
    ) {
        val create = checkNotNull(getOutboxById(outboxId, ownerAccountId)) {
            "생성 완료된 목적지 작업을 찾을 수 없습니다."
        }
        check(create.operation == DestinationOutboxOperation.UPSERT)
        check(create.remoteDestinationId == temporaryRemoteId)
        check(createdDestination.id.toServerDestinationIdOrNull() != null) {
            "서버가 숫자 목적지 ID를 반환하지 않았습니다."
        }

        val local = getById(create.localDestinationId)
        if (local?.ownerAccountId == ownerAccountId && local.remoteDestinationId == temporaryRemoteId) {
            updateDestination(
                local.copy(
                    remoteDestinationId = createdDestination.id,
                    destinationVersion = createdDestination.version,
                ),
            )
        }

        getOutboxForRemoteId(ownerAccountId, temporaryRemoteId)
            .filter { it.localDestinationId == create.localDestinationId }
            .forEach { operation ->
                updateOutbox(operation.copy(remoteDestinationId = createdDestination.id))
            }
        deleteOutbox(outboxId, ownerAccountId)
    }

    @Transaction
    open suspend fun resetOutboxAttempt(ownerAccountId: String, id: Long) {
        getOutboxById(id, ownerAccountId)?.let { operation ->
            updateOutbox(
                operation.copy(
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    lastErrorMessage = null,
                ),
            )
        }
    }

    private suspend fun insertUpsert(destination: SavedDestinationEntity, ownerAccountId: String) {
        val remoteId = checkNotNull(destination.remoteDestinationId)
        insertOutbox(
            DestinationOutboxEntity(
                ownerAccountId = ownerAccountId,
                localDestinationId = destination.id,
                remoteDestinationId = remoteId,
                operation = DestinationOutboxOperation.UPSERT,
                payloadAlias = destination.label,
                payloadLatitude = destination.latitude,
                payloadLongitude = destination.longitude,
                destinationVersion = destination.destinationVersion,
            ),
        )
    }
}

private fun RemoteSavedDestination.toEntity(ownerAccountId: String) = SavedDestinationEntity(
    label = alias,
    latitude = latitude,
    longitude = longitude,
    ownerAccountId = ownerAccountId,
    remoteDestinationId = id,
    destinationVersion = version,
)
