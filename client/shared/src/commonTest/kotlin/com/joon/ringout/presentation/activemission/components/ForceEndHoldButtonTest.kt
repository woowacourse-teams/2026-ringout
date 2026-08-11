package com.joon.ringout.presentation.activemission.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForceEndHoldButtonTest {
    @Test
    fun startsWithFullThirtySecondHoldRemaining() {
        assertEquals(
            expected = 30,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = 0L),
        )
    }

    @Test
    fun roundsPartialSecondsUpUntilHoldCompletes() {
        assertEquals(
            expected = 15,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = 15_000L),
        )
        assertEquals(
            expected = 1,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = 29_999L),
        )
        assertEquals(
            expected = 0,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = 30_000L),
        )
    }

    @Test
    fun clampsProgressOutsideExpectedRange() {
        assertEquals(
            expected = 30,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = -1L),
        )
        assertEquals(
            expected = 0,
            actual = forceEndHoldRemainingSeconds(elapsedMillis = 60_000L),
        )
    }

    @Test
    fun rejectsNonPositiveDuration() {
        assertFailsWith<IllegalArgumentException> {
            forceEndHoldRemainingSeconds(
                elapsedMillis = 0L,
                durationSeconds = 0,
            )
        }
    }

    @Test
    fun usesActualElapsedTimeForLinearHoldProgress() {
        assertEquals(
            expected = 0f,
            actual = forceEndHoldProgress(
                elapsedMillis = 0L,
                isHolding = true,
            ),
        )
        assertEquals(
            expected = 0.5f,
            actual = forceEndHoldProgress(
                elapsedMillis = 15_000L,
                isHolding = true,
            ),
        )
        assertTrue(
            forceEndHoldProgress(
                elapsedMillis = 29_999L,
                isHolding = true,
            ) < 1f,
        )
        assertEquals(
            expected = 1f,
            actual = forceEndHoldProgress(
                elapsedMillis = 30_000L,
                isHolding = true,
            ),
        )
    }

    @Test
    fun releaseOrCancellationResetsHoldProgress() {
        assertEquals(
            expected = 0f,
            actual = forceEndHoldProgress(
                elapsedMillis = 29_999L,
                isHolding = false,
            ),
        )
    }

    @Test
    fun completionGateDispatchesOncePerContinuousHold() {
        val gate = ForceEndHoldCompletionGate()

        assertFalse(
            gate.shouldComplete(
                elapsedMillis = 29_999L,
                isHolding = true,
            ),
        )
        assertTrue(
            gate.shouldComplete(
                elapsedMillis = 30_000L,
                isHolding = true,
            ),
        )
        assertFalse(
            gate.shouldComplete(
                elapsedMillis = 60_000L,
                isHolding = true,
            ),
        )

        assertFalse(
            gate.shouldComplete(
                elapsedMillis = 60_000L,
                isHolding = false,
            ),
        )
        assertTrue(
            gate.shouldComplete(
                elapsedMillis = 30_000L,
                isHolding = true,
            ),
        )
    }
}
