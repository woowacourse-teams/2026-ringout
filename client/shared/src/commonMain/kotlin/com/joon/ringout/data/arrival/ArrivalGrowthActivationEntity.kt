package com.joon.ringout.data.arrival

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Initialized once at database creation or migration, never inferred from a recovered result. */
@Entity(tableName = "arrival_growth_activation")
data class ArrivalGrowthActivationEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "activated_at_epoch_millis") val activatedAtEpochMillis: Long,
)
