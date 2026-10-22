package com.joon.ringout.alarm

/** App commands bypass legacy event import; Intent recovery shares the native journal. */
interface IosAlarmMissionCommands {
    fun dismissRingingAlarm(request: IosAlarmDismissRequest, callback: (IosAlarmDismissResult) -> Unit)

    /** Reads one journal snapshot without importing events or submitting reservations. */
    fun readRecoverySnapshot(callback: (IosAlarmRecoveryAttemptsResult) -> Unit)
}

data class IosAlarmDismissRequest(
    val systemAlarmId: String,
    val sourceAlarmId: String,
    val occurrenceId: String?,
    val retryAttempt: Int,
    val scheduleVersion: Long,
    val chainId: String?,
)

enum class IosAlarmDismissDisposition { HANDLED, FALLBACK, PRESERVED, FAILED }

data class IosAlarmDismissResult(
    val osStopCode: IosAlarmOperationCode,
    val disposition: IosAlarmDismissDisposition,
    val recovery: IosAlarmRecoveryAttemptsResult,
    val occurrenceId: String?,
    val code: IosAlarmOperationCode,
    val message: String? = null,
)

internal suspend fun IosAlarmMissionCommands.dismissRingingAlarmAwait(request: IosAlarmDismissRequest) =
    awaitSingleCallback<IosAlarmDismissResult> { callback -> dismissRingingAlarm(request, callback) }

internal suspend fun IosAlarmMissionCommands.readRecoverySnapshotAwait(): IosAlarmRecoveryAttemptsResult {
    val result = awaitSingleCallback<IosAlarmRecoveryAttemptsResult> { readRecoverySnapshot(it) }
    check(result.code == IosAlarmOperationCode.SUCCESS) { result.message ?: "Could not read alarm journal" }
    return result
}
