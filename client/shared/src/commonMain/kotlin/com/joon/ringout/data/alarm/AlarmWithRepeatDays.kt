package com.joon.ringout.data.alarm

import androidx.room3.Embedded
import androidx.room3.Relation

data class AlarmWithRepeatDays(
    @Embedded
    val alarm: AlarmEntity,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["alarm_id"],
    )
    val repeatDays: List<AlarmRepeatDayEntity>,
)
