package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.presentation.toTwelveHourDisplay

/** Include the date for events outside the card's ringing day (e.g. across midnight). */
internal expect fun formatRecordsTime(epochMillis: Long, completedDate: MissionDate): String

internal data class RecordTimesUiState(
    val title: String,
    val ringingRange: String,
    val ringingDescription: String,
    val completedTime: String?,
    val completedDescription: String,
)

internal fun AlarmUsageRecord.recordTimes(
    format: (Long, MissionDate) -> String = ::formatRecordsTime,
): RecordTimesUiState {
    val started = ringingStartedAtEpochMillis?.let { format(it, date) }
    val stopped = ringingStoppedAtEpochMillis?.let { format(it, date) }
    val completed = missionCompletedAtEpochMillis?.let { format(it, date) }
    val completionLabel = if (result == MissionResult.FAILURE) "강제 종료" else "미션 완료"
    return RecordTimesUiState(
        title = started?.toRecordTitle() ?: "시간 기록 없는 알람",
        ringingRange = if (started != null && stopped == null) started else "${started ?: "--:--"} ~ ${stopped ?: "--:--"}",
        ringingDescription = when {
            started == null && stopped == null -> "울림/종료 시각 기록 없음"
            started == null -> "울림 시작 시각 기록 없음"
            stopped == null -> if (isRingingStartObserved) "울림 확인 시각 · 종료 시각 기록 없음" else "울림 종료 시각 기록 없음"
            isRingingStartObserved -> "울림 확인 ~ 울림 종료"
            else -> "울림 시작 ~ 울림 종료"
        },
        completedTime = completed,
        completedDescription = completed?.let { "$completionLabel $it" }
            ?: if (result == MissionResult.FAILURE) "강제 종료 시각 기록 없음" else "완료 시각 기록 없음",
    )
}

private fun String.toRecordTitle(): String {
    val display = substringAfterLast(' ').toTwelveHourDisplay()
    val datePrefix = substringBeforeLast(' ', "").let { if (it.isEmpty()) "" else "$it " }
    return "$datePrefix${display.period} ${display.time}에 울린 알람"
}
