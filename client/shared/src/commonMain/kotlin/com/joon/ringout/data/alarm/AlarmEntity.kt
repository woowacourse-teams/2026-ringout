package com.joon.ringout.data.alarm

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "alarms",
    indices = [Index(value = ["enabled"])],
)
data class AlarmEntity(
    @PrimaryKey
    val id: String,
    val time: String,
    @ColumnInfo(name = "repeat_enabled")
    val repeatEnabled: Boolean,
    @ColumnInfo(name = "limit_minutes")
    val limitMinutes: Int,
    @ColumnInfo(name = "destination_name")
    val destinationName: String,
    @ColumnInfo(name = "destination_address")
    val destinationAddress: String,
    @ColumnInfo(name = "destination_latitude")
    val destinationLatitude: Double,
    @ColumnInfo(name = "destination_longitude")
    val destinationLongitude: Double,
    @ColumnInfo(name = "target_distance_km")
    val targetDistanceKm: Double,
    @ColumnInfo(name = "alarm_sound_name")
    val alarmSoundName: String,
    @ColumnInfo(name = "alarm_sound_uri")
    val alarmSoundUri: String?,
    val enabled: Boolean,
)
