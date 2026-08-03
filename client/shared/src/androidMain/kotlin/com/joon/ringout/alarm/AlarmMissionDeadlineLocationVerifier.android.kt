package com.joon.ringout.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

internal class AlarmMissionDeadlineLocationVerifier(context: Context) {
    private val applicationContext = context.applicationContext
    private val locationManager =
        applicationContext.getSystemService(LocationManager::class.java)
    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)

    fun requestCurrentLocation(
        mission: ActiveAlarmMission,
        onResult: (ActiveAlarmMissionLocation?) -> Unit,
    ) {
        if (Looper.myLooper() != mainLooper) {
            mainHandler.post {
                requestCurrentLocation(
                    mission = mission,
                    onResult = onResult,
                )
            }
            return
        }

        if (
            applicationContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult(null)
            return
        }

        requestFromEnabledProviders(
            mission = mission,
            onResult = onResult,
        )
    }

    private fun requestFromEnabledProviders(
        mission: ActiveAlarmMission,
        onResult: (ActiveAlarmMissionLocation?) -> Unit,
    ) {
        var bestLocation: ActiveAlarmMissionLocation? = null
        val isCompleted = AtomicBoolean(false)

        lateinit var locationListener: LocationListener
        lateinit var timeout: Runnable

        fun complete(result: ActiveAlarmMissionLocation?) {
            if (!isCompleted.compareAndSet(false, true)) return

            mainHandler.removeCallbacks(timeout)
            runCatching { locationManager.removeUpdates(locationListener) }
            onResult(result)
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val candidate = location.toMissionLocation()
                val currentBest = bestLocation
                if (
                    currentBest == null ||
                    candidate.isMoreAccurateThan(currentBest)
                ) {
                    bestLocation = candidate
                }

                if (
                    mission.hasReachedDestinationByDeadline(
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                        accuracyMeters = candidate.accuracyMeters,
                        capturedAtEpochMillis = candidate.capturedAtEpochMillis,
                    )
                ) {
                    complete(candidate)
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit
        }
        timeout = Runnable {
            complete(bestLocation)
        }
        mainHandler.postDelayed(timeout, DeadlineLocationAcquisitionTimeoutMillis)

        val enabledProviders = runCatching {
            locationManager.getProviders(true)
        }.getOrDefault(emptyList())
            .filter { provider ->
                provider == LocationManager.GPS_PROVIDER ||
                    provider == LocationManager.NETWORK_PROVIDER
            }

        val registeredProviderCount = enabledProviders.count { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    MinimumUpdateIntervalMillis,
                    MinimumUpdateDistanceMeters,
                    locationListener,
                    mainLooper,
                )
            }.isSuccess
        }
        if (registeredProviderCount == 0) {
            complete(null)
        }
    }

    private fun Location.toMissionLocation(): ActiveAlarmMissionLocation =
        ActiveAlarmMissionLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) {
                accuracy
            } else {
                Float.POSITIVE_INFINITY
            },
            capturedAtEpochMillis = time.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
        )

    private fun ActiveAlarmMissionLocation.isMoreAccurateThan(
        other: ActiveAlarmMissionLocation,
    ): Boolean =
        accuracyMeters < other.accuracyMeters ||
            (
                accuracyMeters == other.accuracyMeters &&
                    capturedAtEpochMillis > other.capturedAtEpochMillis
            )

    private companion object {
        const val MinimumUpdateIntervalMillis = 0L
        const val MinimumUpdateDistanceMeters = 0f
    }
}
