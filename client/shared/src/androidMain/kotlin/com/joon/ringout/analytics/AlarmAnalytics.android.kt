package com.joon.ringout.analytics

import android.content.Context
import android.util.Log

internal class AlarmAnalytics internal constructor(
    private val tracker: AnalyticsTracker,
    private val usageStore: AnalyticsUsageStore,
    private val nowEpochMillis: () -> Long,
) {
    constructor(context: Context) : this(
        tracker = FirebaseAnalyticsTracker(context),
        usageStore = AnalyticsUsageStore(context),
        nowEpochMillis = System::currentTimeMillis,
    )

    fun recordAlarmCreated(
        alarmId: String,
        repeatEnabled: Boolean,
        repeatDayCount: Int,
    ) = safelyRecord {
        val creationIndex = usageStore.claimAlarmCreation(alarmId)
            ?: return@safelyRecord
        val normalizedRepeatDayCount = if (repeatEnabled) {
            repeatDayCount.coerceIn(0, 7)
        } else {
            0
        }
        val scheduleType = analyticsScheduleType(
            repeatEnabled = repeatEnabled,
            repeatDayCount = normalizedRepeatDayCount,
        )
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationAlarmCreated,
                parameters = mapOf(
                    AnalyticsParameterName.CreationIndex to
                        AnalyticsParameterValue.Number(creationIndex),
                    AnalyticsParameterName.ScheduleType to
                        AnalyticsParameterValue.Text(scheduleType.wireName),
                    AnalyticsParameterName.RepeatDayCount to
                        AnalyticsParameterValue.Number(normalizedRepeatDayCount.toLong()),
                ),
            ),
        )
    }

    fun recordAlarmRingingStarted(
        occurrenceId: String,
        retryAttempt: Int,
        scheduleType: AnalyticsScheduleType? = null,
    ) = safelyRecord {
        if (
            !usageStore.claimEvent(
                eventName = AnalyticsEventName.DestinationAlarmRingingStarted,
                occurrenceId = occurrenceId,
            )
        ) {
            return@safelyRecord
        }
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationAlarmRingingStarted,
                parameters = buildMap {
                    put(
                        AnalyticsParameterName.RetryAttempt,
                        AnalyticsParameterValue.Number(retryAttempt.coerceAtLeast(0).toLong()),
                    )
                    scheduleType?.let { type ->
                        put(
                            AnalyticsParameterName.ScheduleType,
                            AnalyticsParameterValue.Text(type.wireName),
                        )
                    }
                },
            ),
        )
    }

    fun recordMissionStarted(
        occurrenceId: String,
        retryAttempt: Int,
        scheduleType: AnalyticsScheduleType? = null,
    ) = safelyRecord {
        val useIndex = usageStore.getOrCreateUseIndex(occurrenceId)
            ?: return@safelyRecord
        if (
            !usageStore.claimEvent(
                eventName = AnalyticsEventName.DestinationMissionStarted,
                occurrenceId = occurrenceId,
            )
        ) {
            return@safelyRecord
        }
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationMissionStarted,
                parameters = buildMap {
                    put(
                        AnalyticsParameterName.UseIndex,
                        AnalyticsParameterValue.Number(useIndex),
                    )
                    put(
                        AnalyticsParameterName.RetryAttempt,
                        AnalyticsParameterValue.Number(retryAttempt.coerceAtLeast(0).toLong()),
                    )
                    scheduleType?.let { type ->
                        put(
                            AnalyticsParameterName.ScheduleType,
                            AnalyticsParameterValue.Text(type.wireName),
                        )
                    }
                },
            ),
        )
    }

    fun recordMissionCompleted(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        eventName = AnalyticsEventName.DestinationMissionCompleted,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    fun recordMissionExpired(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        eventName = AnalyticsEventName.DestinationMissionExpired,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    fun recordMissionForceEnded(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        eventName = AnalyticsEventName.DestinationMissionForceEnded,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    fun recordForceEndHoldStarted(
        occurrenceId: String,
        retryAttempt: Int,
    ) = recordForceEndHoldInteraction(
        eventName = AnalyticsEventName.ForceEndHoldStarted,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
    )

    fun recordForceEndHoldCancelled(
        occurrenceId: String,
        retryAttempt: Int,
        holdDurationMillis: Long,
    ) = recordForceEndHoldInteraction(
        eventName = AnalyticsEventName.ForceEndHoldCancelled,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        holdDurationMillis = holdDurationMillis,
    )

    fun recordForceEndHoldCompleted(
        occurrenceId: String,
        retryAttempt: Int,
        holdDurationMillis: Long,
    ) = recordForceEndHoldInteraction(
        eventName = AnalyticsEventName.ForceEndHoldCompleted,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        holdDurationMillis = holdDurationMillis,
    )

    private fun recordForceEndHoldInteraction(
        eventName: AnalyticsEventName,
        occurrenceId: String,
        retryAttempt: Int,
        holdDurationMillis: Long? = null,
    ) = safelyRecord {
        val useIndex = usageStore.findUseIndex(occurrenceId)
            ?: return@safelyRecord
        tracker.log(
            AnalyticsEvent(
                name = eventName,
                parameters = buildMap {
                    put(
                        AnalyticsParameterName.UseIndex,
                        AnalyticsParameterValue.Number(useIndex),
                    )
                    put(
                        AnalyticsParameterName.RetryAttempt,
                        AnalyticsParameterValue.Number(retryAttempt.coerceAtLeast(0).toLong()),
                    )
                    holdDurationMillis?.let { durationMillis ->
                        put(
                            AnalyticsParameterName.HoldDurationMillis,
                            AnalyticsParameterValue.Number(durationMillis.coerceAtLeast(0L)),
                        )
                    }
                },
            ),
        )
    }

    private fun recordMissionOutcome(
        eventName: AnalyticsEventName,
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = safelyRecord {
        val useIndex = usageStore.findUseIndex(occurrenceId)
            ?: return@safelyRecord
        if (!usageStore.claimEvent(eventName, occurrenceId)) {
            return@safelyRecord
        }
        val now = nowEpochMillis()
        val elapsedMillis = if (now >= startedAtEpochMillis) {
            now - startedAtEpochMillis
        } else {
            0L
        }
        tracker.log(
            AnalyticsEvent(
                name = eventName,
                parameters = mapOf(
                    AnalyticsParameterName.UseIndex to AnalyticsParameterValue.Number(useIndex),
                    AnalyticsParameterName.RetryAttempt to
                        AnalyticsParameterValue.Number(retryAttempt.coerceAtLeast(0).toLong()),
                    AnalyticsParameterName.ElapsedBucket to
                        AnalyticsParameterValue.Text(analyticsElapsedBucket(elapsedMillis)),
                ),
            ),
        )
    }

    private inline fun safelyRecord(action: () -> Unit) {
        runCatching(action).onFailure { error ->
            Log.w(LogTag, "Firebase Analytics 이벤트를 기록하지 못했습니다.", error)
        }
    }

    private companion object {
        const val LogTag = "RingoutAnalytics"
    }
}
