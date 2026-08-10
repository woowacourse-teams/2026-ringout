package com.joon.ringout.data.alarm

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "alarm_repeat_days",
    primaryKeys = ["alarm_id", "day_of_week"],
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarm_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AlarmRepeatDayEntity(
    @ColumnInfo(name = "alarm_id")
    val alarmId: String,
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int,
)
