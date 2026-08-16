package com.joon.ringout.alarm

import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.KtorMissionHistoryRemoteDataSource
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal class RoomIosMissionOutcomeRecorder(
    private val recordMissionResult: RecordMissionResult = RecordMissionResult(
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(getRingoutDatabase().missionHistoryDao()),
            remoteDataSource = KtorMissionHistoryRemoteDataSource(
                httpClient = getRingoutHttpClient(),
                tokenStorage = createSecureTokenStorage(),
            ),
        ),
    ),
) : IosMissionOutcomeRecorder {

    override suspend fun recordSuccess(occurrenceId: String, completedAt: String) {
        recordMissionResult(MissionResult.SUCCESS, MissionDate.parse(completedAt), occurrenceId)
    }

    override suspend fun recordFailure(occurrenceId: String, completedAt: String) {
        recordMissionResult(MissionResult.FAILURE, MissionDate.parse(completedAt), occurrenceId)
    }
}

internal fun iosMissionDate(epochMillis: Double): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    return formatter.stringFromDate(
        NSDate(
            timeIntervalSinceReferenceDate =
                epochMillis / 1_000.0 - SecondsFromUnixEpochToAppleReferenceDate,
        ),
    )
}

internal fun iosMissionDate(epochMillis: Long): String = iosMissionDate(epochMillis.toDouble())

private const val SecondsFromUnixEpochToAppleReferenceDate = 978_307_200.0
