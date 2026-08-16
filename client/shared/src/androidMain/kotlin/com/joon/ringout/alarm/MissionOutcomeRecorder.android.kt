package com.joon.ringout.alarm

import android.content.Context
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.KtorMissionHistoryRemoteDataSource
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import java.time.Instant
import java.time.ZoneId

internal class MissionOutcomeRecorder(context: Context) {
    private val applicationContext = context.applicationContext
    private val recordMissionResult = RecordMissionResult(
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(
                getRingoutDatabase(applicationContext).missionHistoryDao(),
            ),
            remoteDataSource = KtorMissionHistoryRemoteDataSource(
                httpClient = getRingoutHttpClient(),
                tokenStorage = createSecureTokenStorage(applicationContext),
            ),
        ),
    )

    suspend fun record(storedMission: StoredAlarmMission) {
        val result = when (storedMission.phase) {
            AlarmMissionPhase.SuccessPendingNotification -> MissionResult.SUCCESS
            AlarmMissionPhase.FailurePendingPersistence -> MissionResult.FAILURE
            AlarmMissionPhase.Tracking,
            AlarmMissionPhase.RetryPendingRing,
            -> error("Only completed mission phases can be recorded.")
        }
        val completedAt = MissionDate.parse(
            storedMission.terminalCompletedAt
                ?: Instant.ofEpochMilli(storedMission.mission.startedAtEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString(),
        )
        recordMissionResult(
            result = result,
            completedAt = completedAt,
            occurrenceId = storedMission.mission.occurrenceId,
        )
    }
}
