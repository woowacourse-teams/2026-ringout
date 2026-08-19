package com.joon.ringout.analytics

internal suspend fun completeAccountWithdrawal(
    withdraw: suspend () -> Unit,
    logout: suspend () -> Unit,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
) {
    withdraw()
    runCatching { productAnalyticsRecorder.recordAccountWithdrawalCompleted() }
    logout()
}
