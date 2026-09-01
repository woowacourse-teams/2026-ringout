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

data class IosAnalyticsParameterDto(
    val name: String,
    val textValue: String? = null,
    val numberValue: Long? = null,
)

data class IosAnalyticsEventDto(
    val name: String,
    val parameters: List<IosAnalyticsParameterDto> = emptyList(),
)

interface IosAnalyticsTracker {
    fun log(event: IosAnalyticsEventDto)
}

// TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 iOS 소셜 로그인 service/callback 계약을 복구한다.

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

    fun analyticsTracker(): IosAnalyticsTracker
}

val LocalIosNativeServices = staticCompositionLocalOf<IosNativeServices> {
    error("IosNativeServices is not available outside the iOS app entry point.")
}
