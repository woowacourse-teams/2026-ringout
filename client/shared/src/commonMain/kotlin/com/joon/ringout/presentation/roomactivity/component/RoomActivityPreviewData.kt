package com.joon.ringout.presentation.roomactivity.component

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.roomactivity.RoomActivityMemberStatus
import com.joon.ringout.presentation.roomactivity.RoomActivityMemberUiModel
import com.joon.ringout.presentation.roomactivity.RoomActivityTimelineUiModel
import com.joon.ringout.presentation.roomactivity.RoomActivityUiState
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel

/** Figma 확인용 데이터. 실제 모임·알람 저장소에는 주입하지 않는다. */
internal val RoomActivityPreviewState = RoomActivityUiState(
    roomId = "preview-room-home",
    activityDate = MissionDate.of(2026, 9, 17),
    members = listOf(
        RoomActivityMemberUiModel("me", "볼링뜨실분다이겨드림", RoomActivityMemberStatus.Moving, isMe = true),
        RoomActivityMemberUiModel("artist", "아아아티스트", RoomActivityMemberStatus.Arrived),
        RoomActivityMemberUiModel("nunu", "누누와월럼프", RoomActivityMemberStatus.Preparing),
        RoomActivityMemberUiModel("runner", "북여서여남여동여", RoomActivityMemberStatus.GaveUp),
        RoomActivityMemberUiModel("member-5", "함께달려요", RoomActivityMemberStatus.Arrived),
        RoomActivityMemberUiModel("member-6", "아침산책", RoomActivityMemberStatus.Moving),
        RoomActivityMemberUiModel("member-7", "오늘도화이팅", RoomActivityMemberStatus.Waiting),
    ),
    timeline = listOf(
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("move-1", "runner", "북여서여남여동여", "06:02", RoomHomeRecordEvent.Moving),
            listOf("runner"),
        ),
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("move-2", "artist", "아아아티스트", "06:03", RoomHomeRecordEvent.Moving),
            listOf("artist"),
        ),
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("arrive-1", "artist", "아아아티스트", "06:12", RoomHomeRecordEvent.Arrived),
            listOf("artist"),
        ),
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("end-1", "runner", "북여서여남여동여", "06:13", RoomHomeRecordEvent.ForceEnded),
            listOf("runner"),
        ),
    ),
)

internal val RoomActivityAllEventsPreviewState = RoomActivityPreviewState.copy(
    timeline = listOf(
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("ring-group", "artist", "아아아티스트", "06:00", RoomHomeRecordEvent.Ringing),
            listOf("artist", "me", "nunu", "runner"),
        ),
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("dismiss-group", "artist", "아아아티스트", "06:00", RoomHomeRecordEvent.Dismissed),
            listOf("artist", "me", "nunu", "runner"),
        ),
    ) + RoomActivityPreviewState.timeline.take(2) + listOf(
        RoomActivityTimelineUiModel(
            RoomHomeRecordUiModel("ring-2", "me", "볼링뜨실분다이겨드림", "06:10", RoomHomeRecordEvent.Ringing, 2),
            listOf("me"),
        ),
    ) + RoomActivityPreviewState.timeline.drop(2),
)
