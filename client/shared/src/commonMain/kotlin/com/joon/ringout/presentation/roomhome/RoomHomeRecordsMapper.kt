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
            // 기록 탭은 울림을 이동 시작으로 표시하고, 개별 알람 해제는 표시하지 않는다.
            if (record.event == RoomRecordEvent.ALARM_DISMISSED) return@mapNotNull null
            RoomHomeRecordUiModel(
                id = "${member.userId}:${record.occurredAt}:${record.event}:$index",
                memberId = member.userId.toString(),
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl,
                timeText = record.occurredAt.roomRecordsTimeText(),
                event = when (record.event) {
                    RoomRecordEvent.ALARM_TRIGGERED, RoomRecordEvent.ALARM_RINGING -> RoomHomeRecordEvent.Moving
                    RoomRecordEvent.ALARM_DISMISSED -> RoomHomeRecordEvent.Dismissed
                    RoomRecordEvent.MOVEMENT_STARTED -> RoomHomeRecordEvent.Moving
                    RoomRecordEvent.ARRIVED -> RoomHomeRecordEvent.Arrived
                    RoomRecordEvent.GAVE_UP -> RoomHomeRecordEvent.ForceEnded
                },
                repeatCount = record.repeatCount,
            )
        },
    )
}
