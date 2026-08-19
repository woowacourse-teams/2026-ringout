package com.joon.ringout.analytics

import com.joon.ringout.platform.IosAnalyticsEventDto
import com.joon.ringout.platform.IosAnalyticsParameterDto
import com.joon.ringout.platform.IosAnalyticsTracker
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

interface IosAlarmAnalyticsRecorder {
    fun recordAlarmRingingStarted(occurrenceId: String, retryAttempt: Int)
    fun recordMissionStarted(occurrenceId: String, retryAttempt: Int)
    fun recordMissionCompleted(occurrenceId: String, retryAttempt: Int, startedAtEpochMillis: Long)
    fun recordMissionExpired(occurrenceId: String, retryAttempt: Int, startedAtEpochMillis: Long)
    fun recordMissionForceEnded(occurrenceId: String, retryAttempt: Int, startedAtEpochMillis: Long)
    fun recordForceEndHoldStarted(occurrenceId: String, retryAttempt: Int)
    fun recordForceEndHoldCancelled(occurrenceId: String, holdDurationMillis: Long)
    fun recordForceEndHoldCompleted(occurrenceId: String, holdDurationMillis: Long)
}

internal class IosAlarmAnalytics(
    private val tracker: IosAnalyticsTracker,
    private val usageStore: IosAnalyticsUsageStore = IosAnalyticsUsageStore(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : IosAlarmAnalyticsRecorder {
    private val forceEndHoldAttempts = mutableMapOf<String, Int>()

    fun recordAlarmCreated(
        alarmId: String,
        repeatEnabled: Boolean,
        repeatDayCount: Int,
    ) = safelyRecord {
        val creationIndex = usageStore.claimAlarmCreation(alarmId) ?: return@safelyRecord
        val normalizedRepeatDayCount = if (repeatEnabled) repeatDayCount.coerceIn(0, 7) else 0
        tracker.log(
            analyticsEvent(
                name = DestinationAlarmCreated,
                numberParameters = mapOf(
                    CreationIndex to creationIndex,
                    RepeatDayCount to normalizedRepeatDayCount.toLong(),
                ),
                textParameters = mapOf(
                    ScheduleType to if (normalizedRepeatDayCount > 0) Weekly else Once,
                ),
            ),
        )
    }

    override fun recordAlarmRingingStarted(
        occurrenceId: String,
        retryAttempt: Int,
    ) = recordOccurrenceEvent(
        name = DestinationAlarmRingingStarted,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        needsUseIndex = false,
    )

    override fun recordMissionStarted(
        occurrenceId: String,
        retryAttempt: Int,
    ) = safelyRecord {
        val useIndex = usageStore.getOrCreateUseIndex(occurrenceId) ?: return@safelyRecord
        if (!usageStore.claimEvent(DestinationMissionStarted, occurrenceId)) return@safelyRecord
        tracker.log(
            analyticsEvent(
                name = DestinationMissionStarted,
                numberParameters = mapOf(
                    UseIndex to useIndex,
                    RetryAttempt to retryAttempt.coerceAtLeast(0).toLong(),
                ),
            ),
        )
    }

    override fun recordMissionCompleted(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        name = DestinationMissionCompleted,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    override fun recordMissionExpired(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        name = DestinationMissionExpired,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    override fun recordMissionForceEnded(
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = recordMissionOutcome(
        name = DestinationMissionForceEnded,
        occurrenceId = occurrenceId,
        retryAttempt = retryAttempt,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    override fun recordForceEndHoldStarted(
        occurrenceId: String,
        retryAttempt: Int,
    ) = safelyRecord {
        if (occurrenceId in forceEndHoldAttempts) return@safelyRecord
        forceEndHoldAttempts[occurrenceId] = retryAttempt
        recordForceEndHoldInteraction(
            name = ForceEndHoldStarted,
            occurrenceId = occurrenceId,
            retryAttempt = retryAttempt,
        )
    }

    override fun recordForceEndHoldCancelled(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) = finishForceEndHold(
        name = ForceEndHoldCancelled,
        occurrenceId = occurrenceId,
        holdDurationMillis = holdDurationMillis,
    )

    override fun recordForceEndHoldCompleted(
        occurrenceId: String,
        holdDurationMillis: Long,
    ) = finishForceEndHold(
        name = ForceEndHoldCompleted,
        occurrenceId = occurrenceId,
        holdDurationMillis = holdDurationMillis,
    )

    private fun finishForceEndHold(
        name: String,
        occurrenceId: String,
        holdDurationMillis: Long,
    ) = safelyRecord {
        val retryAttempt = forceEndHoldAttempts.remove(occurrenceId) ?: return@safelyRecord
        recordForceEndHoldInteraction(
            name = name,
            occurrenceId = occurrenceId,
            retryAttempt = retryAttempt,
            holdDurationMillis = holdDurationMillis,
        )
    }

    private fun recordForceEndHoldInteraction(
        name: String,
        occurrenceId: String,
        retryAttempt: Int,
        holdDurationMillis: Long? = null,
    ) {
        val useIndex = usageStore.findUseIndex(occurrenceId) ?: return
        tracker.log(
            analyticsEvent(
                name = name,
                numberParameters = buildMap {
                    put(UseIndex, useIndex)
                    put(RetryAttempt, retryAttempt.coerceAtLeast(0).toLong())
                    holdDurationMillis?.let {
                        put(HoldDurationMillis, it.coerceAtLeast(0L))
                    }
                },
            ),
        )
    }

    private fun recordOccurrenceEvent(
        name: String,
        occurrenceId: String,
        retryAttempt: Int,
        needsUseIndex: Boolean,
    ) = safelyRecord {
        if (!usageStore.claimEvent(name, occurrenceId)) return@safelyRecord
        val parameters = mutableMapOf(
            RetryAttempt to retryAttempt.coerceAtLeast(0).toLong(),
        )
        if (needsUseIndex) {
            parameters[UseIndex] = usageStore.findUseIndex(occurrenceId) ?: return@safelyRecord
        }
        tracker.log(analyticsEvent(name = name, numberParameters = parameters))
    }

    private fun recordMissionOutcome(
        name: String,
        occurrenceId: String,
        retryAttempt: Int,
        startedAtEpochMillis: Long,
    ) = safelyRecord {
        val useIndex = usageStore.findUseIndex(occurrenceId) ?: return@safelyRecord
        if (!usageStore.claimEvent(name, occurrenceId)) return@safelyRecord
        val elapsedMillis = (nowEpochMillis() - startedAtEpochMillis).coerceAtLeast(0L)
        tracker.log(
            analyticsEvent(
                name = name,
                numberParameters = mapOf(
                    UseIndex to useIndex,
                    RetryAttempt to retryAttempt.coerceAtLeast(0).toLong(),
                ),
                textParameters = mapOf(ElapsedBucket to elapsedBucket(elapsedMillis)),
            ),
        )
    }

    private inline fun safelyRecord(action: () -> Unit) {
        runCatching(action)
    }
}

internal class IosAnalyticsUsageStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ProductAnalyticsUsageStore {
    fun claimAlarmCreation(alarmId: String): Long? {
        val claimedKey = key("created", alarmId)
        if (preferences.objectForKey(claimedKey) != null) return null
        val nextIndex = preferences.integerForKey(CreationCounterKey) + 1L
        preferences.setInteger(nextIndex, CreationCounterKey)
        preferences.setBool(true, claimedKey)
        return nextIndex
    }

    override fun claimDestinationCreation(destinationKey: String): Long? {
        val claimedKey = key("destination_created", destinationKey)
        if (preferences.objectForKey(claimedKey) != null) return null
        val nextIndex = preferences.integerForKey(DestinationCreationCounterKey) + 1L
        preferences.setInteger(nextIndex, DestinationCreationCounterKey)
        preferences.setBool(true, claimedKey)
        return nextIndex
    }

    fun getOrCreateUseIndex(occurrenceId: String): Long? {
        val useIndexKey = key("use", rootOccurrenceId(occurrenceId))
        if (preferences.objectForKey(useIndexKey) != null) {
            return preferences.integerForKey(useIndexKey).takeIf { it > 0L }
        }
        val nextIndex = preferences.integerForKey(UseCounterKey) + 1L
        preferences.setInteger(nextIndex, UseCounterKey)
        preferences.setInteger(nextIndex, useIndexKey)
        return nextIndex
    }

    fun findUseIndex(occurrenceId: String): Long? =
        preferences.integerForKey(key("use", rootOccurrenceId(occurrenceId)))
            .takeIf { it > 0L }

    fun claimEvent(eventName: String, occurrenceId: String): Boolean {
        val claimedKey = key("event_$eventName", occurrenceId)
        if (preferences.objectForKey(claimedKey) != null) return false
        preferences.setBool(true, claimedKey)
        return true
    }

    private fun key(prefix: String, id: String): String =
        "$PreferencesPrefix.$prefix.$id"

    private companion object {
        const val PreferencesPrefix = "ringout.analytics"
        const val CreationCounterKey = "$PreferencesPrefix.creation_counter"
        const val DestinationCreationCounterKey =
            "$PreferencesPrefix.destination_creation_counter"
        const val UseCounterKey = "$PreferencesPrefix.use_counter"
    }
}

private fun analyticsEvent(
    name: String,
    numberParameters: Map<String, Long> = emptyMap(),
    textParameters: Map<String, String> = emptyMap(),
): IosAnalyticsEventDto = IosAnalyticsEventDto(
    name = name,
    parameters = buildList {
        numberParameters.forEach { (parameterName, value) ->
            add(IosAnalyticsParameterDto(name = parameterName, numberValue = value))
        }
        textParameters.forEach { (parameterName, value) ->
            add(IosAnalyticsParameterDto(name = parameterName, textValue = value))
        }
    },
)

private fun rootOccurrenceId(occurrenceId: String): String =
    occurrenceId.substringBefore(":retry-")

private fun elapsedBucket(elapsedMillis: Long): String = when {
    elapsedMillis < 5L * 60_000L -> "under_5m"
    elapsedMillis < 15L * 60_000L -> "5_to_15m"
    elapsedMillis < 30L * 60_000L -> "15_to_30m"
    else -> "over_30m"
}

private const val DestinationAlarmCreated = "destination_alarm_created"
private const val DestinationAlarmRingingStarted = "destination_alarm_ringing_started"
private const val DestinationMissionStarted = "destination_mission_started"
private const val DestinationMissionCompleted = "destination_mission_completed"
private const val DestinationMissionExpired = "destination_mission_expired"
private const val DestinationMissionForceEnded = "destination_mission_force_ended"
private const val ForceEndHoldStarted = "force_end_hold_started"
private const val ForceEndHoldCancelled = "force_end_hold_cancelled"
private const val ForceEndHoldCompleted = "force_end_hold_completed"

private const val CreationIndex = "creation_index"
private const val UseIndex = "use_index"
private const val RetryAttempt = "retry_attempt"
private const val ScheduleType = "schedule_type"
private const val RepeatDayCount = "repeat_day_count"
private const val ElapsedBucket = "elapsed_bucket"
private const val HoldDurationMillis = "hold_duration_ms"

private const val Once = "once"
private const val Weekly = "weekly"
