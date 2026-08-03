package com.joon.ringout.alarm

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

data class ActiveAlarmMission(
    val alarmId: String,
    val destinationName: String,
    val limitMinutes: Int,
    val expiresAtEpochMillis: Long,
    val occurrenceId: String = alarmId,
    val retryAttempt: Int = 0,
    val alarmTime: String = "",
    val startedAtEpochMillis: Long = 0L,
    val destinationLatitude: Double = Double.NaN,
    val destinationLongitude: Double = Double.NaN,
    val arrivalRadiusMeters: Double = DefaultArrivalRadiusMeters,
    val alarmSoundUri: String? = null,
    val hasAlarmSoundUri: Boolean = false,
)

data class ActiveAlarmMissionLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
)

internal fun ActiveAlarmMission.isExpiredAt(epochMillis: Long): Boolean =
    expiresAtEpochMillis <= epochMillis

internal fun ActiveAlarmMission.hasReachedDestination(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
): Boolean {
    if (
        !isUsableDestinationLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
        )
    ) {
        return false
    }

    return distanceMetersBetween(
        startLatitude = latitude,
        startLongitude = longitude,
        endLatitude = destinationLatitude,
        endLongitude = destinationLongitude,
    ) <= arrivalRadiusMeters
}

internal fun ActiveAlarmMission.isUsableDestinationLocation(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
): Boolean {
    if (
        !destinationLatitude.isValidLatitude() ||
        !destinationLongitude.isValidLongitude() ||
        !latitude.isValidLatitude() ||
        !longitude.isValidLongitude() ||
        !arrivalRadiusMeters.isFinite() ||
        arrivalRadiusMeters <= 0.0 ||
        !accuracyMeters.isFinite() ||
        accuracyMeters < 0f
    ) {
        return false
    }

    val maximumAcceptedAccuracyMeters =
        arrivalRadiusMeters.coerceAtMost(MaximumAcceptedLocationAccuracyMeters)
    return accuracyMeters <= maximumAcceptedAccuracyMeters
}

internal fun ActiveAlarmMission.hasReachedDestinationByDeadline(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
    capturedAtEpochMillis: Long,
): Boolean =
    isUsableDeadlineLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMillis = capturedAtEpochMillis,
    ) &&
        hasReachedDestination(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
        )

internal fun ActiveAlarmMission.isUsableDeadlineLocation(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
    capturedAtEpochMillis: Long,
): Boolean {
    val earliestAcceptedLocationEpochMillis = maxOf(
        startedAtEpochMillis,
        expiresAtEpochMillis - MaximumDeadlineLocationAgeMillis,
    )
    return capturedAtEpochMillis in
        earliestAcceptedLocationEpochMillis..expiresAtEpochMillis &&
        isUsableDestinationLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
        )
}

internal fun distanceMetersBetween(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val latitudeDelta = (endLatitude - startLatitude).toRadians()
    val longitudeDelta = (endLongitude - startLongitude).toRadians()
    val startLatitudeRadians = startLatitude.toRadians()
    val endLatitudeRadians = endLatitude.toRadians()
    val haversine =
        sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(startLatitudeRadians) *
            cos(endLatitudeRadians) *
            sin(longitudeDelta / 2.0) *
            sin(longitudeDelta / 2.0)
    val angularDistance = 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    return EarthRadiusMeters * angularDistance
}

private fun Double.isValidLatitude(): Boolean = isFinite() && this in -90.0..90.0

private fun Double.isValidLongitude(): Boolean = isFinite() && this in -180.0..180.0

private fun Double.toRadians(): Double = this * PI / 180.0

internal const val DefaultArrivalRadiusMeters = 10.0
internal const val MaximumDeadlineLocationAgeMillis = 30_000L
internal const val DeadlineLocationAcquisitionTimeoutMillis = 5_000L
private const val MaximumAcceptedLocationAccuracyMeters = 100.0
private const val EarthRadiusMeters = 6_371_000.0
