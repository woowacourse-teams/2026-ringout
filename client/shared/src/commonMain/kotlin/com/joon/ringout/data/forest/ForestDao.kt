package com.joon.ringout.data.forest

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestPlantingPolicy
import kotlinx.coroutines.flow.Flow

/** Implemented by the mission DAO so planting shares its success/receipt transaction. */
interface ForestWriteAccess {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeForestState(state: ForestStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForestTree(tree: ForestTreeEntity)

    @Query("SELECT * FROM forest_trees ORDER BY growth_cycle")
    suspend fun getForestTrees(): List<ForestTreeEntity>

    @Query(ForestMilestonesQuery)
    suspend fun getForestMilestones(daysPerTree: Int, maxGrowthDays: Int): List<ForestMilestoneRow>

    /** Caller must already hold a write transaction. Previously planted trees are never changed. */
    suspend fun plantCompletedForestTrees() {
        initializeForestState(ForestStateEntity())
        val existing = getForestTrees()
        val cycles = existing.map { it.growthCycle }.toSet()
        val occupiedSlots = existing.map { it.slotIndex }.toMutableSet()
        val milestones = getForestMilestones(ForestGrowth.DaysPerTree, ForestGrowth.MaxGrowthDays)
        for (milestone in milestones) {
            if (milestone.growthCycle in cycles) continue
            val tree = ForestPlantingPolicy.createTree(
                growthCycle = milestone.growthCycle,
                occupiedSlots = occupiedSlots,
                plantedAtEpochMillis = milestone.completedAtEpochMillis,
            )
            insertForestTree(tree.toEntity())
            occupiedSlots.add(tree.slotIndex)
        }
    }
}

data class ForestMilestoneRow(
    @ColumnInfo(name = "growth_cycle") val growthCycle: Int,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long,
)

internal const val ForestMilestonesQuery = """
    SELECT successful_day_count_at_arrival / :daysPerTree AS growth_cycle,
           MIN(completed_at_epoch_millis) AS completed_at_epoch_millis
    FROM arrival_results
    WHERE grew_on_this_arrival = 1
      AND successful_day_count_at_arrival BETWEEN :daysPerTree AND :maxGrowthDays
      AND successful_day_count_at_arrival % :daysPerTree = 0
    GROUP BY successful_day_count_at_arrival
    ORDER BY successful_day_count_at_arrival
"""

data class ForestSnapshotRow(
    @ColumnInfo(name = "successful_day_count") val successfulDayCount: Int,
    @ColumnInfo(name = "onboarding_completed_at_epoch_millis") val onboardingCompletedAtEpochMillis: Long?,
    @Embedded val tree: ForestTreeEntity?,
)

// One statement prevents combining a new growth count with an old tree list during a write.
private const val ForestSnapshotQuery = """
    SELECT (SELECT COUNT(DISTINCT completed_local_date) FROM arrival_results) AS successful_day_count,
           s.onboarding_completed_at_epoch_millis, t.*
    FROM forest_state s LEFT JOIN forest_trees t ON 1 = 1
    WHERE s.id = 1
    ORDER BY t.growth_cycle
"""

@Dao
interface ForestDao : ForestWriteAccess {
    @Query(ForestSnapshotQuery)
    suspend fun getSnapshot(): List<ForestSnapshotRow>

    @Query(ForestSnapshotQuery)
    fun observeSnapshot(): Flow<List<ForestSnapshotRow>>

    @Query("""
        UPDATE forest_state SET onboarding_completed_at_epoch_millis = :completedAtEpochMillis
        WHERE id = 1 AND onboarding_completed_at_epoch_millis IS NULL
    """)
    suspend fun completeOnboarding(completedAtEpochMillis: Long)

    @Query("""
        UPDATE forest_trees SET acknowledged_at_epoch_millis = :acknowledgedAtEpochMillis
        WHERE growth_cycle = :growthCycle AND acknowledged_at_epoch_millis IS NULL
    """)
    suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long)
}
