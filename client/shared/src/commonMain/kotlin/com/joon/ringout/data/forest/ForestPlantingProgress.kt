package com.joon.ringout.data.forest

import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import kotlinx.serialization.Serializable

@Serializable
enum class ForestArrivalPlantingStatus { Pending, Blocked, Resolved }

@Serializable
data class ForestArrivalPlantingCheckpoint(
    val occurrenceId: String,
    val arrivedAtEpochMillis: Long,
    val status: ForestArrivalPlantingStatus = ForestArrivalPlantingStatus.Pending,
    val attemptCount: Int = 0,
    val nextRetryAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
    val successfulDayCount: Int? = null,
)

@Serializable
enum class ForestTreePlantingDeliveryStatus { Pending, Blocked, Synced }

@Serializable
data class ForestTreePlantingDelivery(
    val growthCycle: Int,
    val kind: String,
    val plantedAtEpochMillis: Long,
    val status: ForestTreePlantingDeliveryStatus = ForestTreePlantingDeliveryStatus.Pending,
    val attemptCount: Int = 0,
    val nextRetryAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
    val confirmedTree: ForestStoredTree? = null,
)

/** Only server-confirmed placements may be persisted here and overlaid on a stale GET. */
@Serializable
data class ForestStoredTree(
    val growthCycle: Int,
    val kind: String,
    val slotIndex: Int,
    val plantedAtEpochMillis: Long,
    val acknowledgedAtEpochMillis: Long?,
) {
    fun toDomain() = ForestTree(growthCycle, ForestTreeKind.fromPersistedValue(kind), slotIndex, plantedAtEpochMillis, acknowledgedAtEpochMillis)
}

internal fun ForestTree.toStoredTree() = ForestStoredTree(growthCycle, kind.persistedValue, slotIndex, plantedAtEpochMillis, acknowledgedAtEpochMillis)
