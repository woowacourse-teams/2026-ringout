package com.joon.ringout.presentation.roomhome.component

import com.joon.ringout.presentation.roomhome.RoomHomeMemberUiModel
import com.joon.ringout.presentation.roomhome.RoomHomeUiState
import com.joon.ringout.presentation.roomhome.RoomHomeDayRecordsUiModel
import com.joon.ringout.presentation.roomhome.RoomHomeRecordsUiState
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays

internal val RoomHomePreviewDate = MissionDate.of(2026, 9, 17)

internal val RoomHomePreviewRecords = listOf(
    RoomHomeRecordUiModel("ring-1", "member-2", "아아아티스트", "06:00", RoomHomeRecordEvent.Moving),
    RoomHomeRecordUiModel("move-1", "member-4", "북여서여남여동여", "06:02", RoomHomeRecordEvent.Moving),
    RoomHomeRecordUiModel("move-2", "member-2", "아아아티스트", "06:03", RoomHomeRecordEvent.Moving),
    RoomHomeRecordUiModel("ring-2", "member-1", "볼링뜨실분다이겨드림", "06:10", RoomHomeRecordEvent.Moving, ringCount = 2),
    RoomHomeRecordUiModel("arrive-1", "member-2", "아아아티스트", "06:12", RoomHomeRecordEvent.Arrived),
    RoomHomeRecordUiModel("end-1", "member-4", "북여서여남여동여", "06:13", RoomHomeRecordEvent.ForceEnded),
)

internal val RoomHomePreviewRecordsByDate = mapOf(
    RoomHomePreviewDate to RoomHomeDayRecordsUiModel(RoomHomePreviewRecords, achievedMemberCount = 1),
    RoomHomePreviewDate.plusDays(-1) to RoomHomeDayRecordsUiModel(
        records = listOf(
            RoomHomeRecordUiModel("previous-move", "member-3", "누누와월럼프", "06:02", RoomHomeRecordEvent.Moving),
            RoomHomeRecordUiModel("previous-arrive", "member-3", "누누와월럼프", "06:15", RoomHomeRecordEvent.Arrived),
        ),
        achievedMemberCount = 1,
    ),
)

/** 네트워크와 무관한 Preview 전용 데이터. 실제 앱의 목록이나 상태에는 주입하지 않는다. */
internal val RoomHomePreviewState = RoomHomeUiState(
    membershipRole = com.joon.ringout.domain.room.RoomMembershipRole.OWNER,
    room = RoomUiModel(
        id = "preview-room-home",
        name = "아침 러닝가는 사람들",
        description = "매일 아침 함께 달려요. 편한 날에 참여해 주세요.",
        createdAt = "2026-09-15T09:00:00",
        activityDays = listOf("월", "화", "수", "목", "금", "토", "일"),
        activityTimeText = "오전 06:00",
        participantCount = 6,
        isJoined = true,
    ),
    members = listOf(
        RoomHomeMemberUiModel(
            "member-1",
            "볼링뜨실분다이겨드림",
            profileImageUrl = "https://cdn.example.com/member-1.png",
        ),
        RoomHomeMemberUiModel("member-2", "아아아티스트"),
        RoomHomeMemberUiModel("member-3", "누누와월럼프"),
        RoomHomeMemberUiModel("member-4", "북여서여남여동여"),
    ),
    areMembersLoaded = true,
    nextScheduleText = "내일 오전 06:00",
    remainingTimeText = "00:18:24",
    recordsState = RoomHomeRecordsUiState(
        selectedDate = RoomHomePreviewDate,
        records = RoomHomePreviewRecords,
        achievedMemberCount = 1,
        participantCounts = RoomHomePreviewRecordsByDate.mapValues { it.value.achievedMemberCount },
        isDataLoaded = true,
    ),
)
