package com.joon.ringout.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

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
    const val EXTRA_SCHEDULE_FINGERPRINT = "schedule_fingerprint"
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
    putExtra(AlarmRuntime.EXTRA_SCHEDULE_FINGERPRINT, request.scheduleFingerprint())
    removeExtra(AlarmRuntime.EXTRA_SOUND_URI)
    request.alarmSoundUri?.let { putExtra(AlarmRuntime.EXTRA_SOUND_URI, it) }
}

internal fun AlarmScheduleRequest.scheduleFingerprint(): String {
    val canonical = buildString {
        appendFingerprintPart(id)
        appendFingerprintPart(time)
        val canonicalDays = selectedDays
            .distinct()
            .sortedBy { day -> AlarmDayOrder[day] ?: Int.MAX_VALUE }
        appendFingerprintPart(canonicalDays.size.toString())
        canonicalDays.forEach { day -> appendFingerprintPart(day) }
        appendFingerprintPart(repeatEnabled.toString())
        appendFingerprintPart(limitMinutes.toString())
        appendFingerprintPart(destinationName)
        appendFingerprintPart(destinationAddress)
        appendFingerprintPart(destinationLatitude.fingerprintBits().toString())
        appendFingerprintPart(destinationLongitude.fingerprintBits().toString())
        appendFingerprintPart(targetDistanceKm.fingerprintBits().toString())
        appendFingerprintPart(alarmSoundName)
        appendFingerprintPart(alarmSoundUri)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HexDigits[value ushr 4])
            append(HexDigits[value and 0x0f])
        }
    }
}

private fun StringBuilder.appendFingerprintPart(value: String?) {
    if (value == null) {
        append("-1:")
    } else {
        append(value.length).append(':').append(value)
    }
    append(';')
}

private fun Double.fingerprintBits(): Long = if (this == 0.0) 0L else toBits()

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID) ?: return
        val sourceIntent = Intent(intent)
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        AlarmPersistenceScope.launch {
            try {
                withTimeout(AlarmPersistenceTimeoutMillis) {
                    AndroidAlarmScheduler(applicationContext).ringTriggeredIfCurrent(
                        alarmId = alarmId,
                        expectedFingerprint = sourceIntent.getStringExtra(
                            AlarmRuntime.EXTRA_SCHEDULE_FINGERPRINT,
                        ),
                    ) { request ->
                        val runtimeIntent = sourceIntent.apply {
                            putAlarmExtras(request)
                            putExtra(
                                AlarmRuntime.EXTRA_OCCURRENCE_ID,
                                "$alarmId:${System.currentTimeMillis()}",
                            )
                            putExtra(AlarmRuntime.EXTRA_RETRY_ATTEMPT, 0)
                        }
                        withContext(Dispatchers.Main.immediate) {
                            val serviceIntent = Intent(
                                applicationContext,
                                AlarmRingingService::class.java,
                            ).apply {
                                action = AlarmRuntime.ACTION_RING
                                replaceExtras(runtimeIntent)
                            }
                            applicationContext.startForegroundService(serviceIntent)
                            if (Settings.canDrawOverlays(applicationContext)) {
                                runCatching {
                                    applicationContext.startActivity(
                                        AlarmRingingActivity.intentFromRuntime(
                                            applicationContext,
                                            runtimeIntent,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(AlarmPersistenceLogTag, "알람 발화를 준비하지 못했습니다.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == ActionAlarmRescheduleRetry
        ) {
            val applicationContext = context.applicationContext
            val retryAttempt = intent.getIntExtra(ExtraRescheduleRetryAttempt, 0)
            val pendingResult = goAsync()
            AlarmPersistenceScope.launch {
                try {
                    withTimeout(AlarmPersistenceTimeoutMillis) {
                        var failure: Exception? = null
                        try {
                            AndroidAlarmScheduler(applicationContext).rescheduleAll()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failure = error
                        }
                        try {
                            AlarmMissionCoordinator(applicationContext).restoreDeadline(
                                discardCachedLocation = intent.action == Intent.ACTION_BOOT_COMPLETED,
                            )
                        } catch (error: Exception) {
                            val rescheduleFailure = failure
                            if (rescheduleFailure == null) {
                                failure = error
                            } else {
                                rescheduleFailure.addSuppressed(error)
                            }
                        }
                        failure?.let { throw it }
                    }
                    cancelAlarmRescheduleRetry(applicationContext)
                } catch (error: Exception) {
                    Log.e(AlarmPersistenceLogTag, "알람 재등록에 실패했습니다.", error)
                    scheduleAlarmRescheduleRetry(
                        context = applicationContext,
                        attempt = retryAttempt + 1,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

internal fun scheduleAlarmRescheduleRetry(
    context: Context,
    attempt: Int,
) {
    val retryDelayMillis = AlarmRescheduleRetryDelaysMillis.getOrNull(attempt - 1)
    if (retryDelayMillis == null) {
        Log.e(AlarmPersistenceLogTag, "알람 재등록 재시도 횟수를 초과했습니다.")
        return
    }
    runCatching {
        val retryPendingIntent = requireNotNull(
            alarmRescheduleRetryPendingIntent(
                context = context,
                attempt = attempt,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + retryDelayMillis,
            retryPendingIntent,
        )
    }.onFailure { error ->
        Log.e(AlarmPersistenceLogTag, "알람 재등록 재시도를 예약하지 못했습니다.", error)
    }
}

internal fun cancelAlarmRescheduleRetry(context: Context) {
    val pendingIntent = alarmRescheduleRetryPendingIntent(
        context = context,
        attempt = 0,
        flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
    context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    pendingIntent.cancel()
}

private fun alarmRescheduleRetryPendingIntent(
    context: Context,
    attempt: Int,
    flags: Int,
): PendingIntent? = PendingIntent.getBroadcast(
    context,
    AlarmRescheduleRetryRequestCode,
    Intent(context, AlarmRescheduleReceiver::class.java).apply {
        action = ActionAlarmRescheduleRetry
        data = Uri.parse("ringout://alarm/reschedule-retry")
        putExtra(ExtraRescheduleRetryAttempt, attempt)
    },
    flags,
)

private val AlarmPersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private const val AlarmPersistenceTimeoutMillis = 8_000L
private const val AlarmPersistenceLogTag = "RingoutAlarmStore"
private const val ActionAlarmRescheduleRetry = "com.joon.ringout.action.RETRY_ALARM_RESCHEDULE"
private const val ExtraRescheduleRetryAttempt = "reschedule_retry_attempt"
private const val AlarmRescheduleRetryRequestCode = 7_204
private const val HexDigits = "0123456789abcdef"
private val AlarmRescheduleRetryDelaysMillis = listOf(
    60_000L,
    5 * 60_000L,
    15 * 60_000L,
    60 * 60_000L,
    3 * 60 * 60_000L,
)
private val AlarmDayOrder = mapOf(
    "월" to 1,
    "화" to 2,
    "수" to 3,
    "목" to 4,
    "금" to 5,
    "토" to 6,
    "일" to 7,
)
