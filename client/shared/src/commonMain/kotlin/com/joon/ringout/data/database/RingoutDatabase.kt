package com.joon.ringout.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.missionhistory.MissionHistoryDao
import com.joon.ringout.data.missionhistory.MissionHistoryEntity

@Database(
    entities = [MissionHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(RingoutDatabaseConstructor::class)
abstract class RingoutDatabase : RoomDatabase() {
    abstract fun missionHistoryDao(): MissionHistoryDao
}

@Suppress("KotlinNoActualForExpect")
expect object RingoutDatabaseConstructor : RoomDatabaseConstructor<RingoutDatabase> {
    override fun initialize(): RingoutDatabase
}

fun buildRingoutDatabase(
    builder: RoomDatabase.Builder<RingoutDatabase>,
): RingoutDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .build()

internal const val RingoutDatabaseName = "ringout.db"
