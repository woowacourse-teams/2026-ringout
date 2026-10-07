package com.joon.ringout.presentation.roomactivity

import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.domain.room.roomRecordsTimeText
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal fun RoomRecords.toRoomActivityTimelineUiModels(): List<RoomActivityTimelineUiModel> {
    var previousDate: String? = null
    return timeline.map { entry ->
        val (member, record, sourceIndex) = entry
        val eventDate = record.occurredAt.roomRecordsDateText()
        val dateLabel = eventDate.takeIf { previousDate != null && it != previousDate }
            ?.toRoomActivityDateLabel()
        previousDate = eventDate

        val event = when (record.event) {
            RoomRecordEvent.ALARM_TRIGGERED,
            RoomRecordEvent.ALARM_RINGING -> RoomHomeRecordEvent.Ringing
            RoomRecordEvent.ALARM_DISMISSED -> RoomHomeRecordEvent.Dismissed
            RoomRecordEvent.MOVEMENT_STARTED -> RoomHomeRecordEvent.Moving
            RoomRecordEvent.ARRIVED -> RoomHomeRecordEvent.Arrived
            RoomRecordEvent.GAVE_UP -> RoomHomeRecordEvent.ForceEnded
        }
        val userId = member.userId.toString()

        RoomActivityTimelineUiModel(
            record = RoomHomeRecordUiModel(
                id = "${member.userId}:${record.occurredAt}:${record.event}:$sourceIndex",
                memberId = userId,
                nickname = member.nickname,
                timeText = record.occurredAt.roomRecordsTimeText(),
                event = event,
                repeatCount = record.repeatCount.takeIf { record.event == RoomRecordEvent.ALARM_RINGING },
                profileImageUrl = member.profileImageUrl,
            ),
            memberIds = listOf(userId),
            dateLabel = dateLabel,
        )
    }
}

private fun Instant.roomRecordsDateText(): String = (this + 9.hours).toString().substring(0, 10)

private fun String.toRoomActivityDateLabel(): String {
    val month = substring(5, 7).toInt()
    val day = substring(8, 10).toInt()
    return "${month}월 ${day}일"
}
