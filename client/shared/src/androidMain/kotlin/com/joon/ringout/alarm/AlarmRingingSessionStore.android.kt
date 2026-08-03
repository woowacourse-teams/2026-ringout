package com.joon.ringout.alarm

import android.content.Context

internal class AlarmRingingSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun markRinging(occurrenceId: String): Boolean =
        synchronized(AlarmRingingSessionLock) {
            preferences.edit()
                .putString(KeyOccurrenceId, occurrenceId)
                .commit()
        }

    fun <T> runIfCurrent(
        occurrenceId: String,
        block: () -> T,
    ): T? = synchronized(AlarmRingingSessionLock) {
        if (preferences.getString(KeyOccurrenceId, null) != occurrenceId) {
            return@synchronized null
        }
        block()
    }

    fun clearIfCurrent(occurrenceId: String): Boolean =
        synchronized(AlarmRingingSessionLock) {
            if (preferences.getString(KeyOccurrenceId, null) != occurrenceId) {
                return@synchronized false
            }
            preferences.edit().clear().commit()
        }

    private companion object {
        const val PreferencesName = "ringout_ringing_alarm_session"
        const val KeyOccurrenceId = "occurrence_id"
    }
}

private val AlarmRingingSessionLock = Any()
