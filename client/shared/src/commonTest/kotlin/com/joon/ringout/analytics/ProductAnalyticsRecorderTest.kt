package com.joon.ringout.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductAnalyticsRecorderTest {
    @Test
    fun destinationCreationIsRecordedOnceWithoutTheDestinationId() {
        val tracker = RecordingProductAnalyticsTracker()
        val usageStore = RecordingProductAnalyticsUsageStore()
        val recorder = DefaultProductAnalyticsRecorder(tracker, usageStore)

        recorder.recordDestinationCreated(
            destinationId = 9_876_543_210L,
            loginState = AnalyticsLoginState.LoggedIn,
        )
        recorder.recordDestinationCreated(
            destinationId = 9_876_543_210L,
            loginState = AnalyticsLoginState.LoggedIn,
        )

        val event = tracker.events.single()
        assertEquals(AnalyticsEventName.DestinationCreated, event.name)
        assertEquals(
            setOf(
                AnalyticsParameterName.CreationIndex,
                AnalyticsParameterName.LoginState,
            ),
            event.parameters.keys,
        )
        assertEquals(1L, event.number(AnalyticsParameterName.CreationIndex))
        assertEquals("logged_in", event.text(AnalyticsParameterName.LoginState))
        assertFalse(event.parameters.values.joinToString().contains("9876543210"))
    }

    @Test
    fun destinationCreationIndexIncreasesOnlyForNewClaims() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = DefaultProductAnalyticsRecorder(
            tracker = tracker,
            usageStore = RecordingProductAnalyticsUsageStore(),
        )

        recorder.recordDestinationCreated(1L, AnalyticsLoginState.LoggedOut)
        recorder.recordDestinationCreated(1L, AnalyticsLoginState.LoggedOut)
        recorder.recordDestinationCreated(2L, AnalyticsLoginState.LoggedOut)
        recorder.recordDestinationCreated(0L, AnalyticsLoginState.LoggedOut)

        assertEquals(
            listOf(1L, 2L),
            tracker.events.map { event ->
                event.number(AnalyticsParameterName.CreationIndex)
            },
        )
    }

    @Test
    fun destinationSelectionUsesStableSourceAndLoginStateValues() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        recorder.recordDestinationSelected(
            source = DestinationSelectionSource.MapShortcut,
            loginState = AnalyticsLoginState.LoggedIn,
        )
        recorder.recordDestinationSelected(
            source = DestinationSelectionSource.SearchShortcut,
            loginState = AnalyticsLoginState.LoggedOut,
        )

        assertEquals(
            listOf(
                "map_shortcut" to "logged_in",
                "search_shortcut" to "logged_out",
            ),
            tracker.events.map { event ->
                assertEquals(AnalyticsEventName.DestinationSelected, event.name)
                assertEquals(
                    setOf(
                        AnalyticsParameterName.Source,
                        AnalyticsParameterName.LoginState,
                    ),
                    event.parameters.keys,
                )
                event.text(AnalyticsParameterName.Source) to
                    event.text(AnalyticsParameterName.LoginState)
            },
        )
    }

    @Test
    fun stampCalendarViewUsesNumericYearAndMonth() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        recorder.recordStampCalendarViewed(
            year = 2026,
            month = 8,
            loginState = AnalyticsLoginState.LoggedIn,
        )

        val event = tracker.events.single()
        assertEquals(AnalyticsEventName.StampCalendarViewed, event.name)
        assertEquals(2026L, event.number(AnalyticsParameterName.Year))
        assertEquals(8L, event.number(AnalyticsParameterName.Month))
        assertEquals("logged_in", event.text(AnalyticsParameterName.LoginState))
        assertTrue(
            event.parameters[AnalyticsParameterName.Year] is AnalyticsParameterValue.Number,
        )
        assertTrue(
            event.parameters[AnalyticsParameterName.Month] is AnalyticsParameterValue.Number,
        )
    }

    @Test
    fun stampMonthChangeUsesStableDirectionAndTargetMonth() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        recorder.recordStampMonthChanged(
            direction = StampMonthChangeDirection.Previous,
            year = 2025,
            month = 12,
            loginState = AnalyticsLoginState.LoggedOut,
        )
        recorder.recordStampMonthChanged(
            direction = StampMonthChangeDirection.Next,
            year = 2026,
            month = 1,
            loginState = AnalyticsLoginState.LoggedOut,
        )

        assertEquals(
            listOf("previous", "next"),
            tracker.events.map { event ->
                assertEquals(AnalyticsEventName.StampMonthChanged, event.name)
                assertEquals("logged_out", event.text(AnalyticsParameterName.LoginState))
                event.text(AnalyticsParameterName.Direction)
            },
        )
        assertEquals(2025L, tracker.events[0].number(AnalyticsParameterName.Year))
        assertEquals(12L, tracker.events[0].number(AnalyticsParameterName.Month))
        assertEquals(2026L, tracker.events[1].number(AnalyticsParameterName.Year))
        assertEquals(1L, tracker.events[1].number(AnalyticsParameterName.Month))
    }

    @Test
    fun accountWithdrawalCompletedHasNoParameters() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        recorder.recordAccountWithdrawalCompleted()

        assertEquals(AnalyticsEventName.AccountWithdrawalCompleted, tracker.events.single().name)
        assertTrue(tracker.events.single().parameters.isEmpty())
    }

    @Test
    fun loginStartedUsesOnlyTheStableProviderValue() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        AnalyticsAuthProvider.entries.forEach(recorder::recordLoginStarted)

        assertEquals(
            listOf("google", "kakao", "apple"),
            tracker.events.map { event ->
                assertEquals(AnalyticsEventName.LoginStarted, event.name)
                assertEquals(setOf(AnalyticsParameterName.Provider), event.parameters.keys)
                event.text(AnalyticsParameterName.Provider)
            },
        )
    }

    @Test
    fun loginCompletedSerializesNewUserAsFirebaseNumber() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        recorder.recordLoginCompleted(
            provider = AnalyticsAuthProvider.Google,
            isNewUser = false,
        )
        recorder.recordLoginCompleted(
            provider = AnalyticsAuthProvider.Kakao,
            isNewUser = true,
        )

        assertEquals(
            listOf("google" to 0L, "kakao" to 1L),
            tracker.events.map { event ->
                assertEquals(AnalyticsEventName.LoginCompleted, event.name)
                assertEquals(
                    setOf(
                        AnalyticsParameterName.Provider,
                        AnalyticsParameterName.IsNewUser,
                    ),
                    event.parameters.keys,
                )
                assertTrue(
                    event.parameters[AnalyticsParameterName.IsNewUser] is
                        AnalyticsParameterValue.Number,
                )
                event.text(AnalyticsParameterName.Provider) to
                    event.number(AnalyticsParameterName.IsNewUser)
            },
        )
    }

    @Test
    fun signupCompletedUsesOnlyTheStableProviderValue() {
        val tracker = RecordingProductAnalyticsTracker()
        val recorder = recorderWith(tracker)

        AnalyticsAuthProvider.entries.forEach(recorder::recordSignupCompleted)

        assertEquals(
            listOf("google", "kakao", "apple"),
            tracker.events.map { event ->
                assertEquals(AnalyticsEventName.SignupCompleted, event.name)
                assertEquals(setOf(AnalyticsParameterName.Provider), event.parameters.keys)
                event.text(AnalyticsParameterName.Provider)
            },
        )
    }

    @Test
    fun analyticsFailuresNeverEscapeToProductFlows() {
        val recorder = DefaultProductAnalyticsRecorder(
            tracker = AnalyticsTracker { error("tracker failure") },
            usageStore = ProductAnalyticsUsageStore { 1L },
        )

        recorder.recordDestinationCreated(1L, AnalyticsLoginState.LoggedIn)
        recorder.recordDestinationSelected(
            DestinationSelectionSource.MapShortcut,
            AnalyticsLoginState.LoggedIn,
        )
        recorder.recordStampCalendarViewed(2026, 8, AnalyticsLoginState.LoggedIn)
        recorder.recordStampMonthChanged(
            StampMonthChangeDirection.Next,
            2026,
            9,
            AnalyticsLoginState.LoggedIn,
        )
        recorder.recordAccountWithdrawalCompleted()
        recorder.recordLoginStarted(AnalyticsAuthProvider.Google)
        recorder.recordLoginCompleted(
            provider = AnalyticsAuthProvider.Kakao,
            isNewUser = true,
        )
        recorder.recordSignupCompleted(AnalyticsAuthProvider.Apple)

        DefaultProductAnalyticsRecorder(
            tracker = RecordingProductAnalyticsTracker(),
            usageStore = ProductAnalyticsUsageStore { error("usage store failure") },
        ).recordDestinationCreated(1L, AnalyticsLoginState.LoggedIn)
    }

    private fun recorderWith(
        tracker: RecordingProductAnalyticsTracker,
    ): DefaultProductAnalyticsRecorder = DefaultProductAnalyticsRecorder(
        tracker = tracker,
        usageStore = RecordingProductAnalyticsUsageStore(),
    )
}

private class RecordingProductAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}

private class RecordingProductAnalyticsUsageStore : ProductAnalyticsUsageStore {
    private val claimedKeys = mutableSetOf<String>()
    private var creationCounter = 0L

    override fun claimDestinationCreation(destinationKey: String): Long? {
        if (!claimedKeys.add(destinationKey)) return null
        creationCounter += 1L
        return creationCounter
    }
}

private fun AnalyticsEvent.number(name: AnalyticsParameterName): Long =
    (parameters.getValue(name) as AnalyticsParameterValue.Number).value

private fun AnalyticsEvent.text(name: AnalyticsParameterName): String =
    (parameters.getValue(name) as AnalyticsParameterValue.Text).value
