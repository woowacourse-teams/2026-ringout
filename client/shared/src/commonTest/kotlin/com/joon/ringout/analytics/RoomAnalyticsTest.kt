package com.joon.ringout.analytics

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomMembershipRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoomAnalyticsTest {
    @Test
    fun `모임 비활성화는 아홉 모임 이벤트를 막고 개인 로그인 이벤트는 유지한다`() {
        val events = mutableListOf<AnalyticsEvent>()
        val recorder = DefaultProductAnalyticsRecorder(
            tracker = AnalyticsTracker { events += it },
            usageStore = object : ProductAnalyticsUsageStore {
                override fun claimOnboardingEvent(eventName: AnalyticsEventName) = true
                override fun claimDestinationCreation(destinationKey: String) = 1L
            },
        )
        listOf(
            RoomAnalyticsEvent.ListViewed(AuthSessionState.Authenticated),
            RoomAnalyticsEvent.DetailViewed(3), RoomAnalyticsEvent.Created(2),
            RoomAnalyticsEvent.Joined(4), RoomAnalyticsEvent.HomeViewed(4, RoomMembershipRole.MEMBER),
            RoomAnalyticsEvent.RecordsViewed(null), RoomAnalyticsEvent.Left(RoomMembershipRole.MEMBER),
            RoomAnalyticsEvent.Deleted(RoomMembershipRole.OWNER),
            RoomAnalyticsEvent.Updated(RoomMembershipRole.OWNER),
        ).forEach(recorder::recordRoomEvent)
        recorder.recordLoginStarted(AnalyticsAuthProvider.Google)
        assertEquals(listOf(AnalyticsEventName.LoginStarted), events.map { it.name })
    }

    @Test
    fun `모임 이벤트 아홉 종류는 정의된 이름과 범주형 값만 전송한다`() {
        val events = listOf(
            RoomAnalyticsEvent.ListViewed(AuthSessionState.Authenticated),
            RoomAnalyticsEvent.DetailViewed(3), RoomAnalyticsEvent.Created(2),
            RoomAnalyticsEvent.Joined(4), RoomAnalyticsEvent.HomeViewed(4, RoomMembershipRole.MEMBER),
            RoomAnalyticsEvent.RecordsViewed(null), RoomAnalyticsEvent.Left(RoomMembershipRole.MEMBER),
            RoomAnalyticsEvent.Deleted(RoomMembershipRole.OWNER),
            RoomAnalyticsEvent.Updated(RoomMembershipRole.OWNER),
        ).map { it.toAnalyticsEvent() }
        assertEquals(listOf("room_list_viewed", "room_detail_viewed", "room_created", "room_joined",
            "room_home_viewed", "room_records_viewed", "room_left", "room_deleted", "room_updated"), events.map { it.name.wireName })
        assertEquals(AnalyticsParameterValue.Text("weekly"), events[2].parameters[AnalyticsParameterName.ScheduleType])
        assertEquals(AnalyticsParameterValue.Text("owner"), events[7].parameters[AnalyticsParameterName.MembershipRole])
        assertEquals(mapOf(AnalyticsParameterName.MembershipRole to AnalyticsParameterValue.Text("owner")), events[8].parameters)
        assertFalse(events[0].parameters.containsKey(AnalyticsParameterName.JoinedRoomCount))
        assertFalse(events[5].parameters.containsKey(AnalyticsParameterName.MemberCount))
        assertFalse(events.flatMap { it.parameters.keys }.any { it.wireName.endsWith("_id") })
    }

    @Test
    fun `화면 갱신은 조회 이벤트를 중복 기록하지 않고 새 진입은 기록한다`() {
        var count = 0
        val visit = RoomAnalyticsVisit()
        visit.recordWhen { false }
        visit.recordOnce { count++ }
        visit.recordOnce { count++ }
        RoomAnalyticsVisit().recordOnce { count++ }
        assertEquals(2, count)
    }
}
