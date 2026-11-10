package com.joon.ringout.data.forest

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind

@Entity(tableName = "forest_trees", indices = [Index(value = ["slot_index"], unique = true)])
data class ForestTreeEntity(
    @PrimaryKey @ColumnInfo(name = "growth_cycle") val growthCycle: Int,
    val kind: String,
    @ColumnInfo(name = "slot_index") val slotIndex: Int,
    @ColumnInfo(name = "planted_at_epoch_millis") val plantedAtEpochMillis: Long,
    @ColumnInfo(name = "acknowledged_at_epoch_millis") val acknowledgedAtEpochMillis: Long?,
)

internal fun ForestTreeEntity.toDomain() = ForestTree(
    growthCycle = growthCycle,
    kind = ForestTreeKind.fromPersistedValue(kind),
    slotIndex = slotIndex,
    plantedAtEpochMillis = plantedAtEpochMillis,
    acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
)

internal fun ForestTree.toEntity() = ForestTreeEntity(
    growthCycle = growthCycle,
    kind = kind.persistedValue,
    slotIndex = slotIndex,
    plantedAtEpochMillis = plantedAtEpochMillis,
    acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
)
