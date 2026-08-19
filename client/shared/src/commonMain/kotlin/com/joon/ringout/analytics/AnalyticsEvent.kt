package com.joon.ringout.analytics

internal enum class AnalyticsEventName(
    val wireName: String,
) {
    DestinationAlarmCreated("destination_alarm_created"),
    DestinationAlarmRingingStarted("destination_alarm_ringing_started"),
    DestinationMissionStarted("destination_mission_started"),
    DestinationMissionCompleted("destination_mission_completed"),
    DestinationMissionExpired("destination_mission_expired"),
    DestinationMissionForceEnded("destination_mission_force_ended"),
    ForceEndHoldStarted("force_end_hold_started"),
    ForceEndHoldCancelled("force_end_hold_cancelled"),
    ForceEndHoldCompleted("force_end_hold_completed"),
    DestinationCreated("destination_created"),
    DestinationSelected("destination_selected"),
    StampCalendarViewed("stamp_calendar_viewed"),
    StampMonthChanged("stamp_month_changed"),
    AccountWithdrawalCompleted("account_withdrawal_completed"),
    LoginStarted("login_started"),
    LoginCompleted("login_completed"),
    SignupCompleted("signup_completed"),
}

internal enum class AnalyticsParameterName(
    val wireName: String,
) {
    CreationIndex("creation_index"),
    UseIndex("use_index"),
    RetryAttempt("retry_attempt"),
    ScheduleType("schedule_type"),
    RepeatDayCount("repeat_day_count"),
    ElapsedBucket("elapsed_bucket"),
    HoldDurationMillis("hold_duration_ms"),
    LoginState("login_state"),
    Source("source"),
    Year("year"),
    Month("month"),
    Direction("direction"),
    Provider("provider"),
    IsNewUser("is_new_user"),
}

internal sealed interface AnalyticsParameterValue {
    data class Text(val value: String) : AnalyticsParameterValue

    data class Number(val value: Long) : AnalyticsParameterValue
}

internal data class AnalyticsEvent(
    val name: AnalyticsEventName,
    val parameters: Map<AnalyticsParameterName, AnalyticsParameterValue>,
)

internal enum class AnalyticsScheduleType(
    val wireName: String,
) {
    Once("once"),
    Weekly("weekly"),
}

internal fun analyticsScheduleType(
    repeatEnabled: Boolean,
    repeatDayCount: Int,
): AnalyticsScheduleType =
    if (repeatEnabled && repeatDayCount > 0) {
        AnalyticsScheduleType.Weekly
    } else {
        AnalyticsScheduleType.Once
    }

internal fun analyticsElapsedBucket(elapsedMillis: Long): String = when {
    elapsedMillis < 5L * 60_000L -> "under_5m"
    elapsedMillis < 15L * 60_000L -> "5_to_15m"
    elapsedMillis < 30L * 60_000L -> "15_to_30m"
    else -> "over_30m"
}
