package com.joon.ringout.data.forest

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "forest_state")
data class ForestStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "onboarding_completed_at_epoch_millis") val onboardingCompletedAtEpochMillis: Long? = null,
)
