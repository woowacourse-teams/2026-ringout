package com.joon.ringout.presentation.roomactivity

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel

internal enum class RoomActivityMemberStatus(val label: String) {
    Waiting("응답 대기"),
    Preparing("이동 준비"),
    Moving("이동 중"),
    Arrived("목적지 도착"),
    GaveUp("포기"),
}

internal data class RoomActivityMemberUiModel(
    val id: String,
    val nickname: String,
    val status: RoomActivityMemberStatus,
    val isMe: Boolean = false,
) {
    val displayName: String get() = if (isMe) "나" else nickname
}

internal data class RoomActivityTimelineUiModel(
    val record: RoomHomeRecordUiModel,
    val memberIds: List<String>,
    val dateLabel: String? = null,
)

/** API 연결 전에는 호출부가 전달한 표시 데이터만 사용한다. 집계·상태 판정은 수행하지 않는다. */
internal data class RoomActivityUiState(
    val roomId: String,
    val activityDate: MissionDate,
    val members: List<RoomActivityMemberUiModel> = emptyList(),
    val timeline: List<RoomActivityTimelineUiModel> = emptyList(),
    val selectedMemberIds: List<String>? = null,
) {
    val orderedMembers: List<RoomActivityMemberUiModel> get() = members.sortedByDescending { it.isMe }
    val selectedMembers: List<RoomActivityMemberUiModel>
        get() = orderedMembers.filter { it.id in selectedMemberIds.orEmpty() }
}
