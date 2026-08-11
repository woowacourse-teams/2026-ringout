package com.joon.ringout.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class AlarmMissionTrackingService : Service() {
    private lateinit var missionStore: ActiveAlarmMissionStore
    private lateinit var missionCoordinator: AlarmMissionCoordinator
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeMission: ActiveAlarmMission? = null
    private var locationUpdatesRegistered = false
    private var locationRegistrationPending = false
    private var locationRegistrationGeneration = 0L
    private var lastAcceptedLocationFix: TrackingLocationFix? = null
    private var lastLocationAvailability: Boolean? = null

    private val registrationRetry = Runnable {
        if (
            activeMission != null &&
            !locationUpdatesRegistered &&
            !locationRegistrationPending
        ) {
            registerLocationUpdates()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            val isAvailable = availability.isLocationAvailable
            if (isAvailable == lastLocationAvailability) return
            lastLocationAvailability = isAvailable
            if (isAvailable) {
                Log.i(LogTag, "High-accuracy location is available")
                restoreTrackingNotification()
            } else {
                Log.w(LogTag, "High-accuracy location is temporarily unavailable")
                showTrackingWarning("정확한 위치 신호를 찾는 중입니다.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        missionStore = ActiveAlarmMissionStore(applicationContext)
        missionCoordinator = AlarmMissionCoordinator(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        createTrackingNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStopTracking) {
            val expectedOccurrenceId = intent.getStringExtra(
                AlarmRuntime.EXTRA_OCCURRENCE_ID,
            )
            val activeOccurrenceId = activeMission?.occurrenceId
            if (
                expectedOccurrenceId == null ||
                activeOccurrenceId == null ||
                activeOccurrenceId == expectedOccurrenceId
            ) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val mission = missionStore.read()
            ?.takeIf { stored ->
                val expectedOccurrenceId = intent?.getStringExtra(
                    AlarmRuntime.EXTRA_OCCURRENCE_ID,
                )
                expectedOccurrenceId == null ||
                    stored.occurrenceId == expectedOccurrenceId
            }
            ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
        if (activeMission?.occurrenceId != mission.occurrenceId) {
            lastAcceptedLocationFix = null
        }
        activeMission = mission

        if (!startTrackingForeground(mission)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (mission.isExpiredAt(System.currentTimeMillis())) {
            missionCoordinator.handleDeadline(mission.occurrenceId)
            stopSelf()
            return START_NOT_STICKY
        }

        registerLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(registrationRetry)
        locationRegistrationGeneration += 1L
        locationRegistrationPending = false
        removeLocationUpdates()
        locationUpdatesRegistered = false
        lastAcceptedLocationFix = null
        lastLocationAvailability = null
        activeMission = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTrackingForeground(mission: ActiveAlarmMission): Boolean =
        try {
            val notification = createTrackingNotification(mission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TrackingNotificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TrackingNotificationId, notification)
            }
            true
        } catch (error: RuntimeException) {
            Log.e(LogTag, "Failed to start alarm mission location foreground service", error)
            false
        }

    private fun registerLocationUpdates() {
        if (locationUpdatesRegistered || locationRegistrationPending) return
        if (!hasPreciseLocationPermission()) {
            reportLocationRegistrationFailure(
                message = "Precise location permission is not granted",
                userMessage = "정확한 위치 권한을 허용해 주세요.",
            )
            return
        }

        val requestGeneration = ++locationRegistrationGeneration
        locationRegistrationPending = true
        val registrationTask = try {
            fusedLocationClient.requestLocationUpdates(
                trackingLocationRequest(),
                locationCallback,
                Looper.getMainLooper(),
            )
        } catch (error: RuntimeException) {
            if (requestGeneration == locationRegistrationGeneration) {
                locationRegistrationPending = false
                locationUpdatesRegistered = false
            }
            reportLocationRegistrationFailure(
                message = "Failed to request fused location updates",
                userMessage = "위치 추적을 시작하지 못했습니다. 다시 시도합니다.",
                error = error,
            )
            return
        }

        registrationTask.addOnCompleteListener { completedTask ->
            if (requestGeneration != locationRegistrationGeneration || activeMission == null) {
                if (completedTask.isSuccessful) removeLocationUpdates()
                return@addOnCompleteListener
            }

            locationRegistrationPending = false
            locationUpdatesRegistered = completedTask.isSuccessful
            if (completedTask.isSuccessful) {
                mainHandler.removeCallbacks(registrationRetry)
                Log.i(LogTag, "Fused high-accuracy location updates registered")
                restoreTrackingNotification()
            } else {
                reportLocationRegistrationFailure(
                    message = "Fused location update registration failed",
                    userMessage = "위치 추적을 시작하지 못했습니다. 다시 시도합니다.",
                    error = completedTask.exception,
                )
            }
        }
    }

    private fun handleLocation(location: Location) {
        val mission = activeMission ?: return
        val capturedAtEpochMillis = location.time
            .takeIf { capturedAt -> capturedAt > 0L }
            ?: System.currentTimeMillis()
        val candidate = TrackingLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) {
                location.accuracy
            } else {
                Float.POSITIVE_INFINITY
            },
            capturedAtEpochMillis = capturedAtEpochMillis,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )
        val handling = evaluateTrackingLocationHandling(
            previous = lastAcceptedLocationFix,
            candidate = candidate,
            nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            mission = mission,
        )
        if (!handling.shouldForwardToCoordinator) {
            Log.d(
                LogTag,
                "Ignored location fix: decision=${handling.decision}, " +
                    "accuracy=${candidate.accuracyMeters}, provider=${location.provider}",
            )
            return
        }
        if (handling.shouldReplaceLastAcceptedFix) {
            lastAcceptedLocationFix = candidate
        } else {
            Log.i(
                LogTag,
                "Forwarding arrival evidence without replacing the latest fix: " +
                    "decision=${handling.decision}, accuracy=${candidate.accuracyMeters}",
            )
        }

        missionCoordinator.onLocationUpdated(
            mission = mission,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            accuracyMeters = candidate.accuracyMeters,
            capturedAtEpochMillis = candidate.capturedAtEpochMillis,
        )
    }

    private fun trackingLocationRequest(): LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LocationUpdateIntervalMillis,
    )
        .setGranularity(Granularity.GRANULARITY_FINE)
        .setMinUpdateIntervalMillis(LocationFastestUpdateIntervalMillis)
        .setMinUpdateDistanceMeters(0f)
        .setMaxUpdateAgeMillis(0L)
        .setMaxUpdateDelayMillis(0L)
        .setWaitForAccurateLocation(true)
        .build()

    private fun reportLocationRegistrationFailure(
        message: String,
        userMessage: String,
        error: Exception? = null,
    ) {
        locationUpdatesRegistered = false
        if (error == null) {
            Log.e(LogTag, message)
        } else {
            Log.e(LogTag, message, error)
        }
        showTrackingWarning(userMessage)
        mainHandler.removeCallbacks(registrationRetry)
        mainHandler.postDelayed(registrationRetry, LocationRegistrationRetryMillis)
    }

    private fun removeLocationUpdates() {
        if (!::fusedLocationClient.isInitialized) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnFailureListener { error ->
                    Log.w(LogTag, "Failed to remove fused location updates", error)
                }
        } catch (error: RuntimeException) {
            Log.w(LogTag, "Failed to remove fused location updates", error)
        }
    }

    private fun showTrackingWarning(message: String) {
        val mission = activeMission ?: return
        getSystemService(NotificationManager::class.java).notify(
            TrackingNotificationId,
            createTrackingNotification(mission, message),
        )
    }

    private fun restoreTrackingNotification() {
        val mission = activeMission ?: return
        getSystemService(NotificationManager::class.java).notify(
            TrackingNotificationId,
            createTrackingNotification(mission),
        )
    }

    private fun hasPreciseLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createTrackingNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                TrackingNotificationChannelId,
                "목적지 이동 확인",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "제한시간 동안 목적지 도착 여부를 확인합니다."
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun createTrackingNotification(
        mission: ActiveAlarmMission,
        statusMessage: String? = null,
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                TrackingNotificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return Notification.Builder(this, TrackingNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("목적지로 이동 중")
            .setContentText(
                statusMessage
                    ?: "${mission.destinationName}까지 ${mission.limitMinutes}분 안에 도착하세요.",
            )
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                contentIntent?.let(::setContentIntent)
            }
            .build()
    }

    companion object {
        private const val ActionStartTracking =
            "com.joon.ringout.action.START_ALARM_MISSION_TRACKING"
        private const val ActionStopTracking =
            "com.joon.ringout.action.STOP_ALARM_MISSION_TRACKING"
        private const val TrackingNotificationChannelId = "ringout_alarm_mission_tracking"
        private const val TrackingNotificationId = 7_202
        private const val LocationUpdateIntervalMillis = 5_000L
        private const val LocationFastestUpdateIntervalMillis = 2_000L
        private const val LocationRegistrationRetryMillis = 15_000L
        private const val LogTag = "AlarmMissionTracking"

        internal fun startIntent(
            context: Context,
            occurrenceId: String,
        ): Intent = Intent(context, AlarmMissionTrackingService::class.java).apply {
            action = ActionStartTracking
            putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, occurrenceId)
        }

        internal fun stopIntent(
            context: Context,
            occurrenceId: String,
        ): Intent =
            Intent(context, AlarmMissionTrackingService::class.java).apply {
                action = ActionStopTracking
                putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, occurrenceId)
            }
    }
}
