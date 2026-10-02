package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import com.joon.ringout.domain.room.roomRecordsDate
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

internal enum class RoomHomeTab { Info, Records }

internal enum class RoomHomeRecordEvent { Ringing, Dismissed, Moving, Arrived, ForceEnded }

internal data class RoomHomeRecordUiModel(
    val id: String,
    val memberId: String,
    val nickname: String,
    val timeText: String,
    val event: RoomHomeRecordEvent,
    val ringCount: Int = 1,
    val repeatCount: Int? = null,
    val profileImageUrl: String? = null,
)

internal data class RoomHomeDayRecordsUiModel(
    val records: List<RoomHomeRecordUiModel> = emptyList(),
    val achievedMemberCount: Int = 0,
    val achievedMembers: List<RoomHomeMemberUiModel> = emptyList(),
)

internal data class RoomHomeRecordsUiState(
    val selectedDate: MissionDate = roomRecordsDate(),
    val visibleWeekStart: MissionDate = selectedDate.weekDates().first(),
    val records: List<RoomHomeRecordUiModel> = emptyList(),
    val achievedMemberCount: Int = 0,
    val participantCounts: Map<MissionDate, Int> = emptyMap(),
    val participantProfiles: Map<MissionDate, List<String?>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canViewRecords: Boolean = true,
    val isDataLoaded: Boolean = false,
)

internal data class RoomHomeMemberUiModel(
    val id: String,
    val nickname: String,
    val profileImageUrl: String? = null,
)

/** 진행 여부와 참여 인원은 외부에서 전달한다. UI가 시각만으로 활동 여부를 추정하지 않는다. */
internal data class RoomHomeOngoingActivityUiModel(
    val date: MissionDate,
    val participantCount: Int,
)

internal data class RoomHomeUiState(
    val room: RoomUiModel? = null,
    val membershipRole: RoomMembershipRole? = null,
    val members: List<RoomHomeMemberUiModel> = emptyList(),
    val areMembersLoaded: Boolean = false,
    val nextScheduleText: String? = null,
    val remainingTimeText: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val selectedTab: RoomHomeTab = RoomHomeTab.Info,
    val recordsState: RoomHomeRecordsUiState = RoomHomeRecordsUiState(),
    val calendarMonth: MissionYearMonth = recordsState.selectedDate.yearMonth,
    val isCalendarVisible: Boolean = false,
    val ongoingActivity: RoomHomeOngoingActivityUiModel? = null,
    val menuActionState: RoomHomeMenuActionState? = null,
)
