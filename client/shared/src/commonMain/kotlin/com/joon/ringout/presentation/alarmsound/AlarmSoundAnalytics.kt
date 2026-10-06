package com.joon.ringout.presentation.alarmsound

import com.joon.ringout.analytics.AlarmSoundDisplaySelection
import com.joon.ringout.analytics.AnalyticsAlarmSoundSurface
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection

internal fun resolveAlarmSoundDisplaySelection(
    sounds: List<AlarmSoundSelection>,
    initialSelection: AlarmSoundSelection,
    selectedSound: AlarmSoundSelection,
    surface: AnalyticsAlarmSoundSurface,
): AlarmSoundDisplaySelection? {
    val position = sounds.indexOfFirst { it.sameSoundAs(selectedSound) }
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: return null
    return AlarmSoundDisplaySelection(
        surface = surface,
        position = position,
        listSize = sounds.size,
        selectionChanged = !initialSelection.sameSoundAs(selectedSound),
    )
}

internal fun AlarmSoundSelection.sameSoundAs(other: AlarmSoundSelection): Boolean =
    if (uri == null || other.uri == null) {
        uri == null && other.uri == null
    } else {
        uri == other.uri
    }
