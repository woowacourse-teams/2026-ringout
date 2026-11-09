package com.joon.ringout.domain.arrival

import com.joon.ringout.domain.missionhistory.MissionDate

/** A successful occurrence and its growth snapshot, retained after the active mission is cleared. */
data class ArrivalResult(
    val occurrenceId: String,
    val completedLocalDate: MissionDate,
    val completedAtEpochMillis: Long,
    val alarmDismissedAtEpochMillis: Long?,
    val successfulDayCountAtArrival: Int,
    val grewOnThisArrival: Boolean,
    val acknowledgedAtEpochMillis: Long?,
) {
    init {
        require(occurrenceId.isNotBlank()) { "Arrival occurrence ID must not be blank." }
        require(successfulDayCountAtArrival > 0) { "An arrival result must include a successful day." }
    }

    /** Use the saved count, not the current total, when displaying an older result. */
    val growth: ArrivalGrowth
        get() = ArrivalGrowth(successfulDayCountAtArrival)
}
