package com.joon.ringout.data.missionhistory

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "mission_history",
    indices = [Index(value = ["completed_at"])],
)
data class MissionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val result: String,
    @ColumnInfo(name = "completed_at")
    val completedAt: String,
)
