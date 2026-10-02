package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.room.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RoomHomeRecordsMapperTest {
    @Test
    fun `회원 기록을 시간순으로 합치고 같은 시각의 중복 이벤트도 보존한다`() {
        val first = Instant.parse("2026-10-01T23:50:00+09:00")
        val next = Instant.parse("2026-10-02T00:10:00+09:00")
        val records = RoomRecords(listOf(
            RoomMemberRecords(2, "두 번째", null, listOf(
                RoomActivityRecord(RoomRecordEvent.ARRIVED, next),
                RoomActivityRecord(RoomRecordEvent.ALARM_TRIGGERED, first),
                RoomActivityRecord(RoomRecordEvent.ARRIVED, next),
            )),
            RoomMemberRecords(1, "첫 번째", "https://example.com/profile", listOf(
                RoomActivityRecord(RoomRecordEvent.ALARM_RINGING, first, 1),
                RoomActivityRecord(RoomRecordEvent.ALARM_DISMISSED, first),
                RoomActivityRecord(RoomRecordEvent.MOVEMENT_STARTED, first),
                RoomActivityRecord(RoomRecordEvent.GAVE_UP, next),
            )),
            RoomMemberRecords(3, "기록 없음", null, emptyList()),
        ))

        val day = records.toDayUiModel()

        assertEquals(4, day.records.size)
        assertEquals(4, day.records.map { it.id }.distinct().size)
        assertEquals(listOf("1", "1", "2", "2"), day.records.map { it.memberId })
        assertEquals(listOf("23:50", "00:10", "00:10", "00:10"), day.records.map { it.timeText })
        assertEquals(1, day.achievedMemberCount)
        assertEquals(listOf("2"), day.achievedMembers.map { it.id })
        assertEquals(null, day.records.first().repeatCount)
        assertEquals("https://example.com/profile", day.records.first().profileImageUrl)
        assertEquals(listOf(RoomHomeRecordEvent.Moving, RoomHomeRecordEvent.ForceEnded,
            RoomHomeRecordEvent.Arrived, RoomHomeRecordEvent.Arrived), day.records.map { it.event })
    }

    @Test
    fun `알람 종료만 있는 날짜는 표시할 기록이 없고 달성 인원도 없다`() {
        val day = RoomRecords(listOf(RoomMemberRecords(1, "회원", null, listOf(
            RoomActivityRecord(RoomRecordEvent.ALARM_DISMISSED, Instant.parse("2026-10-01T08:00:00+09:00")),
        )))).toDayUiModel()
        assertEquals(emptyList(), day.records)
        assertEquals(0, day.achievedMemberCount)
    }

    @Test
    fun `최초 울림과 재울림만 있으면 이동 시작을 표시하지 않는다`() {
        val day = RoomRecords(listOf(RoomMemberRecords(1, "회원", null, listOf(
            RoomActivityRecord(RoomRecordEvent.ALARM_TRIGGERED, Instant.parse("2026-10-01T08:00:00+09:00")),
            RoomActivityRecord(RoomRecordEvent.ALARM_RINGING, Instant.parse("2026-10-01T08:05:00+09:00"), 1),
        )))).toDayUiModel()
        assertEquals(emptyList(), day.records)
        assertEquals(0, day.achievedMemberCount)
    }

    @Test
    fun `울림과 해제와 이동 시작이 함께 오면 실제 이동 시작 시각으로 한 번만 표시한다`() {
        val day = RoomRecords(listOf(RoomMemberRecords(1, "회원", null, listOf(
            RoomActivityRecord(RoomRecordEvent.ALARM_TRIGGERED, Instant.parse("2026-10-01T08:00:00+09:00")),
            RoomActivityRecord(RoomRecordEvent.ALARM_DISMISSED, Instant.parse("2026-10-01T08:01:00+09:00")),
            RoomActivityRecord(RoomRecordEvent.MOVEMENT_STARTED, Instant.parse("2026-10-01T08:02:00+09:00")),
            RoomActivityRecord(RoomRecordEvent.ALARM_RINGING, Instant.parse("2026-10-01T08:05:00+09:00"), 1),
        )))).toDayUiModel()

        assertEquals(RoomHomeRecordEvent.Moving, day.records.single().event)
        assertEquals("08:02", day.records.single().timeText)
    }

    @Test
    fun `기기 시간대와 무관하게 서울 자정과 연도 경계로 날짜와 시간을 표시한다`() {
        val before = Instant.parse("2026-12-31T14:59:59Z")
        val after = Instant.parse("2026-12-31T15:00:00Z")
        assertEquals("2026-12-31", roomRecordsDate(before).iso8601)
        assertEquals("2027-01-01", roomRecordsDate(after).iso8601)
        assertEquals("23:59", before.roomRecordsTimeText())
        assertEquals("00:00", after.roomRecordsTimeText())
    }
}
