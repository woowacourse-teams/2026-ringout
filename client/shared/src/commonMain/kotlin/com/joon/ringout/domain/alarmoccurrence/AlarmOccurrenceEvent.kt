package com.joon.ringout.domain.alarmoccurrence

import kotlin.time.Instant

/** 재울림 발생 시 한 번 생성한 ID와 시각을 해제 및 재전송에서도 그대로 사용한다. */
data class AlarmRepeatRinging(
    val eventId: String,
    val ringingAt: Instant,
) {
    init {
        require(eventId.isNotBlank() && eventId.length <= 64) {
            "재울림 ID는 공백이 아닌 64자 이하여야 합니다."
        }
    }
}

sealed interface AlarmOccurrenceEvent {
    data class InitialDismissed(val dismissedAt: Instant) : AlarmOccurrenceEvent

    data class RepeatRang(val ringing: AlarmRepeatRinging) : AlarmOccurrenceEvent

    data class RepeatDismissed(
        val ringing: AlarmRepeatRinging,
        val dismissedAt: Instant,
    ) : AlarmOccurrenceEvent

    data class Arrived(val arrivedAt: Instant) : AlarmOccurrenceEvent

    data class ForceEnded(val forceEndedAt: Instant) : AlarmOccurrenceEvent
}
