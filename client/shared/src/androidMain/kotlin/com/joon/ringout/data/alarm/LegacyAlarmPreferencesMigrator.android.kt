package com.joon.ringout.data.alarm

import android.content.Context
import android.util.Log
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val legacyAlarmPreferencesMigrationMutex = Mutex()

/** Imports the pre-Room alarm store exactly once, then removes it after the DB commit. */
internal class LegacyAlarmPreferencesMigrator(
    context: Context,
    private val alarmDataSource: AlarmDataSource,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        LEGACY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    suspend fun ensureMigrated() = legacyAlarmPreferencesMigrationMutex.withLock {
        if (alarmDataSource.hasStorageMigration(STORAGE_MIGRATION_ID)) {
            clearLegacyPreferencesIfPresent()
            return@withLock
        }

        val legacyAlarms = withContext(Dispatchers.IO) {
            preferences.all.mapNotNull { (preferenceKey, value) ->
                val raw = value as? String
                if (raw == null) {
                    Log.w(
                        LOG_TAG,
                        "Skipping non-string legacy alarm preference: key=$preferenceKey",
                    )
                    return@mapNotNull null
                }

                try {
                    decodeLegacyAlarm(raw).validateForStorage()
                } catch (error: Exception) {
                    Log.w(
                        LOG_TAG,
                        "Skipping malformed legacy alarm preference: key=$preferenceKey",
                        error,
                    )
                    null
                }
            }
        }

        // This call persists both the alarms and migration marker in one DB transaction.
        alarmDataSource.importLegacyIfNeeded(
            alarms = legacyAlarms,
            migrationId = STORAGE_MIGRATION_ID,
            completedAtEpochMillis = System.currentTimeMillis(),
        )
        clearLegacyPreferencesIfPresent()
    }

    private suspend fun clearLegacyPreferencesIfPresent() = withContext(Dispatchers.IO) {
        if (preferences.all.isEmpty()) return@withContext

        if (!preferences.edit().clear().commit()) {
            Log.w(LOG_TAG, "Failed to clear migrated legacy alarm preferences; will retry")
        }
    }
}

internal fun decodeLegacyAlarm(raw: String): SavedAlarmSchedule {
    val json = JSONObject(raw)
    val id = json.requiredString(KEY_ID)
    val time = json.requiredString(KEY_TIME)
    val selectedDays = decodeSelectedDays(json.requiredDaysArrayOrDefault())
    val latitude = json.optDouble(KEY_DESTINATION_LATITUDE)
    val longitude = json.optDouble(KEY_DESTINATION_LONGITUDE)
    val targetDistanceKm = json.optDouble(KEY_TARGET_DISTANCE_KM, DEFAULT_TARGET_DISTANCE_KM)

    require(id.isNotBlank()) { "Alarm id must not be blank" }
    require(time.isCanonicalAlarmTime()) { "Alarm time must use canonical HH:mm format" }
    require(latitude.isFinite() && latitude in MIN_LATITUDE..MAX_LATITUDE) {
        "Destination latitude is out of range"
    }
    require(longitude.isFinite() && longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
        "Destination longitude is out of range"
    }
    require(targetDistanceKm.isFinite() && targetDistanceKm > 0.0) {
        "Target distance must be positive and finite"
    }

    return SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = id,
            time = time,
            selectedDays = selectedDays,
            repeatEnabled = json.optBoolean(KEY_REPEAT_ENABLED, true),
            limitMinutes = json.optInt(KEY_LIMIT_MINUTES, DEFAULT_LIMIT_MINUTES),
            destinationName = json.optString(KEY_DESTINATION_NAME),
            destinationAddress = json.optString(KEY_DESTINATION_ADDRESS),
            destinationLatitude = latitude,
            destinationLongitude = longitude,
            targetDistanceKm = targetDistanceKm,
            alarmSoundName = json.optString(KEY_SOUND_NAME, DEFAULT_SOUND_NAME),
            alarmSoundUri = if (json.isNull(KEY_SOUND_URI)) null else json.optString(KEY_SOUND_URI),
        ),
        enabled = json.optBoolean(KEY_ENABLED, true),
    )
}

private fun JSONObject.requiredString(key: String): String {
    require(!isNull(key) && opt(key) is String) { "Missing or invalid $key" }
    return getString(key)
}

private fun JSONObject.requiredDaysArrayOrDefault(): JSONArray {
    if (!has(KEY_DAYS) || isNull(KEY_DAYS)) return JSONArray()
    return requireNotNull(optJSONArray(KEY_DAYS)) { "Invalid $KEY_DAYS" }
}

private fun decodeSelectedDays(daysJson: JSONArray): List<String> = buildList {
    for (index in 0 until daysJson.length()) {
        val day = daysJson.opt(index)
        require(day is String) { "Invalid weekday token" }
        require(day in VALID_WEEKDAY_TOKENS) { "Unknown weekday token" }
        add(day)
    }
}

private fun String.isCanonicalAlarmTime(): Boolean =
    length == CANONICAL_TIME_LENGTH &&
        this[TIME_SEPARATOR_INDEX] == ':' &&
        take(TIME_SEPARATOR_INDEX).all { it in '0'..'9' } &&
        drop(TIME_SEPARATOR_INDEX + 1).all { it in '0'..'9' } &&
        substring(0, TIME_SEPARATOR_INDEX).toInt() in 0..23 &&
        substring(TIME_SEPARATOR_INDEX + 1).toInt() in 0..59

private const val LOG_TAG = "LegacyAlarmMigration"
private const val LEGACY_PREFERENCES_NAME = "ringout_scheduled_alarms"
private const val STORAGE_MIGRATION_ID = "android_alarm_shared_preferences_v1"

private const val KEY_ID = "id"
private const val KEY_TIME = "time"
private const val KEY_DAYS = "days"
private const val KEY_REPEAT_ENABLED = "repeatEnabled"
private const val KEY_LIMIT_MINUTES = "limitMinutes"
private const val KEY_DESTINATION_NAME = "destinationName"
private const val KEY_DESTINATION_ADDRESS = "destinationAddress"
private const val KEY_DESTINATION_LATITUDE = "destinationLatitude"
private const val KEY_DESTINATION_LONGITUDE = "destinationLongitude"
private const val KEY_TARGET_DISTANCE_KM = "targetDistanceKm"
private const val KEY_SOUND_NAME = "soundName"
private const val KEY_SOUND_URI = "soundUri"
private const val KEY_ENABLED = "enabled"

private const val DEFAULT_LIMIT_MINUTES = 12
private const val DEFAULT_TARGET_DISTANCE_KM = 1.2
private const val DEFAULT_SOUND_NAME = "기본 알람음"
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val CANONICAL_TIME_LENGTH = 5
private const val TIME_SEPARATOR_INDEX = 2

private val VALID_WEEKDAY_TOKENS = setOf("월", "화", "수", "목", "금", "토", "일")
