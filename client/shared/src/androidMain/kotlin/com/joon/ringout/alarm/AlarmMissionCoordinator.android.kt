package com.joon.ringout.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicBoolean

class AlarmMissionCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val store = ActiveAlarmMissionStore(applicationContext)
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    fun startFrom(ringingIntent: Intent): ActiveAlarmMission? {
        if (
            !applicationContext.hasMissionFineLocationPermission() ||
            !applicationContext.isMissionLocationEnabled()
        ) {
            return null
        }
        val mission = store.saveFrom(ringingIntent) ?: return null
        scheduleDeadline(mission)
        startTracking(mission)
        return mission
    }

    fun resumeTracking() {
        val storedMission = store.readStoredMission() ?: return
        when (storedMission.phase) {
            AlarmMissionPhase.Tracking -> {
                scheduleDeadline(storedMission.mission)
                if (!storedMission.mission.isExpiredAt(System.currentTimeMillis())) {
                    startTracking(storedMission.mission)
                }
            }
            AlarmMissionPhase.SuccessPendingNotification,
            AlarmMissionPhase.RetryPendingRing,
            -> schedulePendingActionRetry(storedMission.mission)
        }
    }

    fun restoreDeadline(discardCachedLocation: Boolean = false) {
        if (discardCachedLocation) {
            store.clearLastLocation()
        }
        val storedMission = store.readStoredMission() ?: return
        when (storedMission.phase) {
            AlarmMissionPhase.Tracking -> scheduleDeadline(storedMission.mission)
            AlarmMissionPhase.SuccessPendingNotification,
            AlarmMissionPhase.RetryPendingRing,
            -> schedulePendingActionRetry(storedMission.mission)
        }
    }

    fun handleDeadline(expectedOccurrenceId: String? = null) {
        val storedMission = store.readStoredMission() ?: return
        if (
            expectedOccurrenceId != null &&
            storedMission.mission.occurrenceId != expectedOccurrenceId
        ) {
            return
        }
        when (storedMission.phase) {
            AlarmMissionPhase.Tracking -> scheduleDeadline(storedMission.mission)
            AlarmMissionPhase.SuccessPendingNotification,
            AlarmMissionPhase.RetryPendingRing,
            -> schedulePendingActionRetry(storedMission.mission)
        }
    }

    internal fun prepareDeadlineLocationCheck(
        expectedOccurrenceId: String?,
    ): ActiveAlarmMission? {
        val storedMission = store.readStoredMission() ?: return null
        if (
            expectedOccurrenceId != null &&
            storedMission.mission.occurrenceId != expectedOccurrenceId
        ) {
            return null
        }

        return when (storedMission.phase) {
            AlarmMissionPhase.SuccessPendingNotification,
            AlarmMissionPhase.RetryPendingRing,
            -> {
                deliverPendingAction(storedMission.mission.occurrenceId)
                null
            }
            AlarmMissionPhase.Tracking -> {
                if (storedMission.mission.isExpiredAt(System.currentTimeMillis())) {
                    scheduleDeadlineCheckRecovery(storedMission.mission)
                    storedMission.mission
                } else {
                    scheduleDeadline(storedMission.mission)
                    null
                }
            }
        }
    }

    internal fun resolveDeadline(
        expectedOccurrenceId: String?,
        currentLocation: ActiveAlarmMissionLocation?,
    ) {
        val storedMission = store.readStoredMission() ?: return
        if (
            expectedOccurrenceId != null &&
            storedMission.mission.occurrenceId != expectedOccurrenceId
        ) {
            return
        }
        if (storedMission.phase != AlarmMissionPhase.Tracking) {
            deliverPendingAction(storedMission.mission.occurrenceId)
            return
        }

        val mission = storedMission.mission
        if (!mission.isExpiredAt(System.currentTimeMillis())) {
            scheduleDeadline(mission)
            return
        }
        currentLocation?.let { location ->
            store.updateLastLocation(
                occurrenceId = mission.occurrenceId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracyMeters,
                capturedAtEpochMillis = location.capturedAtEpochMillis,
            )
        }
        val deadlineLocation = currentLocation
            ?.takeIf { location ->
                mission.isUsableDeadlineLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    capturedAtEpochMillis = location.capturedAtEpochMillis,
                )
            }
            ?: store.readLastLocation(mission.occurrenceId)
        val reachedByDeadline = deadlineLocation != null &&
            mission.hasReachedDestinationByDeadline(
                latitude = deadlineLocation.latitude,
                longitude = deadlineLocation.longitude,
                accuracyMeters = deadlineLocation.accuracyMeters,
                capturedAtEpochMillis = deadlineLocation.capturedAtEpochMillis,
            )
        if (reachedByDeadline) {
            completeSuccessfully(mission)
        } else {
            ringAgain(mission)
        }
    }

    internal fun onLocationUpdated(
        mission: ActiveAlarmMission,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        capturedAtEpochMillis: Long,
    ) {
        val activeMission = store.read()
            ?.takeIf { current -> current.occurrenceId == mission.occurrenceId }
            ?: return
        store.updateLastLocation(
            occurrenceId = activeMission.occurrenceId,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            capturedAtEpochMillis = capturedAtEpochMillis,
        )

        if (
            capturedAtEpochMillis <= activeMission.expiresAtEpochMillis &&
            activeMission.hasReachedDestination(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
            )
        ) {
            completeSuccessfully(activeMission)
        }
    }

    internal fun confirmRetryAlarmStarted(ringingIntent: Intent): Boolean {
        val storedMission = expectedRetryMission(ringingIntent) ?: return false
        val completion = store.completeTerminalTransition(
            occurrenceId = storedMission.mission.occurrenceId,
            phase = storedMission.phase,
        )
        if (completion.isPersistenceConfirmed) {
            cancelDeadline(storedMission.mission)
        }
        if (completion.wasAccepted) {
            applicationContext.stopService(
                AlarmMissionTrackingService.stopIntent(applicationContext),
            )
        }
        return completion.wasAccepted
    }

    internal fun isExpectedRetryAlarm(ringingIntent: Intent): Boolean =
        expectedRetryMission(ringingIntent) != null

    private fun expectedRetryMission(ringingIntent: Intent): StoredAlarmMission? {
        val sourceOccurrenceId = ringingIntent.getStringExtra(
            AlarmRuntime.EXTRA_RETRY_SOURCE_OCCURRENCE_ID,
        ) ?: return null
        val retryOccurrenceId = ringingIntent.getStringExtra(
            AlarmRuntime.EXTRA_OCCURRENCE_ID,
        ) ?: return null
        return store.readStoredMission()
            ?.takeIf { current ->
                current.mission.occurrenceId == sourceOccurrenceId &&
                    current.phase == AlarmMissionPhase.RetryPendingRing &&
                    current.mission.nextRetryOccurrenceId() == retryOccurrenceId
            }
    }

    private fun scheduleDeadline(mission: ActiveAlarmMission) {
        val deadlineIntent = deadlinePendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val triggerAtMillis = mission.expiresAtEpochMillis
            .coerceAtLeast(System.currentTimeMillis() + 1L)
        scheduleMissionAlarm(
            mission = mission,
            triggerAtMillis = triggerAtMillis,
            operation = deadlineIntent,
        )
    }

    private fun cancelDeadline(mission: ActiveAlarmMission) {
        val deadlineIntent = deadlinePendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(deadlineIntent)
        deadlineIntent.cancel()
        missionShowPendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }

    private fun schedulePendingActionRetry(mission: ActiveAlarmMission) {
        val deadlineIntent = deadlinePendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val retryAtMillis = System.currentTimeMillis() + PendingActionRetryDelayMillis
        scheduleMissionAlarm(
            mission = mission,
            triggerAtMillis = retryAtMillis,
            operation = deadlineIntent,
        )
    }

    private fun scheduleDeadlineCheckRecovery(mission: ActiveAlarmMission) {
        val deadlineIntent = deadlinePendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val retryAtMillis = System.currentTimeMillis() + DeadlineCheckRecoveryDelayMillis
        scheduleMissionAlarm(
            mission = mission,
            triggerAtMillis = retryAtMillis,
            operation = deadlineIntent,
        )
    }

    private fun scheduleMissionAlarm(
        mission: ActiveAlarmMission,
        triggerAtMillis: Long,
        operation: PendingIntent,
    ) {
        val showIntent = missionShowPendingIntent(
            mission = mission,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmClockScheduled = showIntent != null &&
            runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    operation,
                )
            }.isSuccess
        if (alarmClockScheduled) return

        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        }.recoverCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        }
    }

    private fun missionShowPendingIntent(
        mission: ActiveAlarmMission,
        flags: Int,
    ): PendingIntent? {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                action = ActionShowMission
                data = Uri.parse(
                    "ringout://alarm-mission/" +
                        "${Uri.encode(mission.occurrenceId)}/show",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, mission.occurrenceId)
            }
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            mission.occurrenceId.hashCode() xor Int.MIN_VALUE,
            launchIntent,
            flags,
        )
    }

    private fun deadlinePendingIntent(
        mission: ActiveAlarmMission,
        flags: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            applicationContext,
            mission.occurrenceId.hashCode(),
            Intent(applicationContext, AlarmMissionDeadlineReceiver::class.java).apply {
                action = ActionMissionDeadline
                data = Uri.parse(
                    "ringout://alarm-mission/" +
                        "${Uri.encode(mission.occurrenceId)}/deadline",
                )
                putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, mission.occurrenceId)
            },
            flags,
        )

    private fun startTracking(mission: ActiveAlarmMission) {
        runCatching {
            applicationContext.startForegroundService(
                AlarmMissionTrackingService.startIntent(
                    context = applicationContext,
                    occurrenceId = mission.occurrenceId,
                ),
            )
        }
    }

    private fun completeSuccessfully(mission: ActiveAlarmMission) {
        val pendingMission = store.beginTerminalTransition(
            occurrenceId = mission.occurrenceId,
            phase = AlarmMissionPhase.SuccessPendingNotification,
        ) ?: return
        schedulePendingActionRetry(pendingMission)
        deliverPendingAction(pendingMission.occurrenceId)
    }

    private fun ringAgain(mission: ActiveAlarmMission) {
        val pendingMission = store.beginTerminalTransition(
            occurrenceId = mission.occurrenceId,
            phase = AlarmMissionPhase.RetryPendingRing,
        ) ?: return
        schedulePendingActionRetry(pendingMission)
        deliverPendingAction(pendingMission.occurrenceId)
    }

    private fun deliverPendingAction(expectedOccurrenceId: String) {
        synchronized(AlarmMissionSideEffectLock) {
            val storedMission = store.readStoredMission()
                ?.takeIf { current ->
                    current.mission.occurrenceId == expectedOccurrenceId
                }
                ?: return
            when (storedMission.phase) {
                AlarmMissionPhase.Tracking -> return
                AlarmMissionPhase.SuccessPendingNotification -> {
                    val delivered = runCatching {
                        showArrivalNotification(storedMission.mission)
                    }.isSuccess
                    if (!delivered) {
                        schedulePendingActionRetry(storedMission.mission)
                        return
                    }
                    completePendingAction(storedMission)
                }
                AlarmMissionPhase.RetryPendingRing -> {
                    if (!startRetryAlarm(storedMission.mission)) {
                        schedulePendingActionRetry(storedMission.mission)
                    }
                }
            }
        }
    }

    private fun completePendingAction(storedMission: StoredAlarmMission) {
        val completion = store.completeTerminalTransition(
            occurrenceId = storedMission.mission.occurrenceId,
            phase = storedMission.phase,
        )
        if (completion.isPersistenceConfirmed) {
            cancelDeadline(storedMission.mission)
        }
        if (completion.wasAccepted) {
            applicationContext.stopService(
                AlarmMissionTrackingService.stopIntent(applicationContext),
            )
        }
    }

    private fun startRetryAlarm(mission: ActiveAlarmMission): Boolean {
        val retryRuntimeIntent = mission.toRetryRuntimeIntent()
        return runCatching {
            applicationContext.startForegroundService(
                Intent(applicationContext, AlarmRingingService::class.java).apply {
                    action = AlarmRuntime.ACTION_RING
                    data = retryRuntimeIntent.data
                    replaceExtras(retryRuntimeIntent)
                },
            )
        }.isSuccess
    }

    private fun showArrivalNotification(mission: ActiveAlarmMission) {
        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ArrivalNotificationChannelId,
                "목적지 도착",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "제한시간 안에 목적지에 도착하면 알려드립니다."
            },
        )

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                ArrivalNotificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = Notification.Builder(
            applicationContext,
            ArrivalNotificationChannelId,
        )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("목적지에 도착했습니다.")
            .setContentText("${mission.destinationName} 미션을 완료했어요.")
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .apply {
                contentIntent?.let(::setContentIntent)
            }
            .build()

        notificationManager.notify(ArrivalNotificationId, notification)
    }

    private fun ActiveAlarmMission.toRetryRuntimeIntent(): Intent {
        val nextAttempt = retryAttempt + 1
        val nextOccurrenceId = nextRetryOccurrenceId()
        return Intent(AlarmRuntime.ACTION_RING).apply {
            data = Uri.parse(
                "ringout://alarm/${Uri.encode(alarmId)}/retry/$nextAttempt",
            )
            putExtra(AlarmRuntime.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, nextOccurrenceId)
            putExtra(AlarmRuntime.EXTRA_RETRY_SOURCE_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmRuntime.EXTRA_RETRY_ATTEMPT, nextAttempt)
            putExtra(AlarmRuntime.EXTRA_ALARM_TIME, alarmTime)
            putExtra(AlarmRuntime.EXTRA_LIMIT_MINUTES, limitMinutes)
            putExtra(AlarmRuntime.EXTRA_DESTINATION_NAME, destinationName)
            putExtra(AlarmRuntime.EXTRA_DESTINATION_LATITUDE, destinationLatitude)
            putExtra(AlarmRuntime.EXTRA_DESTINATION_LONGITUDE, destinationLongitude)
            putExtra(AlarmRuntime.EXTRA_ARRIVAL_RADIUS_METERS, arrivalRadiusMeters)
            putExtra(AlarmRuntime.EXTRA_HAS_SOUND_URI, hasAlarmSoundUri)
            alarmSoundUri?.let { soundUri ->
                putExtra(AlarmRuntime.EXTRA_SOUND_URI, soundUri)
            }
        }
    }

    private fun ActiveAlarmMission.nextRetryOccurrenceId(): String =
        "$occurrenceId:retry-${retryAttempt + 1}"

    private companion object {
        const val ArrivalNotificationChannelId = "ringout_destination_arrival"
        const val ArrivalNotificationId = 7_203
        const val ActionMissionDeadline = "com.joon.ringout.action.ALARM_MISSION_DEADLINE"
        const val ActionShowMission = "com.joon.ringout.action.SHOW_ALARM_MISSION"
        const val PendingActionRetryDelayMillis = 5_000L
        const val DeadlineCheckRecoveryDelayMillis = 10_000L
    }
}

class AlarmMissionDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val isFinished = AtomicBoolean(false)
        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Ringout:MissionDeadlineLocation",
                )
                .apply {
                    acquire(DeadlineReceiverWakeLockTimeoutMillis)
                }
        }.getOrNull()
        fun finish() {
            if (isFinished.compareAndSet(false, true)) {
                runCatching {
                    wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
                }
                pendingResult.finish()
            }
        }

        val applicationContext = context.applicationContext
        val expectedOccurrenceId = intent.getStringExtra(
            AlarmRuntime.EXTRA_OCCURRENCE_ID,
        )
        val coordinator = AlarmMissionCoordinator(applicationContext)
        val mission = runCatching {
            coordinator.prepareDeadlineLocationCheck(expectedOccurrenceId)
        }.getOrNull()
        if (mission == null) {
            finish()
            return
        }

        runCatching {
            AlarmMissionDeadlineLocationVerifier(applicationContext)
                .requestCurrentLocation(mission) { location ->
                    try {
                        coordinator.resolveDeadline(
                            expectedOccurrenceId = expectedOccurrenceId,
                            currentLocation = location,
                        )
                    } finally {
                        finish()
                    }
                }
        }.onFailure {
            if (!isFinished.get()) {
                try {
                    coordinator.resolveDeadline(
                        expectedOccurrenceId = expectedOccurrenceId,
                        currentLocation = null,
                    )
                } finally {
                    finish()
                }
            }
        }
    }
}

private val AlarmMissionSideEffectLock = Any()
private const val DeadlineReceiverWakeLockTimeoutMillis = 7_000L
