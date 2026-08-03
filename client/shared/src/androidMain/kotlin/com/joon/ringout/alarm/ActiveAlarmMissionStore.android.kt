package com.joon.ringout.alarm

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class ActiveAlarmMissionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun read(): ActiveAlarmMission? = synchronized(ActiveAlarmMissionStoreLock) {
        readStoredMissionLocked()
            ?.takeIf { storedMission ->
                storedMission.phase == AlarmMissionPhase.Tracking
            }
            ?.mission
    }

    internal fun readStoredMission(): StoredAlarmMission? =
        synchronized(ActiveAlarmMissionStoreLock) {
            readStoredMissionLocked()
        }

    private fun readStoredMissionLocked(): StoredAlarmMission? {
        val alarmId = preferences.getString(KeyAlarmId, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val destinationName = preferences.getString(KeyDestinationName, null)
            ?.takeIf(String::isNotBlank)
            ?: DefaultDestinationName
        val limitMinutes = preferences.getInt(KeyLimitMinutes, DefaultLimitMinutes)
            .coerceAtLeast(0)
        val expiresAtEpochMillis = preferences.getLong(KeyExpiresAtEpochMillis, 0L)
        val startedAtEpochMillis = preferences.getLong(
            KeyStartedAtEpochMillis,
            (expiresAtEpochMillis - limitMinutes * MillisPerMinute).coerceAtLeast(0L),
        )
        return StoredAlarmMission(
            mission = ActiveAlarmMission(
                alarmId = alarmId,
                destinationName = destinationName,
                limitMinutes = limitMinutes,
                expiresAtEpochMillis = expiresAtEpochMillis,
                occurrenceId = preferences.getString(KeyOccurrenceId, null)
                    ?.takeIf(String::isNotBlank)
                    ?: "$alarmId:$startedAtEpochMillis",
                retryAttempt = preferences.getInt(KeyRetryAttempt, 0).coerceAtLeast(0),
                alarmTime = preferences.getString(KeyAlarmTime, null).orEmpty(),
                startedAtEpochMillis = startedAtEpochMillis,
                destinationLatitude = preferences.readDouble(
                    key = KeyDestinationLatitude,
                    defaultValue = Double.NaN,
                ),
                destinationLongitude = preferences.readDouble(
                    key = KeyDestinationLongitude,
                    defaultValue = Double.NaN,
                ),
                arrivalRadiusMeters = preferences.readDouble(
                    key = KeyArrivalRadiusMeters,
                    defaultValue = DefaultArrivalRadiusMeters,
                ),
                alarmSoundUri = preferences.getString(KeyAlarmSoundUri, null),
                hasAlarmSoundUri = preferences.getBoolean(KeyHasAlarmSoundUri, false),
            ),
            phase = preferences.getString(KeyPhase, null)
                ?.let { storedPhase ->
                    AlarmMissionPhase.entries.firstOrNull { phase ->
                        phase.storageValue == storedPhase
                    }
                }
                ?: AlarmMissionPhase.Tracking,
        )
    }

    fun clear() {
        synchronized(ActiveAlarmMissionStoreLock) {
            preferences.edit().clear().commit()
        }
    }

    internal fun beginTerminalTransition(
        occurrenceId: String,
        phase: AlarmMissionPhase,
    ): ActiveAlarmMission? = synchronized(ActiveAlarmMissionStoreLock) {
        if (phase == AlarmMissionPhase.Tracking) return@synchronized null
        val storedMission = readStoredMissionLocked()
            ?.takeIf { current ->
                current.mission.occurrenceId == occurrenceId &&
                    current.phase == AlarmMissionPhase.Tracking
            }
            ?: return@synchronized null
        val committed = preferences.edit()
            .putString(KeyPhase, phase.storageValue)
            .commit()
        storedMission.mission.takeIf { committed }
    }

    internal fun completeTerminalTransition(
        occurrenceId: String,
        phase: AlarmMissionPhase,
    ): TerminalTransitionCompletion = synchronized(ActiveAlarmMissionStoreLock) {
        val storedMission = readStoredMissionLocked()
            ?: return@synchronized TerminalTransitionCompletion.Rejected
        if (
            storedMission.mission.occurrenceId != occurrenceId ||
            storedMission.phase != phase
        ) {
            return@synchronized TerminalTransitionCompletion.Rejected
        }
        if (preferences.edit().clear().commit()) {
            TerminalTransitionCompletion.Persisted
        } else {
            // SharedPreferences reflects the edit in memory before disk persistence.
            // The caller may proceed with an already-started side effect, but must
            // retain its recovery alarm until persistence has been confirmed.
            TerminalTransitionCompletion.AcceptedWithoutConfirmedPersistence
        }
    }

    internal fun saveFrom(intent: Intent): ActiveAlarmMission? =
        synchronized(ActiveAlarmMissionStoreLock) {
            saveFromLocked(intent)
        }

    private fun saveFromLocked(intent: Intent): ActiveAlarmMission? {
        val alarmId = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val nowEpochMillis = System.currentTimeMillis()
        val occurrenceId = intent.getStringExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID)
            ?.takeIf(String::isNotBlank)
            ?: "$alarmId:$nowEpochMillis"
        readStoredMissionLocked()?.let { storedMission ->
            return if (
                storedMission.mission.occurrenceId == occurrenceId &&
                storedMission.phase == AlarmMissionPhase.Tracking
            ) {
                storedMission.mission
            } else {
                null
            }
        }

        val destinationName = intent
            .getStringExtra(AlarmRuntime.EXTRA_DESTINATION_NAME)
            .orEmpty()
            .ifBlank { DefaultDestinationName }
        val limitMinutes = intent
            .getIntExtra(AlarmRuntime.EXTRA_LIMIT_MINUTES, DefaultLimitMinutes)
            .coerceAtLeast(0)
        val expiresAtEpochMillis = nowEpochMillis + limitMinutes * MillisPerMinute
        val mission = ActiveAlarmMission(
            alarmId = alarmId,
            destinationName = destinationName,
            limitMinutes = limitMinutes,
            expiresAtEpochMillis = expiresAtEpochMillis,
            occurrenceId = occurrenceId,
            retryAttempt = intent
                .getIntExtra(AlarmRuntime.EXTRA_RETRY_ATTEMPT, 0)
                .coerceAtLeast(0),
            alarmTime = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_TIME).orEmpty(),
            startedAtEpochMillis = nowEpochMillis,
            destinationLatitude = intent.getDoubleExtra(
                AlarmRuntime.EXTRA_DESTINATION_LATITUDE,
                Double.NaN,
            ),
            destinationLongitude = intent.getDoubleExtra(
                AlarmRuntime.EXTRA_DESTINATION_LONGITUDE,
                Double.NaN,
            ),
            arrivalRadiusMeters = intent.getDoubleExtra(
                AlarmRuntime.EXTRA_ARRIVAL_RADIUS_METERS,
                DefaultArrivalRadiusMeters,
            ).takeIf { radius -> radius.isFinite() && radius > 0.0 }
                ?: DefaultArrivalRadiusMeters,
            alarmSoundUri = intent.getStringExtra(AlarmRuntime.EXTRA_SOUND_URI),
            hasAlarmSoundUri = intent.getBooleanExtra(
                AlarmRuntime.EXTRA_HAS_SOUND_URI,
                false,
            ),
        )

        val committed = preferences.edit()
            .clear()
            .putString(KeyPhase, AlarmMissionPhase.Tracking.storageValue)
            .putString(KeyAlarmId, alarmId)
            .putString(KeyOccurrenceId, mission.occurrenceId)
            .putInt(KeyRetryAttempt, mission.retryAttempt)
            .putString(KeyAlarmTime, mission.alarmTime)
            .putString(KeyDestinationName, destinationName)
            .putInt(KeyLimitMinutes, limitMinutes)
            .putLong(KeyStartedAtEpochMillis, mission.startedAtEpochMillis)
            .putLong(KeyExpiresAtEpochMillis, expiresAtEpochMillis)
            .putDouble(KeyDestinationLatitude, mission.destinationLatitude)
            .putDouble(KeyDestinationLongitude, mission.destinationLongitude)
            .putDouble(KeyArrivalRadiusMeters, mission.arrivalRadiusMeters)
            .putBoolean(KeyHasAlarmSoundUri, mission.hasAlarmSoundUri)
            .apply {
                mission.alarmSoundUri?.let { soundUri ->
                    putString(KeyAlarmSoundUri, soundUri)
                }
            }
            .commit()

        return mission.takeIf { committed }
    }

    internal fun updateLastLocation(
        occurrenceId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        capturedAtEpochMillis: Long,
    ) = synchronized(ActiveAlarmMissionStoreLock) {
        val storedMission = readStoredMissionLocked() ?: return@synchronized
        if (
            storedMission.mission.occurrenceId != occurrenceId ||
            storedMission.phase != AlarmMissionPhase.Tracking
        ) {
            return@synchronized
        }
        preferences.edit()
            .putDouble(KeyLastLatitude, latitude)
            .putDouble(KeyLastLongitude, longitude)
            .putFloat(KeyLastAccuracyMeters, accuracyMeters)
            .putLong(KeyLastCapturedAtEpochMillis, capturedAtEpochMillis)
            .apply()
    }

    fun readLastLocation(
        occurrenceId: String,
    ): ActiveAlarmMissionLocation? = synchronized(ActiveAlarmMissionStoreLock) {
        if (preferences.getString(KeyOccurrenceId, null) != occurrenceId) {
            return@synchronized null
        }
        if (
            !preferences.contains(KeyLastLatitude) ||
            !preferences.contains(KeyLastLongitude) ||
            !preferences.contains(KeyLastAccuracyMeters) ||
            !preferences.contains(KeyLastCapturedAtEpochMillis)
        ) {
            return@synchronized null
        }
        ActiveAlarmMissionLocation(
            latitude = preferences.readDouble(KeyLastLatitude, Double.NaN),
            longitude = preferences.readDouble(KeyLastLongitude, Double.NaN),
            accuracyMeters = preferences.getFloat(KeyLastAccuracyMeters, Float.NaN),
            capturedAtEpochMillis = preferences.getLong(KeyLastCapturedAtEpochMillis, 0L),
        )
    }

    internal fun clearLastLocation() {
        synchronized(ActiveAlarmMissionStoreLock) {
            preferences.edit()
                .remove(KeyLastLatitude)
                .remove(KeyLastLongitude)
                .remove(KeyLastAccuracyMeters)
                .remove(KeyLastCapturedAtEpochMillis)
                .commit()
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PreferencesName = "ringout_active_alarm_mission"
        const val KeyPhase = "phase"
        const val KeyAlarmId = "alarm_id"
        const val KeyOccurrenceId = "occurrence_id"
        const val KeyRetryAttempt = "retry_attempt"
        const val KeyAlarmTime = "alarm_time"
        const val KeyDestinationName = "destination_name"
        const val KeyLimitMinutes = "limit_minutes"
        const val KeyStartedAtEpochMillis = "started_at_epoch_millis"
        const val KeyExpiresAtEpochMillis = "expires_at_epoch_millis"
        const val KeyDestinationLatitude = "destination_latitude"
        const val KeyDestinationLongitude = "destination_longitude"
        const val KeyArrivalRadiusMeters = "arrival_radius_meters"
        const val KeyAlarmSoundUri = "alarm_sound_uri"
        const val KeyHasAlarmSoundUri = "has_alarm_sound_uri"
        const val KeyLastLatitude = "last_latitude"
        const val KeyLastLongitude = "last_longitude"
        const val KeyLastAccuracyMeters = "last_accuracy_meters"
        const val KeyLastCapturedAtEpochMillis = "last_captured_at_epoch_millis"
        const val DefaultDestinationName = "선택한 목적지"
        const val DefaultLimitMinutes = 12
        const val MillisPerMinute = 60_000L
    }
}

internal enum class AlarmMissionPhase(
    internal val storageValue: String,
) {
    Tracking("tracking"),
    SuccessPendingNotification("success_pending_notification"),
    RetryPendingRing("retry_pending_ring"),
}

internal data class StoredAlarmMission(
    val mission: ActiveAlarmMission,
    val phase: AlarmMissionPhase,
)

internal enum class TerminalTransitionCompletion {
    Rejected,
    Persisted,
    AcceptedWithoutConfirmedPersistence,
    ;

    val wasAccepted: Boolean
        get() = this != Rejected

    val isPersistenceConfirmed: Boolean
        get() = this == Persisted
}

private fun SharedPreferences.Editor.putDouble(
    key: String,
    value: Double,
): SharedPreferences.Editor = putLong(key, value.toRawBits())

private fun SharedPreferences.readDouble(
    key: String,
    defaultValue: Double,
): Double = if (contains(key)) {
    Double.fromBits(getLong(key, defaultValue.toRawBits()))
} else {
    defaultValue
}

private val ActiveAlarmMissionStoreLock = Any()
