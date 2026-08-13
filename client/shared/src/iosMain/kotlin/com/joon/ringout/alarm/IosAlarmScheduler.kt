package com.joon.ringout.alarm

import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

enum class IosAlarmOperationCode {
    SUCCESS,
    INVALID_ID,
    DENIED,
    NOT_FOUND,
    SDK_ERROR,
    UNKNOWN_ERROR,
}

data class IosAlarmOperationResult(
    val code: IosAlarmOperationCode,
    val message: String? = null,
) {
    val isSuccess: Boolean
        get() = code == IosAlarmOperationCode.SUCCESS
}

data class IosAlarmAuthorizationResult(
    val state: IosAlarmAuthorizationState,
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
) {
    val isSuccess: Boolean
        get() = code == IosAlarmOperationCode.SUCCESS
}

data class IosAlarmScheduleDto(
    val alarmId: String,
    val hour: Int,
    val minute: Int,
    val isoWeekdays: List<Int>,
    val repeats: Boolean,
    val title: String,
    val destinationName: String,
    val limitMinutes: Int,
    val soundName: String,
)

enum class IosScheduledAlarmState {
    SCHEDULED,
    ALERTING,
    COUNTDOWN,
    PAUSED,
    DISABLED,
    UNKNOWN,
}

data class IosScheduledAlarmDto(
    val alarmId: String,
    val state: IosScheduledAlarmState,
)

data class IosScheduledAlarmsResult(
    val alarms: List<IosScheduledAlarmDto> = emptyList(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

interface IosAlarmScheduler {
    fun authorizationState(): IosAlarmAuthorizationState

    fun requestAuthorization(callback: (IosAlarmAuthorizationResult) -> Unit)

    fun schedule(
        request: IosAlarmScheduleDto,
        callback: (IosAlarmOperationResult) -> Unit,
    )

    fun cancel(
        alarmId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    )

    fun scheduledAlarms(callback: (IosScheduledAlarmsResult) -> Unit)
}

internal suspend fun IosAlarmScheduler.requestAuthorizationAwait():
    IosAlarmAuthorizationResult =
    awaitSingleCallback { callback -> requestAuthorization(callback) }

internal suspend fun IosAlarmScheduler.scheduleAwait(request: AlarmScheduleRequest) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        schedule(request.toIosAlarmScheduleDto(), callback)
    }
    result.requireAlarmKitSuccess("AlarmKit 알람을 예약하지 못했습니다.")
}

internal suspend fun IosAlarmScheduler.cancelAwait(alarmId: String) {
    val result = awaitSingleCallback<IosAlarmOperationResult> { callback ->
        cancel(alarmId, callback)
    }
    if (result.code == IosAlarmOperationCode.NOT_FOUND) return
    result.requireAlarmKitSuccess("AlarmKit 알람을 해제하지 못했습니다.")
}

internal suspend fun IosAlarmScheduler.scheduledAlarmsAwait(): List<IosScheduledAlarmDto> {
    val result = awaitSingleCallback<IosScheduledAlarmsResult> { callback ->
        scheduledAlarms(callback)
    }
    IosAlarmOperationResult(result.code, result.message)
        .requireAlarmKitSuccess("AlarmKit 예약 목록을 읽지 못했습니다.")
    return result.alarms
}

internal fun AlarmScheduleRequest.toIosAlarmScheduleDto(): IosAlarmScheduleDto {
    val (hour, minute) = parseAlarmTime()
    return IosAlarmScheduleDto(
        alarmId = id,
        hour = hour,
        minute = minute,
        isoWeekdays = selectedDays
            .mapNotNull(KoreanDayToIsoWeekday::get)
            .distinct()
            .sorted(),
        repeats = repeatEnabled,
        title = destinationName.ifBlank { "Ringout" },
        destinationName = destinationName,
        limitMinutes = limitMinutes,
        soundName = alarmSoundName,
    )
}

internal fun AlarmScheduleRequest.parseAlarmTime(): Pair<Int, Int> {
    val parts = time.split(":")
    require(parts.size == 2) { "Alarm time must use HH:mm format: $time" }
    val hour = parts[0].toIntOrNull()
    val minute = parts[1].toIntOrNull()
    require(hour != null && hour in 0..23 && minute != null && minute in 0..59) {
        "Alarm time must use HH:mm format: $time"
    }
    return hour to minute
}

internal fun IosAlarmOperationResult.requireAlarmKitSuccess(defaultMessage: String) {
    if (isSuccess) return
    throw IosAlarmOperationException(code, message ?: defaultMessage)
}

internal class IosAlarmOperationException(
    val code: IosAlarmOperationCode,
    message: String,
) : IllegalStateException(message)

internal suspend fun <T> awaitSingleCallback(
    register: (((T) -> Unit)) -> Unit,
): T = suspendCancellableCoroutine { continuation ->
    var completed = false
    fun complete(block: () -> Unit) {
        if (completed || !continuation.isActive) return
        completed = true
        block()
    }
    try {
        register { value ->
            complete { continuation.resume(value) }
        }
    } catch (error: CancellationException) {
        complete { continuation.resumeWithException(error) }
    } catch (error: Throwable) {
        complete { continuation.resumeWithException(error) }
    }
}

private val KoreanDayToIsoWeekday = mapOf(
    "월" to 1,
    "화" to 2,
    "수" to 3,
    "목" to 4,
    "금" to 5,
    "토" to 6,
    "일" to 7,
)
