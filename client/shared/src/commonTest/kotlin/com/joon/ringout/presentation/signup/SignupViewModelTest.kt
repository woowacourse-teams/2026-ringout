package com.joon.ringout.presentation.signup

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
import com.joon.ringout.domain.terms.TermId
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
    fun `회원가입 성공 후 제공자를 기록하고 목적지를 동기화한다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Kakao,
            )
            viewModel.signup(
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
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
            assertTrue(viewModel.uiState.hasPendingSignup)
        }

    @Test
    fun `목적지 동기화 실패 후 재시도해도 회원가입과 분석을 중복하지 않는다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            destinationRepository.syncFailuresRemaining = 1
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Google,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(1, authRepository.signupRequests.size)
            assertEquals(1, destinationRepository.syncCount)
            assertEquals("sync failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completedEventId)

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service),
            )

            assertEquals(1, authRepository.signupRequests.size)
            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(2, destinationRepository.syncCount)
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `회원가입 API가 실패하면 분석과 목적지 동기화를 실행하지 않는다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            authRepository.signupFailure = IllegalStateException("signup failed")
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Apple,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service),
            )

            assertTrue(analytics.signupProviders.isEmpty())
            assertEquals(0, destinationRepository.syncCount)
            assertEquals("signup failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completedEventId)
            assertFalse(viewModel.uiState.isSaving)
        }

    @Test
    fun `분석 기록이 실패해도 목적지 동기화와 완료 처리를 계속한다`() =
        withViewModel { viewModel, _, destinationRepository, analytics ->
            analytics.signupFailure = IllegalStateException("analytics failed")
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Apple,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service),
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
    fun `회원가입 요청이 진행 중이면 중복 요청을 무시한다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            val signupGate = CompletableDeferred<Unit>()
            authRepository.signupGate = signupGate
            viewModel.startSignup(
                signupToken = "first-token",
                provider = AnalyticsAuthProvider.Google,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service),
            )
            viewModel.signup(
                agreedTermIds = setOf(TermId.Privacy),
            )

            assertTrue(viewModel.uiState.isSaving)
            assertEquals(1, authRepository.signupRequests.size)

            signupGate.complete(Unit)

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertEquals(1, destinationRepository.syncCount)
            assertFalse(viewModel.uiState.isSaving)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `회원가입 시작 정보가 없으면 회원가입 요청을 실행하지 않는다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            viewModel.signup(agreedTermIds = setOf(TermId.Service))

            assertTrue(authRepository.signupRequests.isEmpty())
            assertTrue(analytics.signupProviders.isEmpty())
            assertEquals(0, destinationRepository.syncCount)
            assertFalse(viewModel.uiState.isSaving)
            assertFalse(viewModel.uiState.hasPendingSignup)
        }

    @Test
    fun `회원가입 상태를 초기화하면 진행 중인 요청 결과를 반영하지 않는다`() =
        withViewModel { viewModel, authRepository, destinationRepository, analytics ->
            val signupGate = CompletableDeferred<Unit>()
            authRepository.signupGate = signupGate
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Kakao,
            )
            viewModel.signup(agreedTermIds = setOf(TermId.Service))

            viewModel.resetSignup()
            signupGate.complete(Unit)

            assertFalse(viewModel.uiState.hasPendingSignup)
            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.completedEventId)
            assertTrue(analytics.signupProviders.isEmpty())
            assertEquals(0, destinationRepository.syncCount)
        }

    @Test
    fun `완료 이벤트를 소비하면 회원가입 시작 정보를 제거한다`() =
        withViewModel { viewModel, _, _, _ ->
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Google,
            )
            viewModel.signup(agreedTermIds = setOf(TermId.Service))
            val completedEventId = requireNotNull(viewModel.uiState.completedEventId)

            viewModel.consumeCompletedEvent(completedEventId)

            assertFalse(viewModel.uiState.hasPendingSignup)
            assertNull(viewModel.uiState.completedEventId)
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
