package com.joon.ringout.analytics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountWithdrawalAnalyticsTest {
    @Test
    fun successfulWithdrawalRecordsTheEventBeforeLogout() = runTest {
        val calls = mutableListOf<String>()
        val recorder = WithdrawalProductAnalyticsRecorder {
            calls += "analytics"
        }

        completeAccountWithdrawal(
            withdraw = { calls += "withdraw" },
            productAnalyticsRecorder = recorder,
            logout = { calls += "logout" },
        )

        assertEquals(listOf("withdraw", "analytics", "logout"), calls)
    }

    @Test
    fun withdrawalFailureDoesNotRecordOrLogout() = runTest {
        val calls = mutableListOf<String>()
        val recorder = WithdrawalProductAnalyticsRecorder {
            calls += "analytics"
        }

        assertFailsWith<IllegalStateException> {
            completeAccountWithdrawal(
                withdraw = {
                    calls += "withdraw"
                    error("withdraw failed")
                },
                productAnalyticsRecorder = recorder,
                logout = { calls += "logout" },
            )
        }

        assertEquals(listOf("withdraw"), calls)
    }

    @Test
    fun analyticsFailureDoesNotPreventLogout() = runTest {
        val calls = mutableListOf<String>()
        val recorder = WithdrawalProductAnalyticsRecorder {
            calls += "analytics"
            error("analytics failed")
        }

        completeAccountWithdrawal(
            withdraw = { calls += "withdraw" },
            productAnalyticsRecorder = recorder,
            logout = { calls += "logout" },
        )

        assertEquals(listOf("withdraw", "analytics", "logout"), calls)
    }

    @Test
    fun logoutFailureHappensAfterTheWithdrawalEvent() = runTest {
        val calls = mutableListOf<String>()
        val recorder = WithdrawalProductAnalyticsRecorder {
            calls += "analytics"
        }

        assertFailsWith<IllegalStateException> {
            completeAccountWithdrawal(
                withdraw = { calls += "withdraw" },
                productAnalyticsRecorder = recorder,
                logout = {
                    calls += "logout"
                    error("logout failed")
                },
            )
        }

        assertEquals(listOf("withdraw", "analytics", "logout"), calls)
    }
}

private class WithdrawalProductAnalyticsRecorder(
    private val onWithdrawalRecorded: () -> Unit,
) : ProductAnalyticsRecorder {
    override fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordAccountWithdrawalCompleted() = onWithdrawalRecorded()

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = Unit

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = Unit

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = Unit
}
