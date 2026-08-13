package com.joon.ringout.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.joon.ringout.alarm.IosAlarmMissionEventInbox
import com.joon.ringout.alarm.IosAlarmScheduler
import com.joon.ringout.alarm.IosMissionLocationService

enum class IosAlarmAuthorizationState {
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
}

interface IosNativeServices {
    fun isMapsAvailable(): Boolean

    fun isPlacesAvailable(): Boolean

    fun createDestinationMapController(
        initialLatitude: Double,
        initialLongitude: Double,
        listener: IosDestinationMapListener,
    ): IosDestinationMapController?

    fun createActiveMissionMapController(
        destinationLatitude: Double,
        destinationLongitude: Double,
    ): IosActiveMissionMapController?

    fun destinationSearchService(): IosDestinationSearchService

    fun destinationLocationService(): IosDestinationLocationService

    fun alarmAuthorizationState(): IosAlarmAuthorizationState

    fun normalizeAlarmId(id: String): String?

    fun alarmScheduler(): IosAlarmScheduler

    fun alarmMissionEventInbox(): IosAlarmMissionEventInbox

    fun missionLocationService(): IosMissionLocationService
}

val LocalIosNativeServices = staticCompositionLocalOf<IosNativeServices> {
    error("IosNativeServices is not available outside the iOS app entry point.")
}
