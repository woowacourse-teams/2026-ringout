package com.joon.ringout.data.destination

import androidx.room3.Entity
import androidx.room3.ColumnInfo
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "saved_destinations",
    indices = [Index(value = ["owner_account_id", "remote_destination_id"], unique = true)],
)
data class SavedDestinationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "owner_account_id", defaultValue = "NULL") val ownerAccountId: String? = null,
    @ColumnInfo(name = "remote_destination_id", defaultValue = "NULL") val remoteDestinationId: String? = null,
    @ColumnInfo(name = "destination_version", defaultValue = "1") val destinationVersion: Long = 1L,
)
