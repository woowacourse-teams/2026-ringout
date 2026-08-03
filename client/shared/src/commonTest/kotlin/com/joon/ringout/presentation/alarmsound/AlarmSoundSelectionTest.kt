package com.joon.ringout.presentation.alarmsound

import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
