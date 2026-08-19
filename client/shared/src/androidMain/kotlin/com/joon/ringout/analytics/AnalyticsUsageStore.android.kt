package com.joon.ringout.analytics

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

internal class AnalyticsUsageStore internal constructor(
    private val preferences: AnalyticsUsagePreferences,
    private val lock: Any = AnalyticsUsageStoreLock,
) : ProductAnalyticsUsageStore {
    constructor(context: Context) : this(
        preferences = SharedPreferencesAnalyticsUsagePreferences(
            context.applicationContext.getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            ),
        ),
    )

    fun claimAlarmCreation(alarmId: String): Long? = synchronized(lock) {
        val claimedKey = creationClaimKey(alarmId)
        if (preferences.contains(claimedKey)) return@synchronized null

        val nextIndex = preferences.getLong(CreationCounterKey, 0L) + 1L
        val committed = preferences.commit(
            longValues = mapOf(CreationCounterKey to nextIndex),
            trueFlags = setOf(claimedKey),
        )
        nextIndex.takeIf { committed }
    }

    override fun claimDestinationCreation(destinationKey: String): Long? = synchronized(lock) {
        val claimedKey = destinationCreationClaimKey(destinationKey)
        if (preferences.contains(claimedKey)) return@synchronized null

        val nextIndex = preferences.getLong(DestinationCreationCounterKey, 0L) + 1L
        val committed = preferences.commit(
            longValues = mapOf(DestinationCreationCounterKey to nextIndex),
            trueFlags = setOf(claimedKey),
        )
        nextIndex.takeIf { committed }
    }

    fun getOrCreateUseIndex(occurrenceId: String): Long? = synchronized(lock) {
        val useIndexKey = useIndexKey(occurrenceId)
        if (preferences.contains(useIndexKey)) {
            return@synchronized preferences.getLong(useIndexKey, 0L)
                .takeIf { it > 0L }
        }

        val nextIndex = preferences.getLong(UseCounterKey, 0L) + 1L
        val committed = preferences.commit(
            longValues = mapOf(
                UseCounterKey to nextIndex,
                useIndexKey to nextIndex,
            ),
        )
        nextIndex.takeIf { committed }
    }

    fun findUseIndex(occurrenceId: String): Long? = synchronized(lock) {
        val key = useIndexKey(occurrenceId)
        if (!preferences.contains(key)) return@synchronized null
        preferences.getLong(key, 0L).takeIf { it > 0L }
    }

    fun claimEvent(
        eventName: AnalyticsEventName,
        occurrenceId: String,
    ): Boolean = synchronized(lock) {
        val claimedKey = eventClaimKey(eventName, occurrenceId)
        if (preferences.contains(claimedKey)) return@synchronized false
        preferences.commit(trueFlags = setOf(claimedKey))
    }

    private companion object {
        const val PreferencesName = "ringout_analytics_usage"
        const val CreationCounterKey = "creation_counter"
        const val DestinationCreationCounterKey = "destination_creation_counter"
        const val UseCounterKey = "use_counter"

        fun creationClaimKey(alarmId: String): String =
            "created_${analyticsLocalKeyHash(alarmId)}"

        fun destinationCreationClaimKey(destinationKey: String): String =
            "destination_created_${analyticsLocalKeyHash(destinationKey)}"

        fun useIndexKey(occurrenceId: String): String =
            "use_${analyticsLocalKeyHash(analyticsRootOccurrenceId(occurrenceId))}"

        fun eventClaimKey(
            eventName: AnalyticsEventName,
            occurrenceId: String,
        ): String = "event_${eventName.wireName}_${analyticsLocalKeyHash(occurrenceId)}"
    }
}

internal interface AnalyticsUsagePreferences {
    fun contains(key: String): Boolean

    fun getLong(key: String, defaultValue: Long): Long

    fun commit(
        longValues: Map<String, Long> = emptyMap(),
        trueFlags: Set<String> = emptySet(),
    ): Boolean
}

private class SharedPreferencesAnalyticsUsagePreferences(
    private val preferences: SharedPreferences,
) : AnalyticsUsagePreferences {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getLong(key: String, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue)

    override fun commit(
        longValues: Map<String, Long>,
        trueFlags: Set<String>,
    ): Boolean = preferences.edit().apply {
        longValues.forEach { (key, value) -> putLong(key, value) }
        trueFlags.forEach { key -> putBoolean(key, true) }
    }.commit()
}

internal fun analyticsRootOccurrenceId(occurrenceId: String): String =
    occurrenceId.substringBefore(":retry-")

private fun analyticsLocalKeyHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) }
    }
}

private val AnalyticsUsageStoreLock = Any()
