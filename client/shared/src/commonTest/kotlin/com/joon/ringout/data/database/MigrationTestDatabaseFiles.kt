package com.joon.ringout.data.database

import androidx.sqlite.SQLiteDriver

internal expect fun createMigrationTestDatabasePath(): String

internal expect fun deleteMigrationTestDatabaseFiles(databasePath: String)

internal expect fun createMigrationTestSQLiteDriver(): SQLiteDriver


internal fun removeAlarmSyncSchemaForVersion18(hasIosRecordTable: Boolean = true): Array<String> =
    buildList {
        add("DROP TABLE alarm_outbox")
        add("DROP TABLE alarm_restore")
        add("DROP INDEX IF EXISTS index_alarms_owner_account_id_remote_alarm_id")
        for (column in listOf(
            "remote_alarm_id", "alarm_version", "confirmed_alarm_version", "remote_repeat_time", "pending_reservation_id",
        )) {
            add("ALTER TABLE alarms DROP COLUMN $column")
        }
        add("ALTER TABLE alarm_occurrence_sync DROP COLUMN remote_alarm_id")
        if (hasIosRecordTable) add("ALTER TABLE ios_alarm_occurrence_recording DROP COLUMN remote_alarm_id")
    }.toTypedArray()
