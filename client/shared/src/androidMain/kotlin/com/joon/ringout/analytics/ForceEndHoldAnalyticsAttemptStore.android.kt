package com.joon.ringout.analytics

internal data class ForceEndHoldAnalyticsAttempt(
    val occurrenceId: String,
    val retryAttempt: Int,
)

internal class ForceEndHoldAnalyticsAttemptStore {
    private val activeAttempts = mutableMapOf<String, ForceEndHoldAnalyticsAttempt>()
    private val lock = Any()

    fun begin(attempt: ForceEndHoldAnalyticsAttempt): Boolean = synchronized(lock) {
        if (attempt.occurrenceId in activeAttempts) return@synchronized false
        activeAttempts[attempt.occurrenceId] = attempt
        true
    }

    fun finish(occurrenceId: String): ForceEndHoldAnalyticsAttempt? = synchronized(lock) {
        activeAttempts.remove(occurrenceId)
    }
}
