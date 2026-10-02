package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class RoomRecordEvent {
    ALARM_TRIGGERED, ALARM_RINGING, ALARM_DISMISSED, MOVEMENT_STARTED, ARRIVED, GAVE_UP,
}

data class RoomActivityRecord(
    val event: RoomRecordEvent,
    val occurredAt: Instant,
    /** 서버의 반복 울림 순번. 최초 울림을 포함한 총 횟수가 아니다. */
    val repeatCount: Int? = null,
)

data class RoomMemberRecords(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val records: List<RoomActivityRecord>,
)

data class RoomRecordEntry(
    val member: RoomMemberRecords,
    val record: RoomActivityRecord,
    val sourceIndex: Int,
)

data class RoomRecords(val members: List<RoomMemberRecords>) {
    // 같은 시각의 서로 다른 기록도 보존한다. 서버에는 이벤트 ID가 없으므로 원본 순번으로 구분한다.
    val timeline: List<RoomRecordEntry>
        get() = members.flatMap { member ->
            member.records.mapIndexed { index, record -> RoomRecordEntry(member, record, index) }
        }.sortedWith(compareBy({ it.record.occurredAt }, { it.member.userId }, { it.sourceIndex }))

    val achievedMembers: List<RoomMemberRecords>
        get() = members.filter { member -> member.records.any { it.event == RoomRecordEvent.ARRIVED } }
            .distinctBy { it.userId }
}

/** 모임 기록 API의 날짜 경계는 Asia/Seoul(UTC+09:00)이다. 기기의 시간대와 무관하다. */
fun roomRecordsDate(now: Instant = Clock.System.now()): MissionDate =
    MissionDate.parse((now + 9.hours).toString().substring(0, 10))

fun Instant.roomRecordsTimeText(): String = (this + 9.hours).toString().substring(11, 16)
