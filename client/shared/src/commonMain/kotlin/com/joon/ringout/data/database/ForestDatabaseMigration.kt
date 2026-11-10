package com.joon.ringout.data.database

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import com.joon.ringout.data.forest.ForestMilestonesQuery
import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestPlantingPolicy

internal class ForestDatabaseCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        initializeForestState(connection)
    }
}

private suspend fun initializeForestState(connection: SQLiteConnection) {
    connection.executeSQL("INSERT OR IGNORE INTO forest_state (id, onboarding_completed_at_epoch_millis) VALUES (1, NULL)")
}

/** Backfill only earned arrival receipts, never the legacy mission history excluded by v14. */
internal val RingoutMigration14To15 = Migration(14, 15) { connection ->
    connection.executeSQL("""
        CREATE TABLE IF NOT EXISTS forest_state (
            id INTEGER NOT NULL PRIMARY KEY,
            onboarding_completed_at_epoch_millis INTEGER
        )
    """.trimIndent())
    connection.executeSQL("""
        CREATE TABLE IF NOT EXISTS forest_trees (
            growth_cycle INTEGER NOT NULL PRIMARY KEY,
            kind TEXT NOT NULL,
            slot_index INTEGER NOT NULL,
            planted_at_epoch_millis INTEGER NOT NULL,
            acknowledged_at_epoch_millis INTEGER
        )
    """.trimIndent())
    connection.executeSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_forest_trees_slot_index ON forest_trees (slot_index)")
    initializeForestState(connection)

    val occupiedSlots = mutableSetOf<Int>()
    connection.prepare(ForestMilestonesQuery).use { statement ->
        statement.bindLong(1, ForestGrowth.DaysPerTree.toLong())
        statement.bindLong(2, ForestGrowth.MaxGrowthDays.toLong())
        while (statement.step()) {
            val tree = ForestPlantingPolicy.createTree(
                growthCycle = statement.getLong(0).toInt(),
                occupiedSlots = occupiedSlots,
                plantedAtEpochMillis = statement.getLong(1),
            )
            connection.prepare("""
                INSERT INTO forest_trees (growth_cycle, kind, slot_index, planted_at_epoch_millis, acknowledged_at_epoch_millis)
                VALUES (?, ?, ?, ?, NULL)
            """.trimIndent()).use { insert ->
                insert.bindLong(1, tree.growthCycle.toLong())
                insert.bindText(2, tree.kind.persistedValue)
                insert.bindLong(3, tree.slotIndex.toLong())
                insert.bindLong(4, tree.plantedAtEpochMillis)
                insert.step()
            }
            occupiedSlots.add(tree.slotIndex)
        }
    }
}
