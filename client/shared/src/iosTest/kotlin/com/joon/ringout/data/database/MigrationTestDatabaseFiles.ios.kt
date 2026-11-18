@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.database

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

internal actual fun createMigrationTestDatabasePath(): String =
    NSTemporaryDirectory() + "ringout-migration-${NSUUID().UUIDString}.db"

internal actual fun deleteMigrationTestDatabaseFiles(databasePath: String) {
    listOf(databasePath, "$databasePath-shm", "$databasePath-wal").forEach { path ->
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}

internal actual fun createMigrationTestSQLiteDriver(): androidx.sqlite.SQLiteDriver =
    androidx.sqlite.driver.bundled.BundledSQLiteDriver()
