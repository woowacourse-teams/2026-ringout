package com.joon.ringout.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object AlarmRuntime {
    const val ACTION_RING = "com.joon.ringout.action.RING_ALARM"
    const val ACTION_STOP = "com.joon.ringout.action.STOP_ALARM"

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    const val EXTRA_RETRY_SOURCE_OCCURRENCE_ID = "retry_source_occurrence_id"
    const val EXTRA_RETRY_ATTEMPT = "retry_attempt"
    const val EXTRA_ALARM_TIME = "alarm_time"
    const val EXTRA_LIMIT_MINUTES = "limit_minutes"
    const val EXTRA_TARGET_DISTANCE_KM = "target_distance_km"
    const val EXTRA_DESTINATION_NAME = "destination_name"
    const val EXTRA_DESTINATION_LATITUDE = "destination_latitude"
    const val EXTRA_DESTINATION_LONGITUDE = "destination_longitude"
    const val EXTRA_ARRIVAL_RADIUS_METERS = "arrival_radius_meters"
    const val EXTRA_SOUND_URI = "sound_uri"
    const val EXTRA_HAS_SOUND_URI = "has_sound_uri"
}

internal fun Intent.putAlarmExtras(request: AlarmScheduleRequest): Intent = apply {
    data = Uri.parse("ringout://alarm/${Uri.encode(request.id)}")
    putExtra(AlarmRuntime.EXTRA_ALARM_ID, request.id)
    putExtra(AlarmRuntime.EXTRA_ALARM_TIME, request.time)
    putExtra(AlarmRuntime.EXTRA_LIMIT_MINUTES, request.limitMinutes)
    putExtra(AlarmRuntime.EXTRA_TARGET_DISTANCE_KM, request.targetDistanceKm)
    putExtra(AlarmRuntime.EXTRA_DESTINATION_NAME, request.destinationName)
    putExtra(AlarmRuntime.EXTRA_DESTINATION_LATITUDE, request.destinationLatitude)
    putExtra(AlarmRuntime.EXTRA_DESTINATION_LONGITUDE, request.destinationLongitude)
    putExtra(AlarmRuntime.EXTRA_ARRIVAL_RADIUS_METERS, DefaultArrivalRadiusMeters)
    putExtra(AlarmRuntime.EXTRA_HAS_SOUND_URI, request.alarmSoundUri != null)
    request.alarmSoundUri?.let { putExtra(AlarmRuntime.EXTRA_SOUND_URI, it) }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID) ?: return
        val runtimeIntent = Intent(intent).apply {
            putExtra(
                AlarmRuntime.EXTRA_OCCURRENCE_ID,
                "$alarmId:${System.currentTimeMillis()}",
            )
            putExtra(AlarmRuntime.EXTRA_RETRY_ATTEMPT, 0)
        }
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRuntime.ACTION_RING
            replaceExtras(runtimeIntent)
        }
        context.startForegroundService(serviceIntent)
        if (Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    AlarmRingingActivity.intentFromRuntime(context, runtimeIntent),
                )
            }
        }
        runCatching {
            AndroidAlarmScheduler(context.applicationContext).onTriggered(alarmId)
        }
    }
}

class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AndroidAlarmScheduler(context.applicationContext).rescheduleAll()
            AlarmMissionCoordinator(context.applicationContext).restoreDeadline(
                discardCachedLocation = intent.action == Intent.ACTION_BOOT_COMPLETED,
            )
        }
    }
}
