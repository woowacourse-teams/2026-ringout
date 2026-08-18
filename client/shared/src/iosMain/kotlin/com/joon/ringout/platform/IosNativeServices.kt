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

interface IosGoogleSignInCallback {
    fun onSuccess(accessToken: String)

    fun onCancelled()

    fun onFailure(message: String)
}

interface IosGoogleSignInService {
    fun signIn(callback: IosGoogleSignInCallback)
}

interface IosKakaoSignInCallback {
    fun onSuccess(accessToken: String)

    fun onCancelled()

    fun onFailure(message: String)
}

interface IosKakaoSignInService {
    fun signIn(callback: IosKakaoSignInCallback)
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

    fun analyticsTracker(): IosAnalyticsTracker

    fun googleSignInService(): IosGoogleSignInService

    fun kakaoSignInService(): IosKakaoSignInService
}

val LocalIosNativeServices = staticCompositionLocalOf<IosNativeServices> {
    error("IosNativeServices is not available outside the iOS app entry point.")
}
