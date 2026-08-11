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
