package com.joon.ringout.presentation.termsagreement

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignupViewModelTest {
    @Test
    fun successfulSignupRecordsItsProviderAfterSignupAndBeforeDestinationSync() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            viewModel.signup(
                signupToken = "signup-token",
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
                provider = AnalyticsAuthProvider.Kakao,
            )

            assertEquals(
                listOf("auth_signup", "analytics", "destination_sync"),
                analytics.order,
            )
            assertEquals(
                listOf(
                    SignupRequest(
                        signupToken = "signup-token",
                        agreedTerms = setOf(AuthTerm.SERVICE, AuthTerm.PRIVACY),
                        agreedAt = "2026-08-19",
                    ),
                ),
                authRepository.signupRequests,
            )
            assertEquals(listOf(AnalyticsAuthProvider.Kakao), analytics.signupProviders)
            assertEquals(1, destinationRepository.syncCount)
            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun destinationSyncFailureDoesNotLoseOrDuplicateTheCompletedSignupEvent() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            destinationRepository.syncFailuresRemaining = 1

            viewModel.signup(
                signupToken = "signup-token",
                agreedTermIds = setOf(TermId.Service),
                provider = AnalyticsAuthProvider.Google,
            )

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(1, authRepository.signupRequests.size)
            assertEquals(1, destinationRepository.syncCount)
            assertEquals("sync failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completedEventId)

            viewModel.signup(
                signupToken = "signup-token",
                agreedTermIds = setOf(TermId.Service),
                provider = AnalyticsAuthProvider.Google,
            )

            assertEquals(1, authRepository.signupRequests.size)
            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(2, destinationRepository.syncCount)
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun signupApiFailureDoesNotRecordAnalyticsOrSyncDestinations() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            authRepository.signupFailure = IllegalStateException("signup failed")

            viewModel.signup(
                signupToken = "signup-token",
                agreedTermIds = setOf(TermId.Service),
                provider = AnalyticsAuthProvider.Apple,
            )

            assertTrue(analytics.signupProviders.isEmpty())
            assertEquals(0, destinationRepository.syncCount)
            assertEquals("signup failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completedEventId)
            assertFalse(viewModel.uiState.isSaving)
        }

    @Test
    fun analyticsFailureDoesNotInterruptDestinationSyncOrCompletion() =
        withViewModel { viewModel, _, destinationRepository, analytics ->
            analytics.signupFailure = IllegalStateException("analytics failed")

            viewModel.signup(
                signupToken = "signup-token",
                agreedTermIds = setOf(TermId.Service),
                provider = AnalyticsAuthProvider.Apple,
            )

            assertEquals(
                listOf("auth_signup", "analytics", "destination_sync"),
                analytics.order,
            )
            assertEquals(1, destinationRepository.syncCount)
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun repeatedSignupWhileTheFirstRequestIsRunningIsIgnored() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            val signupGate = CompletableDeferred<Unit>()
            authRepository.signupGate = signupGate

            viewModel.signup(
                signupToken = "first-token",
                agreedTermIds = setOf(TermId.Service),
                provider = AnalyticsAuthProvider.Google,
            )
            viewModel.signup(
                signupToken = "second-token",
                agreedTermIds = setOf(TermId.Privacy),
                provider = AnalyticsAuthProvider.Apple,
            )

            assertTrue(viewModel.uiState.isSaving)
            assertEquals(1, authRepository.signupRequests.size)

            signupGate.complete(Unit)

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(1, destinationRepository.syncCount)
            assertFalse(viewModel.uiState.isSaving)
            assertNotNull(viewModel.uiState.completedEventId)
        }
}

private inline fun withViewModel(
    block: (
        SignupViewModel,
        FakeAuthRepository,
        FakeDestinationRepository,
        RecordingProductAnalyticsRecorder,
    ) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val order = mutableListOf<String>()
    val authRepository = FakeAuthRepository(order)
    val destinationRepository = FakeDestinationRepository(order)
    val analytics = RecordingProductAnalyticsRecorder(order)
    val viewModel = SignupViewModel(
        authRepository = authRepository,
        destinationRepository = destinationRepository,
        productAnalyticsRecorder = analytics,
        currentDate = { "2026-08-19" },
        coroutineScope = scope,
    )
    try {
        block(viewModel, authRepository, destinationRepository, analytics)
    } finally {
        scope.cancel()
    }
}

private data class SignupRequest(
    val signupToken: String,
    val agreedTerms: Set<AuthTerm>,
    val agreedAt: String,
)

private class FakeAuthRepository(
    private val order: MutableList<String>,
) : AuthRepository {
    val signupRequests = mutableListOf<SignupRequest>()
    var signupGate: CompletableDeferred<Unit>? = null
    var signupFailure: Throwable? = null

    override suspend fun restoreSession() = Unit

    override suspend fun loginWithApple(idToken: String): SocialLoginOutcome =
        SocialLoginOutcome.Authenticated

    override suspend fun loginWithGoogle(accessToken: String): SocialLoginOutcome =
        SocialLoginOutcome.Authenticated

    override suspend fun loginWithKakao(accessToken: String): SocialLoginOutcome =
        SocialLoginOutcome.Authenticated

    override suspend fun signup(
        signupToken: String,
        agreedTerms: Set<AuthTerm>,
        agreedAt: String,
    ) {
        signupRequests += SignupRequest(signupToken, agreedTerms, agreedAt)
        signupGate?.await()
        signupFailure?.let { throw it }
        order += "auth_signup"
    }

    override suspend fun logout() = Unit
}

private class FakeDestinationRepository(
    private val order: MutableList<String>,
) : DestinationRepository {
    private val destinations = MutableStateFlow(emptyList<SavedDestination>())

    var syncCount = 0
    var syncFailuresRemaining = 0

    override fun observeAll(): Flow<List<SavedDestination>> = destinations

    override suspend fun fetchAll(): List<SavedDestination> = destinations.value

    override suspend fun sync(): List<SavedDestination> {
        syncCount++
        order += "destination_sync"
        if (syncFailuresRemaining > 0) {
            syncFailuresRemaining--
            error("sync failed")
        }
        return destinations.value
    }

    override suspend fun save(destination: SavedDestination): SavedDestination = destination

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private class RecordingProductAnalyticsRecorder(
    val order: MutableList<String>,
) : ProductAnalyticsRecorder {
    val signupProviders = mutableListOf<AnalyticsAuthProvider>()
    var signupFailure: Throwable? = null

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

    override fun recordAccountWithdrawalCompleted() = Unit

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = Unit

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = Unit

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) {
        order += "analytics"
        signupProviders += provider
        signupFailure?.let { throw it }
    }
}
