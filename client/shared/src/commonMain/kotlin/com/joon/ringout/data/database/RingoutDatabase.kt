package com.joon.ringout.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.alarm.AlarmDao
import com.joon.ringout.data.alarm.AlarmEntity
import com.joon.ringout.data.alarm.AlarmRepeatDayEntity
import com.joon.ringout.data.alarm.StorageMigrationEntity
import com.joon.ringout.data.destination.SavedDestinationDao
import com.joon.ringout.data.destination.SavedDestinationEntity
import com.joon.ringout.data.missionhistory.MissionHistoryDao
import com.joon.ringout.data.missionhistory.MissionHistoryEntity

@Database(
    entities = [
        MissionHistoryEntity::class,
        AlarmEntity::class,
        AlarmRepeatDayEntity::class,
        StorageMigrationEntity::class,
        SavedDestinationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(RingoutDatabaseConstructor::class)
abstract class RingoutDatabase : RoomDatabase() {
    abstract fun missionHistoryDao(): MissionHistoryDao

    abstract fun alarmDao(): AlarmDao

    abstract fun destinationDao(): SavedDestinationDao
}

@Suppress("KotlinNoActualForExpect")
expect object RingoutDatabaseConstructor : RoomDatabaseConstructor<RingoutDatabase> {
    override fun initialize(): RingoutDatabase
}

fun buildRingoutDatabase(
    builder: RoomDatabase.Builder<RingoutDatabase>,
): RingoutDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .addMigrations(
        RingoutMigration1To2,
        RingoutMigration2To3,
        RingoutMigration3To4,
    )
    .build()

internal const val RingoutDatabaseName = "ringout.db"
