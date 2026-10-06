package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrence
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRinging
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRingingType
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class AlarmOccurrenceStartRequest(
    val alarmId: String,
    val scheduledAt: String,
    val startedAt: String,
)

internal fun AlarmOccurrenceStart.toRequest() = AlarmOccurrenceStartRequest(
    alarmId = alarmId,
    scheduledAt = scheduledAt.toString(),
    startedAt = startedAt.toString(),
)

@Serializable
internal data class AlarmOccurrenceEventRequest(
    val eventId: String? = null,
    val ringingAt: String? = null,
    val dismissedAt: String? = null,
    val arrivedAt: String? = null,
    val forceEndedAt: String? = null,
)

internal fun AlarmOccurrenceEvent.toRequest(): AlarmOccurrenceEventRequest = when (this) {
    is AlarmOccurrenceEvent.InitialDismissed -> AlarmOccurrenceEventRequest(dismissedAt = dismissedAt.toString())
    is AlarmOccurrenceEvent.RepeatRang -> AlarmOccurrenceEventRequest(
        eventId = ringing.eventId,
        ringingAt = ringing.ringingAt.toString(),
    )
    is AlarmOccurrenceEvent.RepeatDismissed -> AlarmOccurrenceEventRequest(
        eventId = ringing.eventId,
        ringingAt = ringing.ringingAt.toString(),
        dismissedAt = dismissedAt.toString(),
    )
    is AlarmOccurrenceEvent.Arrived -> AlarmOccurrenceEventRequest(arrivedAt = arrivedAt.toString())
    is AlarmOccurrenceEvent.ForceEnded -> AlarmOccurrenceEventRequest(forceEndedAt = forceEndedAt.toString())
}

@Serializable
internal data class AlarmOccurrenceDetailResponse(
    val alarmOccurrenceId: String,
    val startedAt: String,
    val ringings: List<AlarmRingingResponse>,
    val arrivedAt: String? = null,
    val forceEndedAt: String? = null,
) {
    fun toDomain() = AlarmOccurrence(
        id = AlarmOccurrenceId(alarmOccurrenceId),
        startedAt = Instant.parse(startedAt),
        ringings = ringings.map(AlarmRingingResponse::toDomain),
        arrivedAt = arrivedAt?.let(Instant::parse),
        forceEndedAt = forceEndedAt?.let(Instant::parse),
    )
}

@Serializable
internal data class AlarmRingingResponse(
    val type: String,
    val eventId: String? = null,
    val ringingAt: String,
    val dismissedAt: String? = null,
) {
    fun toDomain() = AlarmOccurrenceRinging(
        type = AlarmOccurrenceRingingType.valueOf(type),
        eventId = eventId,
        ringingAt = Instant.parse(ringingAt),
        dismissedAt = dismissedAt?.let(Instant::parse),
    )
}
