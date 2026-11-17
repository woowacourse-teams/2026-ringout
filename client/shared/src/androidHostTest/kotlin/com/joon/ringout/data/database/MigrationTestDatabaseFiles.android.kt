package com.joon.ringout.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File

internal actual fun createMigrationTestDatabasePath(): String =
    File.createTempFile("ringout-migration-", ".db").absolutePath

internal actual fun deleteMigrationTestDatabaseFiles(databasePath: String) {
    listOf(databasePath, "$databasePath-shm", "$databasePath-wal")
        .forEach { path -> File(path).delete() }
}

internal actual fun createMigrationTestSQLiteDriver(): SQLiteDriver = AndroidSQLiteDriver()
