package com.joon.ringout.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

internal class ArrivalGrowthDatabaseCallback(
    private val activatedAtEpochMillis: Long,
) : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        initializeArrivalGrowth(connection, activatedAtEpochMillis)
    }
}

internal suspend fun initializeArrivalGrowth(connection: SQLiteConnection, activatedAtEpochMillis: Long) {
    connection.executeSQL(
        "INSERT OR IGNORE INTO arrival_growth_activation (id, activated_at_epoch_millis) VALUES (1, $activatedAtEpochMillis)",
    )
}
