package com.joon.ringout.alarm

import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal class RoomIosMissionOutcomeRecorder : IosMissionOutcomeRecorder {
    private val recordMissionResult = RecordMissionResult(
        DefaultMissionHistoryRepository(
            RoomMissionHistoryDataSource(getRingoutDatabase().missionHistoryDao()),
        ),
    )

    override suspend fun recordSuccess(occurrenceId: String, completedAtEpochMillis: Long) {
        recordMissionResult(MissionResult.SUCCESS, missionDate(completedAtEpochMillis), occurrenceId)
    }

    override suspend fun recordFailure(occurrenceId: String, completedAtEpochMillis: Long) {
        recordMissionResult(MissionResult.FAILURE, missionDate(completedAtEpochMillis), occurrenceId)
    }
}

private fun missionDate(epochMillis: Long): MissionDate {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    return MissionDate.parse(
        formatter.stringFromDate(
            NSDate(
                timeIntervalSinceReferenceDate =
                    epochMillis / 1_000.0 - SecondsFromUnixEpochToAppleReferenceDate,
            ),
        ),
    )
}

private const val SecondsFromUnixEpochToAppleReferenceDate = 978_307_200.0
