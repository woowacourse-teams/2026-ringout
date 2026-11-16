package com.joon.ringout.alarm

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only a running service can grant access to its ringing screen. Never restores stale sessions. */
object AlarmRingingRuntime {
    private val lock = Any()
    private val mutableRingingOccurrenceId = MutableStateFlow<String?>(null)
    val ringingOccurrenceId: StateFlow<String?> = mutableRingingOccurrenceId.asStateFlow()
    private var ringingIntent: Intent? = null
    private val starting = mutableMapOf<String, Pair<String, Long>>()

    internal fun markStarting(alarmId: String, occurrenceId: String) = synchronized(lock) {
        starting[occurrenceId] = alarmId to SystemClock.elapsedRealtime()
    }

    internal fun clearStarting(occurrenceId: String) {
        synchronized(lock) { starting.remove(occurrenceId) }
    }


    internal fun markRinging(intent: Intent) = synchronized(lock) {
        val occurrenceId = intent.getStringExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID)
            ?.takeIf(String::isNotBlank)
            ?: return@synchronized
        starting.remove(occurrenceId)
        ringingIntent = Intent(intent)
        mutableRingingOccurrenceId.value = occurrenceId
    }

    internal fun clearIfCurrent(occurrenceId: String) = synchronized(lock) {
        if (mutableRingingOccurrenceId.value != occurrenceId) return@synchronized
        ringingIntent = null
        mutableRingingOccurrenceId.value = null
    }

    internal fun isRinging(alarmId: String): Boolean = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        starting.entries.removeAll { (_, launch) -> now - launch.second >= 30_000 }
        ringingIntent?.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID) == alarmId ||
            starting.values.any { (id, _) -> id == alarmId }
    }

    fun openRingingActivity(context: Context): Boolean = synchronized(lock) {
        val source = ringingIntent ?: return@synchronized false
        runCatching {
            context.startActivity(AlarmRingingActivity.intentFromRuntime(context, source))
        }.isSuccess
    }
}
