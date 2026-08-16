package com.joon.ringout.alarm

interface IosMissionLocationListener {
    fun onStateChanged(state: MissionLocationState)

    fun onLocation(
        occurrenceId: String,
        location: ActiveAlarmMissionLocation,
        elapsedRealtimeNanos: Long,
    )

    fun onError(message: String)
}

interface IosMissionLocationService {
    fun currentState(): MissionLocationState

    fun setListener(listener: IosMissionLocationListener?)

    fun requestWhenInUseAuthorization()

    fun requestAlwaysAuthorization()

    fun confirmAlwaysAuthorizationResult()

    fun requestTemporaryFullAccuracyAuthorization(purposeKey: String)

    fun startTracking(occurrenceId: String)

    fun stopTracking()

    fun beginBackgroundRecovery(recoveryId: String)

    fun endBackgroundRecovery(recoveryId: String)
}

internal const val ActiveMissionFullAccuracyPurposeKey = "RingoutActiveMission"
