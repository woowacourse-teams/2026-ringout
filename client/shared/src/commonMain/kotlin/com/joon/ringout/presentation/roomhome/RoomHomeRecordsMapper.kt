package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.domain.room.roomRecordsTimeText

internal fun RoomRecords.toDayUiModel(): RoomHomeDayRecordsUiModel {
    val achieved = achievedMembers.map { RoomHomeMemberUiModel(it.userId.toString(), it.nickname, it.profileImageUrl) }
    return RoomHomeDayRecordsUiModel(
        achievedMembers = achieved,
        achievedMemberCount = achieved.size,
        records = timeline.mapNotNull { (member, record, index) ->
            // 알람 울림과 해제는 숨기고 실제 이동 행동만 표시한다.
            val event = when (record.event) {
                RoomRecordEvent.ALARM_TRIGGERED,
                RoomRecordEvent.ALARM_RINGING,
                RoomRecordEvent.ALARM_DISMISSED -> return@mapNotNull null
                RoomRecordEvent.MOVEMENT_STARTED -> RoomHomeRecordEvent.Moving
                RoomRecordEvent.ARRIVED -> RoomHomeRecordEvent.Arrived
                RoomRecordEvent.GAVE_UP -> RoomHomeRecordEvent.ForceEnded
            }
            RoomHomeRecordUiModel(
                id = "${member.userId}:${record.occurredAt}:${record.event}:$index",
                memberId = member.userId.toString(),
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl,
                timeText = record.occurredAt.roomRecordsTimeText(),
                event = event,
                repeatCount = record.repeatCount,
            )
        },
    )
}
