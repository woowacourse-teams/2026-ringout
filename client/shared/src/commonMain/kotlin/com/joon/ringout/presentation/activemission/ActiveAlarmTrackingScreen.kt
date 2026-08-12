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
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.MaximumAcceptedLocationAccuracyMeters
import com.joon.ringout.alarm.MaximumTrackingLocationAgeMillis
import com.joon.ringout.presentation.activemission.components.ActiveAlarmMapUnavailable
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingCard
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingHeader
import com.joon.ringout.presentation.activemission.components.ForceEndCompletedDialog
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
    modifier: Modifier = Modifier,
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
    var isForceEndDialogVisible by remember(mission.occurrenceId) {
        mutableStateOf(false)
    }
    var isForceEndRequested by remember(mission.occurrenceId) {
        mutableStateOf(false)
    }

    PlatformBackHandler(
        onBack = {
            if (!isForceEndDialogVisible) onBackClick()
        },
    )

    ActiveAlarmTrackingLayout(
        missionOccurrenceId = mission.occurrenceId,
        destinationName = mission.destinationName,
        countdown = formatAlarmMissionRemainingTime(remainingSeconds),
        mapUnavailableMessage = when {
            !hasValidDestination -> "설정한 목적지의 위치 정보가 없습니다."
            mapError != null -> mapError
            else -> null
        },
        onBackClick = onBackClick,
        onForceEndHoldStarted = {
            if (!isForceEndRequested) {
                onForceEndHoldStarted(mission.occurrenceId)
            }
        },
        onForceEndHoldCancelled = { holdDurationMillis ->
            if (!isForceEndRequested) {
                onForceEndHoldCancelled(
                    mission.occurrenceId,
                    holdDurationMillis,
                )
            }
        },
        onForceEndHoldComplete = { holdDurationMillis ->
            if (!isForceEndRequested) {
                onForceEndHoldCompleted(
                    mission.occurrenceId,
                    holdDurationMillis,
                )
                isForceEndDialogVisible = true
            }
        },
        isForceEndEnabled = !isForceEndDialogVisible && !isForceEndRequested,
        isForceEndDialogVisible = isForceEndDialogVisible,
        onForceEndConfirm = {
            if (!isForceEndRequested) {
                isForceEndRequested = true
                isForceEndDialogVisible = false
                onForceEndClick(mission.occurrenceId)
            }
        },
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
    mapUnavailableMessage: String?,
    onBackClick: () -> Unit,
    onForceEndHoldStarted: () -> Unit,
    onForceEndHoldCancelled: (holdDurationMillis: Long) -> Unit,
    onForceEndHoldComplete: (holdDurationMillis: Long) -> Unit,
    isForceEndEnabled: Boolean,
    isForceEndDialogVisible: Boolean,
    onForceEndConfirm: () -> Unit,
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

        if (isForceEndDialogVisible) {
            ForceEndCompletedDialog(
                onConfirm = onForceEndConfirm,
            )
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

@Preview(name = "Dark active alarm map", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingLayout(
            missionOccurrenceId = "preview-dark",
            destinationName = "스터디카페",
            countdown = "11:42",
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = true,
            isForceEndDialogVisible = false,
            onForceEndConfirm = {},
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
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = true,
            isForceEndDialogVisible = false,
            onForceEndConfirm = {},
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

@Preview(name = "Force end completed dialog", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingForceEndDialogPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingLayout(
            missionOccurrenceId = "preview-dialog",
            destinationName = "스터디카페",
            countdown = "11:42",
            mapUnavailableMessage = null,
            onBackClick = {},
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
            isForceEndEnabled = false,
            isForceEndDialogVisible = true,
            onForceEndConfirm = {},
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
