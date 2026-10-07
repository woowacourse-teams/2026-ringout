package com.joon.ringout.presentation.roomactivity

import com.joon.ringout.domain.room.RoomActivityRecord
import com.joon.ringout.domain.room.RoomMemberRecords
import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RoomActivityTimelineMapperTest {
    @Test
    fun `여섯 이벤트를 KST 시간과 다음 날짜 경계까지 순서대로 표시한다`() {
        val timeline = RoomRecords(
            listOf(
                RoomMemberRecords(
                    userId = 18,
                    nickname = "기록 회원",
                    profileImageUrl = "https://example.test/profile.png",
                    records = listOf(
                        record(RoomRecordEvent.ALARM_TRIGGERED, "2026-10-07T14:50:00Z"),
                        record(RoomRecordEvent.ALARM_RINGING, "2026-10-07T14:55:00Z", repeatCount = 2),
                        record(RoomRecordEvent.ALARM_DISMISSED, "2026-10-07T14:59:00Z"),
                        record(RoomRecordEvent.MOVEMENT_STARTED, "2026-10-07T15:00:00Z"),
                        record(RoomRecordEvent.ARRIVED, "2026-10-07T15:10:00Z"),
                        record(RoomRecordEvent.GAVE_UP, "2026-10-07T15:11:00Z"),
                    ),
                ),
            ),
        ).toRoomActivityTimelineUiModels()

        assertEquals(
            listOf(
                RoomHomeRecordEvent.Ringing,
                RoomHomeRecordEvent.Ringing,
                RoomHomeRecordEvent.Dismissed,
                RoomHomeRecordEvent.Moving,
                RoomHomeRecordEvent.Arrived,
                RoomHomeRecordEvent.ForceEnded,
            ),
            timeline.map { it.record.event },
        )
        assertEquals(listOf("23:50", "23:55", "23:59", "00:00", "00:10", "00:11"), timeline.map { it.record.timeText })
        assertEquals(2, timeline[1].record.repeatCount)
        assertEquals(listOf(null, null, null, "10월 8일", null, null), timeline.map { it.dateLabel })
        assertEquals("기록 회원", timeline.first().record.nickname)
        assertEquals("https://example.test/profile.png", timeline.first().record.profileImageUrl)
        assertEquals("18", timeline.first().record.memberId)
        assertEquals(listOf("18"), timeline.first().memberIds)
    }

    @Test
    fun `동일 회원의 같은 시각 기록을 고유하고 재조회 가능한 key로 보존한다`() {
        val source = RoomRecords(
            listOf(
                RoomMemberRecords(
                    userId = 18,
                    nickname = "기록 회원",
                    profileImageUrl = null,
                    records = listOf(
                        record(RoomRecordEvent.ALARM_RINGING, "2026-10-07T14:55:00Z", repeatCount = 1),
                        record(RoomRecordEvent.ALARM_RINGING, "2026-10-07T14:55:00Z", repeatCount = 2),
                    ),
                ),
            ),
        )

        val firstQuery = source.toRoomActivityTimelineUiModels()
        val secondQuery = source.toRoomActivityTimelineUiModels()

        assertEquals(2, firstQuery.size)
        assertEquals(2, firstQuery.map { it.record.id }.distinct().size)
        assertEquals(firstQuery.map { it.record.id }, secondQuery.map { it.record.id })
        assertEquals(listOf(1, 2), firstQuery.map { it.record.repeatCount })
    }

    @Test
    fun `기록이 없으면 빈 타임라인을 반환한다`() {
        assertTrue(RoomRecords(emptyList()).toRoomActivityTimelineUiModels().isEmpty())
    }

    private fun record(
        event: RoomRecordEvent,
        occurredAt: String,
        repeatCount: Int? = null,
    ) = RoomActivityRecord(event, Instant.parse(occurredAt), repeatCount)
}
