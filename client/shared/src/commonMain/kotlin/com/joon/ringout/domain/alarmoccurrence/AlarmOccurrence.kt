package com.joon.ringout.domain.alarmoccurrence

import kotlin.time.Instant
import kotlin.jvm.JvmInline

/** 서버가 발급한 실행 ID. 기기의 알람 ID 및 재울림 eventId와 구분한다. */
@JvmInline
value class AlarmOccurrenceId(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            "알람 실행 ID는 UUID 형식이어야 합니다."
        }
    }
}

data class AlarmOccurrenceStart(
    val alarmId: String,
    val scheduledAt: Instant,
    val startedAt: Instant,
) {
    init {
        require(alarmId.isNotBlank() && alarmId.length <= 64) {
            "알람 ID는 공백이 아닌 64자 이하여야 합니다."
        }
    }
}

data class AlarmOccurrence(
    val id: AlarmOccurrenceId,
    val startedAt: Instant,
    val ringings: List<AlarmOccurrenceRinging>,
    val arrivedAt: Instant?,
    val forceEndedAt: Instant?,
)

data class AlarmOccurrenceRinging(
    val type: AlarmOccurrenceRingingType,
    val eventId: String?,
    val ringingAt: Instant,
    val dismissedAt: Instant?,
)

enum class AlarmOccurrenceRingingType { INITIAL, REPEAT }
