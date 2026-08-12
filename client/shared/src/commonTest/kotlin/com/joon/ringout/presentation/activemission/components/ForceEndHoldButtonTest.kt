package com.joon.ringout.presentation.activemission.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun earlyReleaseIsCancelledOnceWithMeasuredDuration() {
        val state = ForceEndHoldInteractionState()

        val attemptId = assertNotNull(state.start())
        assertNull(state.start())
        val result = assertIs<ForceEndHoldResult.Cancelled>(
            state.finish(
                attemptId = attemptId,
                elapsedMillis = 12_345L,
            ),
        )

        assertEquals(12_345L, result.holdDurationMillis)
        assertNull(
            state.finish(
                attemptId = attemptId,
                elapsedMillis = 20_000L,
            ),
        )
    }

    @Test
    fun thresholdReleaseIsCompletedAndLaterReleaseIsIgnored() {
        val state = ForceEndHoldInteractionState()

        val attemptId = assertNotNull(state.start())
        val result = assertIs<ForceEndHoldResult.Completed>(
            state.finish(
                attemptId = attemptId,
                elapsedMillis = 30_000L,
            ),
        )

        assertEquals(30_000L, result.holdDurationMillis)
        assertNull(
            state.finish(
                attemptId = attemptId,
                elapsedMillis = 30_001L,
            ),
        )
    }

    @Test
    fun finishedAttemptAllowsANewIndependentAttempt() {
        val state = ForceEndHoldInteractionState()

        val firstAttemptId = assertNotNull(state.start())
        assertIs<ForceEndHoldResult.Cancelled>(
            state.finish(
                attemptId = firstAttemptId,
                elapsedMillis = 1_000L,
            ),
        )
        val secondAttemptId = assertNotNull(state.start())
        assertIs<ForceEndHoldResult.Completed>(
            state.finish(
                attemptId = secondAttemptId,
                elapsedMillis = 31_000L,
            ),
        )
    }

    @Test
    fun negativeMeasuredDurationIsClampedToZero() {
        val state = ForceEndHoldInteractionState()

        val attemptId = assertNotNull(state.start())
        val result = assertIs<ForceEndHoldResult.Cancelled>(
            state.finish(
                attemptId = attemptId,
                elapsedMillis = -1L,
            ),
        )

        assertEquals(0L, result.holdDurationMillis)
    }

    @Test
    fun staleInputCannotFinishANewerAttempt() {
        val state = ForceEndHoldInteractionState()
        val firstAttemptId = assertNotNull(state.start())
        assertIs<ForceEndHoldResult.Cancelled>(
            state.cancel(
                attemptId = firstAttemptId,
                elapsedMillis = 1_000L,
            ),
        )
        val secondAttemptId = assertNotNull(state.start())

        assertNull(
            state.finish(
                attemptId = firstAttemptId,
                elapsedMillis = 5_000L,
            ),
        )
        assertIs<ForceEndHoldResult.Cancelled>(
            state.finish(
                attemptId = secondAttemptId,
                elapsedMillis = 2_000L,
            ),
        )
    }

    @Test
    fun lifecycleCancellationNeverCompletesAnAttempt() {
        val state = ForceEndHoldInteractionState()
        val attemptId = assertNotNull(state.start())

        val result = assertIs<ForceEndHoldResult.Cancelled>(
            state.cancel(
                attemptId = attemptId,
                elapsedMillis = 31_000L,
            ),
        )

        assertEquals(31_000L, result.holdDurationMillis)
    }
}
