package com.joon.ringout.analytics

import com.joon.ringout.alarm.AlarmScheduleRequest

internal enum class AnalyticsEventName(
    val wireName: String,
) {
    TutorialBegin("tutorial_begin"),
    OnboardingStepViewed("onboarding_step_viewed"),
    OnboardingSubmit("onboarding_submit"),
    TutorialComplete("tutorial_complete"),
    DestinationAlarmCreated("destination_alarm_created"),
    DestinationAlarmUpdated("destination_alarm_updated"),
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
    FlowName("flow_name"),
    FlowVersion("flow_version"),
    StepCount("step_count"),
    StepName("step_name"),
    StepIndex("step_index"),
    CreationIndex("creation_index"),
    SettingsSchemaVersion("settings_schema_version"),
    LimitMinutes("limit_minutes"),
    AlarmTime("alarm_time"),
    RepeatDays("repeat_days"),
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
    AlarmSoundSource("alarm_sound_source"),
    AlarmSoundListConfirmed("alarm_sound_list_confirmed"),
    AlarmSoundSurface("alarm_sound_surface"),
    AlarmSoundPosition("alarm_sound_position"),
    AlarmSoundListSize("alarm_sound_list_size"),
    AlarmSoundSelectionChanged("alarm_sound_selection_changed"),
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

internal enum class AnalyticsAlarmSoundSource(
    val wireName: String,
) {
    SystemDefault("system_default"),
    DeviceAlarm("device_alarm"),
}

enum class AnalyticsAlarmSoundSurface(
    val wireName: String,
) {
    EditorPicker("editor_picker"),
    OnboardingStep("onboarding_step"),
}

data class AlarmSoundDisplaySelection(
    val surface: AnalyticsAlarmSoundSurface,
    val position: Int,
    val listSize: Int,
    val selectionChanged: Boolean,
) {
    internal fun isValid(): Boolean =
        position in 1..listSize && listSize >= 1
}

data class AlarmSettingsAnalyticsContext(
    val soundDisplaySelection: AlarmSoundDisplaySelection? = null,
)

internal data class AlarmSettingsAnalyticsPayload(
    val scheduleType: AnalyticsScheduleType,
    val repeatDayCount: Int,
    val limitMinutes: Int,
    val alarmTime: String,
    val repeatDays: String,
    val alarmSoundSource: AnalyticsAlarmSoundSource,
    val soundDisplaySelection: AlarmSoundDisplaySelection?,
)

internal fun normalizeAlarmSettingsAnalytics(
    request: AlarmScheduleRequest,
    context: AlarmSettingsAnalyticsContext,
): AlarmSettingsAnalyticsPayload? {
    val timeParts = request.time.split(":")
    if (
        timeParts.size != 2 ||
        timeParts[0].length != 2 ||
        timeParts[1].length != 2 ||
        timeParts[0].toIntOrNull() !in 0..23 ||
        timeParts[1].toIntOrNull() !in 0..59
    ) {
        return null
    }
    if (request.limitMinutes !in 1..30) return null

    val selectedDays = if (request.repeatEnabled) {
        request.selectedDays
            .toSet()
            .let { selected -> AlarmDayTokens.filter { it.korean in selected }.map(DayToken::token) }
    } else {
        emptyList()
    }
    val displaySelection = context.soundDisplaySelection?.takeIf(AlarmSoundDisplaySelection::isValid)
    return AlarmSettingsAnalyticsPayload(
        scheduleType = analyticsScheduleType(
            repeatEnabled = request.repeatEnabled,
            repeatDayCount = selectedDays.size,
        ),
        repeatDayCount = selectedDays.size,
        limitMinutes = request.limitMinutes,
        alarmTime = request.time,
        repeatDays = selectedDays.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "none",
        alarmSoundSource = if (request.alarmSoundUri == null) {
            AnalyticsAlarmSoundSource.SystemDefault
        } else {
            AnalyticsAlarmSoundSource.DeviceAlarm
        },
        soundDisplaySelection = displaySelection,
    )
}

private data class DayToken(
    val korean: String,
    val token: String,
)

private val AlarmDayTokens = listOf(
    DayToken("월", "mon"),
    DayToken("화", "tue"),
    DayToken("수", "wed"),
    DayToken("목", "thu"),
    DayToken("금", "fri"),
    DayToken("토", "sat"),
    DayToken("일", "sun"),
)

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
