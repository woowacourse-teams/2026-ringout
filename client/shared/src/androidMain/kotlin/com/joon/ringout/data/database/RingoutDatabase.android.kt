package com.joon.ringout.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun getRingoutDatabaseBuilder(
    context: Context,
): RoomDatabase.Builder<RingoutDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath(RingoutDatabaseName)
    return Room.databaseBuilder<RingoutDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
}

@Volatile
private var ringoutDatabaseInstance: RingoutDatabase? = null

internal fun getRingoutDatabase(context: Context): RingoutDatabase =
    ringoutDatabaseInstance ?: synchronized(RingoutDatabase::class.java) {
        ringoutDatabaseInstance ?: buildRingoutDatabase(
            getRingoutDatabaseBuilder(context.applicationContext),
        ).also { database ->
            ringoutDatabaseInstance = database
        }
    }
