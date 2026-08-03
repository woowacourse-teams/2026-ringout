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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.presentation.activemission.components.ActiveAlarmMapUnavailable
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingCard
import com.joon.ringout.presentation.activemission.components.ActiveAlarmTrackingHeader
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.home.components.formatAlarmMissionRemainingTime
import com.joon.ringout.presentation.home.components.rememberActiveAlarmMissionRemainingSeconds

@Composable
fun ActiveAlarmTrackingScreen(
    mission: ActiveAlarmMission,
    currentLocation: ActiveAlarmMissionLocation?,
    onBackClick: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingSeconds = rememberActiveAlarmMissionRemainingSeconds(
        mission = mission,
        onExpired = onExpired,
    )
    val validCurrentLocation = currentLocation?.takeIf { location ->
        isValidMapCoordinate(location.latitude, location.longitude)
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
        destinationName = mission.destinationName,
        limitMinutes = mission.limitMinutes,
        countdown = formatAlarmMissionRemainingTime(remainingSeconds),
        hasCurrentLocation = validCurrentLocation != null,
        mapUnavailableMessage = when {
            !hasValidDestination -> "설정한 목적지의 위치 정보가 없습니다."
            mapError != null -> mapError
            else -> null
        },
        onBackClick = onBackClick,
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
    destinationName: String,
    limitMinutes: Int,
    countdown: String,
    hasCurrentLocation: Boolean,
    mapUnavailableMessage: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )

        ActiveAlarmTrackingCard(
            destinationName = destinationName,
            limitMinutes = limitMinutes,
            countdown = countdown,
            hasCurrentLocation = hasCurrentLocation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
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

@Preview(name = "Dark active alarm map", widthDp = 402, heightDp = 941)
@Composable
private fun ActiveAlarmTrackingScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingLayout(
            destinationName = "집 앞에 수원천",
            limitMinutes = 13,
            countdown = "08:42",
            hasCurrentLocation = true,
            mapUnavailableMessage = null,
            onBackClick = {},
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
            destinationName = "집 앞에 수원천",
            limitMinutes = 13,
            countdown = "08:42",
            hasCurrentLocation = false,
            mapUnavailableMessage = null,
            onBackClick = {},
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
