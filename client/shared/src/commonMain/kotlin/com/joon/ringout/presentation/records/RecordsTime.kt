package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult

/** Include the date for events outside the card's completion day (e.g. across midnight). */
internal expect fun formatRecordsTime(epochMillis: Long, completedDate: MissionDate): String

internal data class RecordTimesUiState(
    val title: String,
    val ringingRange: String,
    val ringingDescription: String,
    val completedTime: String?,
    val completedDescription: String,
)

internal fun MissionHistoryEntry.recordTimes(
    format: (Long, MissionDate) -> String = ::formatRecordsTime,
): RecordTimesUiState {
    val started = ringingStartedAtEpochMillis?.let { format(it, completedAt) }
    val stopped = ringingStoppedAtEpochMillis?.let { format(it, completedAt) }
    val completed = missionCompletedAtEpochMillis?.let { format(it, completedAt) }
    val completionLabel = if (result == MissionResult.SUCCESS) "미션 완료" else "미션 종료"
    return RecordTimesUiState(
        title = started?.let { "${it}에 울린 알람" } ?: "시간 기록 없는 알람",
        ringingRange = "${started ?: "--:--"} ~ ${stopped ?: "--:--"}",
        ringingDescription = when {
            started == null && stopped == null -> "울림·종료 시각 기록 없음"
            started == null -> "울림 시작 시각 기록 없음"
            stopped == null -> if (isRingingStartObserved) "울림 확인 시각 · 종료 시각 기록 없음" else "울림 종료 시각 기록 없음"
            isRingingStartObserved -> "울림 확인 ~ 울림 종료"
            else -> "울림 시작 ~ 울림 종료"
        },
        completedTime = completed,
        completedDescription = completed?.let { "$completionLabel $it" } ?: "완료 시각 기록 없음",
    )
}
