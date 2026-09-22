package com.joon.ringout.presentation.alarmsound

import com.joon.ringout.analytics.AnalyticsAlarmSoundSurface
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmSoundSelectionTest {
    private val sounds = listOf(
        AlarmSoundSelection("기본 알람음", null),
        AlarmSoundSelection("첫 번째", "content://alarm/1"),
        AlarmSoundSelection("두 번째", "content://alarm/2"),
    )

    @Test
    fun resolvesDeviceSoundByUri() {
        val resolved = resolveInitialAlarmSoundSelection(
            sounds = sounds,
            current = AlarmSoundSelection("이름이 달라도 같은 음원", "content://alarm/2"),
        )

        assertEquals(sounds[2], resolved)
    }

    @Test
    fun nullUriResolvesSystemDefaultSound() {
        val resolved = resolveInitialAlarmSoundSelection(
            sounds = sounds,
            current = AlarmSoundSelection("Ring Ring Ring", null),
        )

        assertEquals(sounds.first(), resolved)
    }

    @Test
    fun missingSoundFallsBackToFirstAvailableSound() {
        val resolved = resolveInitialAlarmSoundSelection(
            sounds = sounds,
            current = AlarmSoundSelection("사라진 알람음", "content://alarm/missing"),
        )

        assertEquals(sounds.first(), resolved)
    }

    @Test
    fun recordsOneBasedPositionAndWhetherTheSelectionChanged() {
        val initial = sounds[0]

        val unchanged = resolveAlarmSoundDisplaySelection(
            sounds = sounds,
            initialSelection = initial,
            selectedSound = sounds[0],
            surface = AnalyticsAlarmSoundSurface.OnboardingStep,
        )
        val changed = resolveAlarmSoundDisplaySelection(
            sounds = sounds,
            initialSelection = initial,
            selectedSound = sounds[2],
            surface = AnalyticsAlarmSoundSurface.EditorPicker,
        )

        assertEquals(1, unchanged?.position)
        assertEquals(3, unchanged?.listSize)
        assertFalse(unchanged?.selectionChanged == true)
        assertEquals(AnalyticsAlarmSoundSurface.EditorPicker, changed?.surface)
        assertEquals(3, changed?.position)
        assertTrue(changed?.selectionChanged == true)
    }

    @Test
    fun missingSelectedSoundDoesNotInventAListPosition() {
        assertNull(
            resolveAlarmSoundDisplaySelection(
                sounds = sounds,
                initialSelection = sounds.first(),
                selectedSound = AlarmSoundSelection("missing", "content://missing"),
                surface = AnalyticsAlarmSoundSurface.EditorPicker,
            ),
        )
    }
}
