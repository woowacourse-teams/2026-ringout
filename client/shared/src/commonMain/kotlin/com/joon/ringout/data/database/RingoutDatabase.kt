package com.joon.ringout.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTrackingEntity
import com.joon.ringout.data.alarmactivity.AlarmRingingObservationEntity
import com.joon.ringout.data.alarm.AlarmDao
import com.joon.ringout.data.alarm.AlarmEntity
import com.joon.ringout.data.alarm.AlarmRepeatDayEntity
import com.joon.ringout.data.alarm.StorageMigrationEntity
import com.joon.ringout.data.destination.SavedDestinationDao
import com.joon.ringout.data.destination.SavedDestinationEntity
import com.joon.ringout.data.missionhistory.MissionHistoryDao
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceRingingLinkEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncDao

@Database(
    entities = [
        AlarmOccurrenceTimesEntity::class,
        AlarmActivityEntity::class,
        AlarmActivityTrackingEntity::class,
        AlarmRingingObservationEntity::class,
        MissionHistoryEntity::class,
        AlarmEntity::class,
        AlarmRepeatDayEntity::class,
        StorageMigrationEntity::class,
        SavedDestinationEntity::class,
        AlarmOccurrenceSyncEntity::class,
        AlarmOccurrenceRingingLinkEntity::class,
        AlarmOccurrenceOutboxEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@ConstructedBy(RingoutDatabaseConstructor::class)
abstract class RingoutDatabase : RoomDatabase() {
    abstract fun alarmActivityDao(): AlarmActivityDao

    abstract fun missionHistoryDao(): MissionHistoryDao

    abstract fun alarmDao(): AlarmDao

    abstract fun destinationDao(): SavedDestinationDao

    abstract fun alarmOccurrenceSyncDao(): AlarmOccurrenceSyncDao
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
        RingoutMigration4To5,
        RingoutMigration5To6,
        RingoutMigration6To7,
        RingoutMigration7To8,
        RingoutMigration8To9,
        RingoutMigration9To10,
    )
    .build()

internal const val RingoutDatabaseName = "ringout.db"
