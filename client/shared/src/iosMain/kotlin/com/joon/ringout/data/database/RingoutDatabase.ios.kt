@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun getRingoutDatabaseBuilder(): RoomDatabase.Builder<RingoutDatabase> {
    val databasePath = documentDirectory() + "/" + RingoutDatabaseName
    return Room.databaseBuilder<RingoutDatabase>(name = databasePath)
}

private val ringoutDatabaseInstance: RingoutDatabase by lazy {
    buildRingoutDatabase(getRingoutDatabaseBuilder())
}

internal fun getRingoutDatabase(): RingoutDatabase = ringoutDatabaseInstance

private fun documentDirectory(): String {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(directory?.path)
}
