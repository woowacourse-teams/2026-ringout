package com.joon.ringout.alarm

data class IosMissionArrivalRequest(
    val chainId: String,
    val occurrenceId: String,
    val fallbackDestinationName: String? = null,
)

enum class IosMissionArrivalResult {
    HANDLED,
    PERMISSION_SKIPPED,
    ALREADY_HANDLED,
    MISSING_CHAIN,
    CLAIM_STORAGE_FAILED,
    REQUEST_FAILED,
}

interface IosMissionArrivalNotifier {
    fun notify(request: IosMissionArrivalRequest, callback: (IosMissionArrivalResult) -> Unit)
}

internal suspend fun IosMissionArrivalNotifier.notifyAwait(
    request: IosMissionArrivalRequest,
): IosMissionArrivalResult = awaitSingleCallback { callback -> notify(request, callback) }

internal object NoOpIosMissionArrivalNotifier : IosMissionArrivalNotifier {
    override fun notify(request: IosMissionArrivalRequest, callback: (IosMissionArrivalResult) -> Unit) {
        callback(IosMissionArrivalResult.ALREADY_HANDLED)
    }
}
