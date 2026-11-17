package com.joon.ringout.data.arrival

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.missionhistory.MissionDate

/** A durable snapshot, independent of active missions and editable alarm settings. */
@Entity(
    tableName = "arrival_results",
    indices = [
        Index(value = ["completed_local_date"]),
        Index(value = ["acknowledged_at_epoch_millis", "completed_at_epoch_millis"]),
    ],
)
data class ArrivalResultEntity(
    @PrimaryKey @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "completed_local_date") val completedLocalDate: String,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long,
    @ColumnInfo(name = "alarm_dismissed_at_epoch_millis") val alarmDismissedAtEpochMillis: Long?,
    @ColumnInfo(name = "successful_day_count_at_arrival") val successfulDayCountAtArrival: Int,
    @ColumnInfo(name = "grew_on_this_arrival") val grewOnThisArrival: Boolean,
    @ColumnInfo(name = "acknowledged_at_epoch_millis") val acknowledgedAtEpochMillis: Long? = null,
)

internal fun ArrivalResultEntity.toDomain(): ArrivalResult = ArrivalResult(
    occurrenceId = occurrenceId,
    completedLocalDate = MissionDate.parse(completedLocalDate),
    completedAtEpochMillis = completedAtEpochMillis,
    alarmDismissedAtEpochMillis = alarmDismissedAtEpochMillis,
    successfulDayCountAtArrival = successfulDayCountAtArrival,
    grewOnThisArrival = grewOnThisArrival,
    acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
)
