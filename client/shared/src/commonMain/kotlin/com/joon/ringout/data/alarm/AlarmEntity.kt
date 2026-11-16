package com.joon.ringout.data.alarm

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "alarms",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["owner_account_id", "remote_alarm_id"], unique = true),
    ],
)
data class AlarmEntity(
    @PrimaryKey
    val id: String,
    val time: String,
    @ColumnInfo(name = "repeat_enabled")
    val repeatEnabled: Boolean,
    @ColumnInfo(name = "limit_minutes")
    val limitMinutes: Int,
    @ColumnInfo(name = "destination_label")
    val destinationLabel: String,
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
    @ColumnInfo(name = "schedule_version", defaultValue = "1")
    val scheduleVersion: Long = 1,
    @ColumnInfo(name = "owner_account_id", defaultValue = "NULL")
    val ownerAccountId: String? = null,
    @ColumnInfo(name = "remote_alarm_id", defaultValue = "NULL")
    val remoteAlarmId: String? = null,
    @ColumnInfo(name = "alarm_version", defaultValue = "0")
    val alarmVersion: Long = 0,
    @ColumnInfo(name = "confirmed_alarm_version", defaultValue = "NULL")
    val confirmedAlarmVersion: Long? = null,
    @ColumnInfo(name = "remote_repeat_time", defaultValue = "NULL")
    val remoteRepeatTime: Int? = null,
    @ColumnInfo(name = "pending_reservation_id", defaultValue = "NULL")
    val pendingReservationId: String? = null,
)
