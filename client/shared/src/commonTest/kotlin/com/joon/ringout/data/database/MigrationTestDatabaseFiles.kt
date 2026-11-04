package com.joon.ringout.data.database

import androidx.sqlite.SQLiteDriver

internal expect fun createMigrationTestDatabasePath(): String

internal expect fun deleteMigrationTestDatabaseFiles(databasePath: String)

internal expect fun createMigrationTestSQLiteDriver(): SQLiteDriver
