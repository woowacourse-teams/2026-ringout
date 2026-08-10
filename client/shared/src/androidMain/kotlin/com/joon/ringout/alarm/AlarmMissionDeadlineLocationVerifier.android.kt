package com.joon.ringout.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.atomic.AtomicBoolean

internal class AlarmMissionDeadlineLocationVerifier(context: Context) {
    private val applicationContext = context.applicationContext
    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)
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
            Log.w(LocationVerificationLogTag, "마감 위치 확인에 필요한 정확한 위치 권한이 없습니다.")
            onResult(null)
            return
        }

        val isCompleted = AtomicBoolean(false)
        var bestLocation: ActiveAlarmMissionLocation? = null
        lateinit var locationCallback: LocationCallback
        lateinit var timeout: Runnable

        fun removeLocationUpdates() {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
                .onSuccess { removalTask ->
                    removalTask.addOnFailureListener { error ->
                        Log.w(
                            LocationVerificationLogTag,
                            "마감 위치 구독을 해제하지 못했습니다.",
                            error,
                        )
                    }
                }
                .onFailure { error ->
                    Log.w(LocationVerificationLogTag, "마감 위치 구독을 해제하지 못했습니다.", error)
                }
        }

        fun complete(result: ActiveAlarmMissionLocation?) {
            if (!isCompleted.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            removeLocationUpdates()
            onResult(result)
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isCompleted.get()) return
                locationResult.locations.forEach { location ->
                    val candidate = location.toMissionLocation()
                    if (
                        !mission.isUsableDeadlineLocation(
                            latitude = candidate.latitude,
                            longitude = candidate.longitude,
                            accuracyMeters = candidate.accuracyMeters,
                            capturedAtEpochMillis = candidate.capturedAtEpochMillis,
                        )
                    ) {
                        return@forEach
                    }

                    bestLocation = mission.selectBestDeadlineLocation(
                        listOfNotNull(bestLocation, candidate),
                    )
                    if (
                        mission.hasReachedDestinationByDeadline(
                            latitude = candidate.latitude,
                            longitude = candidate.longitude,
                            accuracyMeters = candidate.accuracyMeters,
                            capturedAtEpochMillis = candidate.capturedAtEpochMillis,
                        )
                    ) {
                        complete(candidate)
                        return
                    }
                }
            }
        }

        timeout = Runnable {
            Log.w(LocationVerificationLogTag, "마감 위치 확인 시간이 초과되었습니다.")
            complete(bestLocation)
        }
        mainHandler.postDelayed(timeout, DeadlineLocationAcquisitionTimeoutMillis)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            DeadlineLocationUpdateIntervalMillis,
        )
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(0L)
            .setDurationMillis(DeadlineLocationAcquisitionTimeoutMillis)
            .setMinUpdateIntervalMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        val registrationTask = try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper,
            )
        } catch (error: SecurityException) {
            Log.e(LocationVerificationLogTag, "마감 위치 요청 권한을 확인하지 못했습니다.", error)
            complete(null)
            return
        } catch (error: Exception) {
            Log.e(LocationVerificationLogTag, "마감 위치 요청을 시작하지 못했습니다.", error)
            complete(null)
            return
        }

        registrationTask.addOnCompleteListener { completedTask ->
            if (completedTask.isSuccessful) {
                if (isCompleted.get()) {
                    removeLocationUpdates()
                }
            } else {
                Log.e(
                    LocationVerificationLogTag,
                    "마감 위치 구독 등록에 실패했습니다.",
                    completedTask.exception,
                )
                complete(null)
            }
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

    private companion object {
        const val LocationVerificationLogTag = "RingoutMissionLocation"
        const val DeadlineLocationUpdateIntervalMillis = 500L
    }
}
