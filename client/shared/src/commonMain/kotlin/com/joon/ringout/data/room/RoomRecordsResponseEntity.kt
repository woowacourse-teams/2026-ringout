package com.joon.ringout.data.room

import com.joon.ringout.domain.room.RoomActivityRecord
import com.joon.ringout.domain.room.RoomMemberRecords
import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class RoomRecordsResponseEntity(val memberRecords: List<RoomMemberRecordsEntity>) {
    fun toDomain(): RoomRecords = RoomRecords(memberRecords.map { member ->
        RoomMemberRecords(
            userId = member.userId,
            nickname = member.nickname,
            profileImageUrl = member.profileImageUrl?.takeIf(String::isNotBlank),
            records = member.records.map { record ->
                val event = RoomRecordEvent.valueOf(record.event)
                if (event == RoomRecordEvent.ALARM_RINGING) {
                    require(record.count != null && record.count > 0) { "반복 울림 순번이 올바르지 않아요." }
                }
                RoomActivityRecord(
                    event = event,
                    occurredAt = Instant.parse(record.occurredAt),
                    repeatCount = record.count.takeIf { event == RoomRecordEvent.ALARM_RINGING },
                )
            },
        )
    })
}

@Serializable
internal data class RoomMemberRecordsEntity(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
    val records: List<RoomActivityRecordEntity>,
)

@Serializable
internal data class RoomActivityRecordEntity(
    val event: String,
    val occurredAt: String,
    val count: Int? = null,
)
