package com.joon.ringout.presentation.activemission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.MissionRecoveryReservationState
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MaximumAcceptedLocationAccuracyMeters
import com.joon.ringout.alarm.MaximumTrackingLocationAgeMillis
import com.joon.ringout.alarm.MissionLocationAccuracyState
import com.joon.ringout.alarm.MissionLocationAuthorizationState
import com.joon.ringout.alarm.MissionLocationServicesState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.presentation.activemission.components.ActiveAlarmLocationStatusBanner
import com.joon.ringout.presentation.activemission.components.ActiveAlarmMapUnavailable
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingCard
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingHeader
import com.joon.ringout.presentation.activemission.components.activeAlarmTrackingColors
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.home.components.formatAlarmMissionRemainingTime
import com.joon.ringout.presentation.home.components.rememberActiveAlarmMissionRemainingSeconds
import kotlin.time.Clock

@Composable
fun ActiveAlarmTrackingScreen(
    mission: ActiveAlarmMission,
    currentLocation: ActiveAlarmMissionLocation?,
    onBackClick: () -> Unit,
    onForceEndClick: (occurrenceId: String) -> Unit,
    onExpired: () -> Unit,
    locationState: MissionLocationState = DefaultMissionLocationState,
    modifier: Modifier = Modifier,
    isForceEndEnabled: Boolean = true,
    onForceEndHoldStarted: (occurrenceId: String) -> Unit = {},
    onForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
    onForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
) {
    val remainingSeconds = rememberActiveAlarmMissionRemainingSeconds(
        mission = mission,
        onExpired = onExpired,
    )
    val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
    val validCurrentLocation = currentLocation?.takeIf { location ->
        isUsableActiveAlarmMapLocation(
            location = location,
            nowEpochMillis = nowEpochMillis,
        )
    }
    val hasValidDestination = isValidMapCoordinate(
        latitude = mission.destinationLatitude,
        longitude = mission.destinationLongitude,
    )
    var mapError by remember(mission.occurrenceId) {
        mutableStateOf<String?>(null)
    }
    PlatformBackHandler(onBack = onBackClick)

    ActiveAlarmTrackingLayout(
        missionOccurrenceId = mission.occurrenceId,
        destinationName = mission.destinationName,
        countdown = if (mission.recoveryReservationState != null && remainingSeconds == 0L)
            "제한 시간 만료" else formatAlarmMissionRemainingTime(remainingSeconds),
        trackingStatusMessage = when (mission.recoveryReservationState) {
            MissionRecoveryReservationState.FAILED -> "재울림을 예약하지 못했습니다. 알람 권한을 확인해 주세요."
            MissionRecoveryReservationState.UNKNOWN -> "재울림 예약 여부를 확인하지 못했습니다."
            MissionRecoveryReservationState.PENDING -> "재울림 예약을 확인하고 있습니다."
            else -> missionLocationStatusMessage(locationState)
        },
        mapUnavailableMessage = when {
            !hasValidDestination -> "설정한 목적지의 위치 정보가 없습니다."
            mapError != null -> mapError
            else -> null
        },
        onBackClick = onBackClick,
        onForceEndHoldStarted = {
            if (isForceEndEnabled) {
                onForceEndHoldStarted(mission.occurrenceId)
            }
        },
        onForceEndHoldCancelled = { holdDurationMillis ->
            if (isForceEndEnabled) {
                onForceEndHoldCancelled(
                    mission.occurrenceId,
                    holdDurationMillis,
                )
            }
        },
        onForceEndHoldComplete = { holdDurationMillis ->
            if (isForceEndEnabled) {
                onForceEndHoldCompleted(
                    mission.occurrenceId,
                    holdDurationMillis,
                )
                onForceEndClick(mission.occurrenceId)
            }
        },
        isForceEndEnabled = isForceEndEnabled,
        modifier = modifier,
        mapContent = { mapModifier ->
            PlatformActiveAlarmMap(
                destinationLatitude = mission.destinationLatitude,
                destinationLongitude = mission.destinationLongitude,
                currentLatitude = validCurrentLocation?.latitude,
                currentLongitude = validCurrentLocation?.longitude,
                onMapError = { error -> mapError = error },
                modifier = mapModifier,
            )
        },
    )
}

