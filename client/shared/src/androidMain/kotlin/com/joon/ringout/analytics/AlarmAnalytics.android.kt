package com.joon.ringout.analytics

import android.content.Context
import android.util.Log
import com.joon.ringout.alarm.AlarmScheduleRequest

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
        request: AlarmScheduleRequest,
        context: AlarmSettingsAnalyticsContext,
    ) = recordAlarmSave(
        eventName = AnalyticsEventName.DestinationAlarmCreated,
        request = request,
        context = context,
        claimCreationIndex = { usageStore.claimAlarmCreation(request.id) },
    )

    fun recordAlarmUpdated(
        request: AlarmScheduleRequest,
        context: AlarmSettingsAnalyticsContext,
    ) = recordAlarmSave(
        eventName = AnalyticsEventName.DestinationAlarmUpdated,
        request = request,
        context = context,
        claimCreationIndex = null,
    )

    private fun recordAlarmSave(
        eventName: AnalyticsEventName,
        request: AlarmScheduleRequest,
        context: AlarmSettingsAnalyticsContext,
        claimCreationIndex: (() -> Long?)?,
    ) = safelyRecord {
        val payload = normalizeAlarmSettingsAnalytics(request, context)
            ?: return@safelyRecord
        val creationIndex = claimCreationIndex?.invoke()
        if (claimCreationIndex != null && creationIndex == null) {
            return@safelyRecord
        }
        tracker.log(
            AnalyticsEvent(
                name = eventName,
                parameters = buildMap {
                    creationIndex?.let { index ->
                        put(
                            AnalyticsParameterName.CreationIndex,
                            AnalyticsParameterValue.Number(index),
                        )
                    }
                    putAlarmSettings(payload)
                },
            ),
        )
    }

    private fun MutableMap<AnalyticsParameterName, AnalyticsParameterValue>.putAlarmSettings(
        payload: AlarmSettingsAnalyticsPayload,
    ) {
        put(
            AnalyticsParameterName.SettingsSchemaVersion,
            AnalyticsParameterValue.Number(SettingsSchemaVersion),
        )
        put(
            AnalyticsParameterName.LimitMinutes,
            AnalyticsParameterValue.Number(payload.limitMinutes.toLong()),
        )
        put(
            AnalyticsParameterName.AlarmTime,
            AnalyticsParameterValue.Text(payload.alarmTime),
        )
        put(
            AnalyticsParameterName.RepeatDays,
            AnalyticsParameterValue.Text(payload.repeatDays),
        )
        put(
            AnalyticsParameterName.ScheduleType,
            AnalyticsParameterValue.Text(payload.scheduleType.wireName),
        )
        put(
            AnalyticsParameterName.RepeatDayCount,
            AnalyticsParameterValue.Number(payload.repeatDayCount.toLong()),
        )
        put(
            AnalyticsParameterName.AlarmSoundSource,
            AnalyticsParameterValue.Text(payload.alarmSoundSource.wireName),
        )
        val displaySelection = payload.soundDisplaySelection
        put(
            AnalyticsParameterName.AlarmSoundListConfirmed,
            AnalyticsParameterValue.Number(if (displaySelection == null) 0L else 1L),
        )
        displaySelection?.let { selection ->
            put(
                AnalyticsParameterName.AlarmSoundSurface,
                AnalyticsParameterValue.Text(selection.surface.wireName),
            )
            put(
                AnalyticsParameterName.AlarmSoundPosition,
                AnalyticsParameterValue.Number(selection.position.toLong()),
            )
            put(
                AnalyticsParameterName.AlarmSoundListSize,
                AnalyticsParameterValue.Number(selection.listSize.toLong()),
            )
            put(
                AnalyticsParameterName.AlarmSoundSelectionChanged,
                AnalyticsParameterValue.Number(if (selection.selectionChanged) 1L else 0L),
            )
        }
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
        const val SettingsSchemaVersion = 2L
    }
}
