package com.joon.ringout.analytics

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomMembershipRole

/** 분석에 필요한 범주와 수치만 전달한다. 모임 ID와 사용자 입력 문자열은 전송하지 않는다. */
sealed interface RoomAnalyticsEvent {
    data class ListViewed(val loginState: AuthSessionState, val joinedRoomCount: Int? = null) : RoomAnalyticsEvent
    data class DetailViewed(val memberCount: Int) : RoomAnalyticsEvent
    data class Created(val repeatDayCount: Int) : RoomAnalyticsEvent
    data class Joined(val memberCount: Int?) : RoomAnalyticsEvent
    data class HomeViewed(val memberCount: Int, val role: RoomMembershipRole) : RoomAnalyticsEvent
    data class RecordsViewed(val memberCount: Int?) : RoomAnalyticsEvent
    data class Left(val role: RoomMembershipRole) : RoomAnalyticsEvent
    data class Deleted(val role: RoomMembershipRole) : RoomAnalyticsEvent
}

internal fun RoomAnalyticsEvent.toAnalyticsEvent(): AnalyticsEvent {
    val parameters = mutableMapOf<AnalyticsParameterName, AnalyticsParameterValue>()
    fun number(name: AnalyticsParameterName, value: Int?) {
        if (value != null && value >= 0) parameters[name] = AnalyticsParameterValue.Number(value.toLong())
    }
    fun text(name: AnalyticsParameterName, value: String) { parameters[name] = AnalyticsParameterValue.Text(value) }
    val name = when (this) {
        is RoomAnalyticsEvent.ListViewed -> {
            text(AnalyticsParameterName.LoginState, when (loginState) {
                AuthSessionState.Authenticated -> "logged_in"
                AuthSessionState.Unauthenticated -> "logged_out"
                AuthSessionState.Restoring, AuthSessionState.ReauthenticationRequired -> "unknown"
            })
            number(AnalyticsParameterName.JoinedRoomCount, joinedRoomCount)
            AnalyticsEventName.RoomListViewed
        }
        is RoomAnalyticsEvent.DetailViewed -> {
            number(AnalyticsParameterName.MemberCount, memberCount)
            text(AnalyticsParameterName.EntrySource, "room_list")
            AnalyticsEventName.RoomDetailViewed
        }
        is RoomAnalyticsEvent.Created -> {
            number(AnalyticsParameterName.RepeatDayCount, repeatDayCount)
            text(AnalyticsParameterName.ScheduleType, if (repeatDayCount > 0) "weekly" else "once")
            AnalyticsEventName.RoomCreated
        }
        is RoomAnalyticsEvent.Joined -> {
            number(AnalyticsParameterName.MemberCount, memberCount)
            text(AnalyticsParameterName.EntrySource, "room_detail")
            AnalyticsEventName.RoomJoined
        }
        is RoomAnalyticsEvent.HomeViewed -> {
            number(AnalyticsParameterName.MemberCount, memberCount)
            text(AnalyticsParameterName.MembershipRole, role.name.lowercase())
            AnalyticsEventName.RoomHomeViewed
        }
        is RoomAnalyticsEvent.RecordsViewed -> {
            number(AnalyticsParameterName.MemberCount, memberCount)
            AnalyticsEventName.RoomRecordsViewed
        }
        is RoomAnalyticsEvent.Left -> {
            text(AnalyticsParameterName.MembershipRole, role.name.lowercase())
            AnalyticsEventName.RoomLeft
        }
        is RoomAnalyticsEvent.Deleted -> {
            text(AnalyticsParameterName.MembershipRole, role.name.lowercase())
            AnalyticsEventName.RoomDeleted
        }
    }
    return AnalyticsEvent(name, parameters)
}

/** LaunchedEffect의 재실행과 데이터 갱신을 실제 화면 재진입과 구분한다. */
internal class RoomAnalyticsVisit {
    private var recorded = false
    fun recordWhen(record: () -> Boolean) {
        if (!recorded) recorded = runCatching(record).getOrDefault(false)
    }
    fun recordOnce(record: () -> Unit) {
        if (recorded) return
        recorded = true
        runCatching(record)
    }
}