@Composable
private fun ActiveAlarmTrackingLayout(
    missionOccurrenceId: String,
    destinationName: String,
    countdown: String,
    trackingStatusMessage: String?,
    mapUnavailableMessage: String?,
    onBackClick: () -> Unit,
    onForceEndHoldStarted: () -> Unit,
    onForceEndHoldCancelled: (holdDurationMillis: Long) -> Unit,
    onForceEndHoldComplete: (holdDurationMillis: Long) -> Unit,
    isForceEndEnabled: Boolean,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
    val colors = activeAlarmTrackingColors()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.screenChrome)
            .semantics { paneTitle = "목적지로 이동 중" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (mapUnavailableMessage == null) {
                mapContent(Modifier.fillMaxSize())
            } else {
                ActiveAlarmMapUnavailable(
                    message = mapUnavailableMessage,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ActiveAlarmTrackingHeader(
                onBackClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 10.dp, top = 18.dp, end = 11.dp),
            )

            trackingStatusMessage?.let { message ->
                ActiveAlarmLocationStatusBanner(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 10.dp, top = 92.dp, end = 11.dp),
                )
            }

            key(missionOccurrenceId) {
                ActiveAlarmTrackingCard(
                    destinationName = destinationName,
                    countdown = countdown,
                    onForceEndHoldStarted = onForceEndHoldStarted,
                    onForceEndHoldCancelled = onForceEndHoldCancelled,
                    onForceEndHoldComplete = onForceEndHoldComplete,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isForceEndEnabled = isForceEndEnabled,
                )
            }
        }
    }
}

internal fun isValidMapCoordinate(
    latitude: Double,
    longitude: Double,
): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

internal fun isUsableActiveAlarmMapLocation(
    location: ActiveAlarmMissionLocation,
    nowEpochMillis: Long,
): Boolean {
    if (!isValidMapCoordinate(location.latitude, location.longitude)) return false
    if (
        !location.accuracyMeters.isFinite() ||
        location.accuracyMeters < 0f ||
        location.accuracyMeters > MaximumAcceptedLocationAccuracyMeters
    ) {
        return false
    }
    if (location.capturedAtEpochMillis > nowEpochMillis) return false

    val earliestUsableLocationEpochMillis =
        if (nowEpochMillis < Long.MIN_VALUE + MaximumTrackingLocationAgeMillis) {
            Long.MIN_VALUE
        } else {
            nowEpochMillis - MaximumTrackingLocationAgeMillis
        }
    return location.capturedAtEpochMillis >= earliestUsableLocationEpochMillis
}

internal fun missionLocationStatusMessage(state: MissionLocationState): String? = when {
    state.services == MissionLocationServicesState.DISABLED ->
        "위치 서비스가 꺼져 있어 도착 여부를 확인할 수 없어요."

    state.authorization == MissionLocationAuthorizationState.DENIED ||
        state.authorization == MissionLocationAuthorizationState.RESTRICTED ->
        "위치 권한이 없어 도착 여부를 확인할 수 없어요."

    state.authorization == MissionLocationAuthorizationState.WHEN_IN_USE &&
        state.accuracy == MissionLocationAccuracyState.REDUCED ->
        "정확한 위치가 꺼져 있어 도착 판정이 늦어질 수 있어요. 앱을 강제 종료하면 추적이 중단돼요."

    state.authorization == MissionLocationAuthorizationState.WHEN_IN_USE ->
        "앱을 강제 종료하면 위치 추적과 도착 확인이 중단돼요."

    state.authorization != MissionLocationAuthorizationState.ALWAYS ->
        "위치 권한을 확인하고 있어요."

    state.accuracy == MissionLocationAccuracyState.REDUCED ->
        "정확한 위치가 꺼져 있어 도착 판정이 늦어질 수 있어요."

    else -> null
}

@Preview(name = "Dark active alarm map", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingLayout(
            missionOccurrenceId = "preview-dark",
            destinationName = "스터디카페",
            countdown = "11:42",
            trackingStatusMessage = null,
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = true,
            mapContent = { mapModifier ->
                Box(
                    modifier = mapModifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            },
        )
    }
}

@Preview(name = "Light active alarm map", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        ActiveAlarmTrackingLayout(
            missionOccurrenceId = "preview-light",
            destinationName = "스터디카페",
            countdown = "11:42",
            trackingStatusMessage = null,
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = true,
            mapContent = { mapModifier ->
                Box(
                    modifier = mapModifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            },
        )
    }
}

@Preview(name = "Force end processing", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingScreenProcessingPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingLayout(
            missionOccurrenceId = "preview-dialog",
            destinationName = "스터디카페",
            countdown = "11:42",
            trackingStatusMessage = "정확한 위치가 꺼져 있어 도착 판정이 늦어질 수 있어요.",
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = false,
            mapContent = { mapModifier ->
                Box(
                    modifier = mapModifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            },
        )
    }
}
