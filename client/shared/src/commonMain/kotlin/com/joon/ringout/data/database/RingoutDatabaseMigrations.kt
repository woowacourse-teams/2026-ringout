package com.joon.ringout.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.async.executeSQL

internal val RingoutMigration1To2 = Migration(1, 2) { connection ->
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarms` (
            `id` TEXT NOT NULL,
            `time` TEXT NOT NULL,
            `repeat_enabled` INTEGER NOT NULL,
            `limit_minutes` INTEGER NOT NULL,
            `destination_name` TEXT NOT NULL,
            `destination_address` TEXT NOT NULL,
            `destination_latitude` REAL NOT NULL,
            `destination_longitude` REAL NOT NULL,
            `target_distance_km` REAL NOT NULL,
            `alarm_sound_name` TEXT NOT NULL,
            `alarm_sound_uri` TEXT,
            `enabled` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    connection.executeSQL(
        "CREATE INDEX IF NOT EXISTS `index_alarms_enabled` ON `alarms` (`enabled`)",
    )
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarm_repeat_days` (
            `alarm_id` TEXT NOT NULL,
            `day_of_week` INTEGER NOT NULL,
            PRIMARY KEY(`alarm_id`, `day_of_week`),
            FOREIGN KEY(`alarm_id`) REFERENCES `alarms`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `storage_migrations` (
            `id` TEXT NOT NULL,
            `completed_at_epoch_millis` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
}

internal val RingoutMigration2To3 = Migration(2, 3) { connection ->
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `saved_destinations` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `address` TEXT NOT NULL,
            `latitude` REAL NOT NULL,
            `longitude` REAL NOT NULL
        )
        """.trimIndent(),
    )
}

internal val RingoutMigration3To4 = Migration(3, 4) { connection ->
    connection.executeSQL(
        "ALTER TABLE `mission_history` ADD COLUMN `occurrence_id` TEXT",
    )
    connection.executeSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_mission_history_occurrence_id` ON `mission_history` (`occurrence_id`)",
    )
}

internal val RingoutMigration4To5 = Migration(4, 5) { connection ->
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarm_activity_events` (
            `event_key` TEXT NOT NULL PRIMARY KEY,
            `alarm_id` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `occurred_at_epoch_millis` INTEGER NOT NULL,
            `local_date` TEXT NOT NULL
        )
        """.trimIndent(),
    )
    connection.executeSQL(
        "CREATE INDEX IF NOT EXISTS `index_alarm_activity_events_local_date_type` ON `alarm_activity_events` (`local_date`, `type`)",
    )
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarm_activity_tracking` (
            `id` INTEGER NOT NULL PRIMARY KEY,
            `started_at_epoch_millis` INTEGER NOT NULL,
            `local_date` TEXT NOT NULL
        )
        """.trimIndent(),
    )
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarm_ringing_observations` (
            `system_alarm_id` TEXT NOT NULL PRIMARY KEY,
            `event_key` TEXT NOT NULL
        )
        """.trimIndent(),
    )
}

/** Remove the discontinued creation statistics while keeping ringing history and alarms. */
internal val RingoutMigration5To6 = Migration(5, 6) { connection ->
    connection.executeSQL("DELETE FROM alarm_activity_events WHERE type = 'CREATED'")
}
