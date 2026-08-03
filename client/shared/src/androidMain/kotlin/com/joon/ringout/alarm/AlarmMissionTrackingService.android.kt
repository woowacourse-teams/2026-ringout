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
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper

class AlarmMissionTrackingService : Service() {
    private lateinit var missionStore: ActiveAlarmMissionStore
    private lateinit var missionCoordinator: AlarmMissionCoordinator
    private lateinit var locationManager: LocationManager
    private var activeMission: ActiveAlarmMission? = null
    private var locationUpdatesRegistered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            requestUpdatesFrom(provider)
        }

        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?,
        ) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        missionStore = ActiveAlarmMissionStore(applicationContext)
        missionCoordinator = AlarmMissionCoordinator(applicationContext)
        locationManager = getSystemService(LocationManager::class.java)
        createTrackingNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStopTracking) {
            stopSelf()
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
        if (locationUpdatesRegistered) {
            runCatching { locationManager.removeUpdates(locationListener) }
        }
        locationUpdatesRegistered = false
        activeMission = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTrackingForeground(mission: ActiveAlarmMission): Boolean =
        runCatching {
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
        }.isSuccess

    private fun registerLocationUpdates() {
        if (!hasPreciseLocationPermission()) return
        val providers = locationManager.allProviders
            .filter { provider ->
                provider == LocationManager.GPS_PROVIDER ||
                    provider == LocationManager.NETWORK_PROVIDER
            }
        providers.forEach(::requestUpdatesFrom)
        locationUpdatesRegistered = providers.isNotEmpty()

        providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull(Location::getTime)
            ?.takeIf { location ->
                val missionStartedAt = activeMission?.startedAtEpochMillis ?: Long.MAX_VALUE
                location.time >= missionStartedAt
            }
            ?.let(::handleLocation)
    }

    private fun requestUpdatesFrom(provider: String) {
        if (!hasPreciseLocationPermission()) return
        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                LocationUpdateIntervalMillis,
                LocationUpdateDistanceMeters,
                locationListener,
                Looper.getMainLooper(),
            )
        }
    }

    private fun handleLocation(location: Location) {
        val mission = activeMission ?: return
        val capturedAtEpochMillis = location.time
            .takeIf { capturedAt -> capturedAt > 0L }
            ?: System.currentTimeMillis()
        if (capturedAtEpochMillis < mission.startedAtEpochMillis) return

        missionCoordinator.onLocationUpdated(
            mission = mission,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) {
                location.accuracy
            } else {
                Float.POSITIVE_INFINITY
            },
            capturedAtEpochMillis = capturedAtEpochMillis,
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

    private fun createTrackingNotification(mission: ActiveAlarmMission): Notification {
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
                "${mission.destinationName}까지 ${mission.limitMinutes}분 안에 도착하세요.",
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
        private const val LocationUpdateDistanceMeters = 5f

        internal fun startIntent(
            context: Context,
            occurrenceId: String,
        ): Intent = Intent(context, AlarmMissionTrackingService::class.java).apply {
            action = ActionStartTracking
            putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, occurrenceId)
        }

        internal fun stopIntent(context: Context): Intent =
            Intent(context, AlarmMissionTrackingService::class.java).apply {
                action = ActionStopTracking
            }
    }
}
