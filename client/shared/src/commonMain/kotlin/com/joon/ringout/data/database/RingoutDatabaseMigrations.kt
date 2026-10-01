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

internal val RingoutMigration6To7 = Migration(6, 7) { connection ->
    connection.executeSQL(
        """
        CREATE TABLE IF NOT EXISTS `alarm_occurrence_times` (
            `occurrence_id` TEXT NOT NULL PRIMARY KEY,
            `ringing_started_at` INTEGER,
            `ringing_stopped_at` INTEGER,
            `mission_completed_at` INTEGER,
            `ringing_start_observed` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    // Old activity rows have a real captured timestamp, but no source/precision metadata.
    // Never backfill stop/completion times from a date or from the scheduled alarm time.
    connection.executeSQL(
        """
        INSERT OR IGNORE INTO alarm_occurrence_times
            (occurrence_id, ringing_started_at, ringing_start_observed)
        SELECT SUBSTR(event_key, 6), occurred_at_epoch_millis, 1
        FROM alarm_activity_events
        WHERE type = 'RANG' AND event_key LIKE 'rang:%'
        """.trimIndent(),
    )
}

/** Scheduled display times are separate from captured starts; old schedules cannot be reconstructed. */
internal val RingoutMigration7To8 = Migration(7, 8) { connection ->
    connection.executeSQL("ALTER TABLE alarm_occurrence_times ADD COLUMN ringing_scheduled_at INTEGER")
}

internal val RingoutMigration8To9 = Migration(8, 9) { connection ->
    connection.executeSQL("ALTER TABLE alarms ADD COLUMN schedule_version INTEGER NOT NULL DEFAULT 1")
    connection.executeSQL("ALTER TABLE alarm_activity_events ADD COLUMN schedule_version INTEGER NOT NULL DEFAULT 1")
}

/** 기존 기록에 계정이나 실제 울림 시각을 추정해서 부여하지 않고 새 전송 저장소만 추가한다. */
internal val RingoutMigration9To10 = Migration(9, 10) { connection ->
    connection.executeSQL("""
        CREATE TABLE IF NOT EXISTS alarm_occurrence_sync (
            local_execution_id TEXT NOT NULL PRIMARY KEY,
            owner_account_id TEXT NOT NULL,
            alarm_id TEXT NOT NULL,
            schedule_version INTEGER NOT NULL,
            scheduled_at INTEGER,
            started_at INTEGER,
            server_occurrence_id TEXT
        )
    """.trimIndent())
    connection.executeSQL("CREATE INDEX IF NOT EXISTS index_alarm_occurrence_sync_owner_account_id ON alarm_occurrence_sync (owner_account_id)")
    connection.executeSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_alarm_occurrence_sync_server_occurrence_id ON alarm_occurrence_sync (server_occurrence_id)")
    connection.executeSQL("""
        CREATE TABLE IF NOT EXISTS alarm_occurrence_ringing_links (
            local_ringing_id TEXT NOT NULL PRIMARY KEY,
            local_execution_id TEXT NOT NULL,
            event_id TEXT,
            ringing_at INTEGER,
            FOREIGN KEY (local_execution_id) REFERENCES alarm_occurrence_sync (local_execution_id) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
    """.trimIndent())
    connection.executeSQL("CREATE INDEX IF NOT EXISTS index_alarm_occurrence_ringing_links_local_execution_id ON alarm_occurrence_ringing_links (local_execution_id)")
    connection.executeSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_alarm_occurrence_ringing_links_event_id ON alarm_occurrence_ringing_links (event_id)")
    connection.executeSQL("""
        CREATE TABLE IF NOT EXISTS alarm_occurrence_outbox (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            local_execution_id TEXT NOT NULL,
            local_ringing_id TEXT NOT NULL,
            deduplication_key TEXT NOT NULL,
            kind TEXT NOT NULL,
            occurred_at INTEGER,
            state TEXT NOT NULL,
            attempt_count INTEGER NOT NULL,
            next_attempt_at INTEGER,
            last_error_code TEXT,
            last_error_message TEXT,
            FOREIGN KEY (local_execution_id) REFERENCES alarm_occurrence_sync (local_execution_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY (local_ringing_id) REFERENCES alarm_occurrence_ringing_links (local_ringing_id) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
    """.trimIndent())
    connection.executeSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_alarm_occurrence_outbox_local_execution_id_deduplication_key ON alarm_occurrence_outbox (local_execution_id, deduplication_key)")
    connection.executeSQL("CREATE INDEX IF NOT EXISTS index_alarm_occurrence_outbox_local_ringing_id ON alarm_occurrence_outbox (local_ringing_id)")
    connection.executeSQL("CREATE INDEX IF NOT EXISTS index_alarm_occurrence_outbox_state_next_attempt_at ON alarm_occurrence_outbox (state, next_attempt_at)")
}
