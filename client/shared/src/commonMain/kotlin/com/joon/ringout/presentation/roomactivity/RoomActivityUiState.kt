package com.joon.ringout.presentation.roomactivity

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomMemberMovementStatus
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel

internal enum class RoomActivityMemberStatus(val label: String) {
    Waiting("응답 대기"),
    Preparing("이동 준비"),
    Moving("이동 중"),
    Arrived("목적지 도착"),
    GaveUp("포기"),
    Unknown("상태 확인 불가"),
}

internal data class RoomActivityMemberUiModel(
    val id: String,
    val nickname: String,
    val status: RoomActivityMemberStatus,
    val isMe: Boolean = false,
    val profileImageUrl: String? = null,
) {
    val displayName: String get() = if (isMe) "나" else nickname
}

internal data class RoomActivityTimelineUiModel(
    val record: RoomHomeRecordUiModel,
    val memberIds: List<String>,
    val dateLabel: String? = null,
)

/** 회원 이동 상태와 기록 타임라인의 조회 상태를 각 영역별로 유지한다. */
internal data class RoomActivityUiState(
    val roomId: String = "",
    val activityDate: MissionDate? = null,
    val members: List<RoomActivityMemberUiModel> = emptyList(),
    val timeline: List<RoomActivityTimelineUiModel> = emptyList(),
    val timelineDate: MissionDate? = null,
    val selectedMemberIds: List<String>? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDataLoaded: Boolean = false,
    val errorMessage: String? = null,
    val refreshErrorMessage: String? = null,
    val canRetry: Boolean = false,
    val showTimeline: Boolean = false,
    val isTimelineInitialLoading: Boolean = false,
    val isTimelineRefreshing: Boolean = false,
    val isTimelineDataLoaded: Boolean = false,
    val timelineErrorMessage: String? = null,
    val timelineRefreshErrorMessage: String? = null,
    val canRetryTimeline: Boolean = false,
) {
    val orderedMembers: List<RoomActivityMemberUiModel> get() = members.sortedByDescending { it.isMe }
    val selectedMembers: List<RoomActivityMemberUiModel>
        get() = orderedMembers.filter { it.id in selectedMemberIds.orEmpty() }
    val isEmptySuccess: Boolean get() = isDataLoaded && members.isEmpty() && errorMessage == null
    val isTimelineEmptySuccess: Boolean
        get() = isTimelineDataLoaded && timeline.isEmpty() && timelineErrorMessage == null
}

internal fun RoomMemberMovement.toRoomActivityMemberUiModel() = RoomActivityMemberUiModel(
    id = userId.toString(),
    nickname = nickname,
    status = status.toRoomActivityMemberStatus(),
    profileImageUrl = profileImageUrl,
)

private fun RoomMemberMovementStatus.toRoomActivityMemberStatus() = when (this) {
    RoomMemberMovementStatus.Idle -> RoomActivityMemberStatus.Waiting
    RoomMemberMovementStatus.AlarmTriggered -> RoomActivityMemberStatus.Preparing
    RoomMemberMovementStatus.MovementStarted,
    RoomMemberMovementStatus.Moving -> RoomActivityMemberStatus.Moving
    RoomMemberMovementStatus.Arrived -> RoomActivityMemberStatus.Arrived
    RoomMemberMovementStatus.GaveUp -> RoomActivityMemberStatus.GaveUp
    RoomMemberMovementStatus.Unknown -> RoomActivityMemberStatus.Unknown
}
