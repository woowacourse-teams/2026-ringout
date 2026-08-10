package com.joon.ringout.data.alarm

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "storage_migrations")
data class StorageMigrationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long,
)
