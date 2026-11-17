package com.joon.ringout.presentation.signup

import androidx.lifecycle.ViewModelStore
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.presentation.signup.model.SignupUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignupViewModelTest {
    @Test
    fun `필수 약관 중 하나라도 동의하지 않으면 회원가입을 요청하지 않는다`() {
        listOf(emptySet(), setOf(TermId.Service), setOf(TermId.Privacy)).forEach { terms ->
            withViewModel { viewModel, repository, _ ->
                viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
                viewModel.signup(terms)

                assertTrue(repository.signupRequests.isEmpty())
                assertFalse(viewModel.uiState.isSaving)
                assertEquals("필수 약관에 모두 동의해 주세요.", viewModel.uiState.errorMessage)
            }
        }
    }

    @Test
    fun `저장소를 정리하면 가입 정보를 버리고 추가 가입을 무시한다`() =
        withViewModel { viewModel, repository, analytics ->
            viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))
            assertNotNull(viewModel.uiState.completedEventId)

            clearViewModel(viewModel)
            viewModel.startSignup("another-token", AnalyticsAuthProvider.Apple)
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))

            assertEquals(SignupUiState(), viewModel.uiState)
            assertEquals(1, repository.signupRequests.size)
            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
        }

    @Test
    fun `저장소를 정리하면 회원가입 작업만 취소한다`() {
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        withViewModel(scope = parentScope) { viewModel, repository, analytics ->
            val gate = CompletableDeferred<Unit>()
            var signupFinished = false
            repository.signupAction = {
                try {
                    gate.await()
                } finally {
                    signupFinished = true
                }
            }
            viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))
            assertTrue(viewModel.uiState.isSaving)

            clearViewModel(viewModel)

            assertTrue(signupFinished)
            assertFalse(gate.isCompleted)
            assertTrue(parentScope.isActive)
            assertEquals(SignupUiState(), viewModel.uiState)
            assertTrue(analytics.signupProviders.isEmpty())
        }
    }

    @Test
    fun `저장소 정리 후 취소에 협조하지 않는 가입 응답을 무시한다`() {
        val outcomes = listOf(
            Result.success(Unit),
            Result.failure<Unit>(IllegalStateException("late signup failure")),
        )
        outcomes.forEach { outcome ->
            withViewModel { viewModel, repository, analytics ->
                val gate = CompletableDeferred<Unit>()
                var backendReturned = false
                repository.signupAction = {
                    withContext(NonCancellable) { gate.await() }
                    backendReturned = true
                    outcome.getOrThrow()
                }
                viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
                viewModel.signup(setOf(TermId.Service, TermId.Privacy))

                clearViewModel(viewModel)
                gate.complete(Unit)

                assertTrue(backendReturned)
                assertEquals(SignupUiState(), viewModel.uiState)
                assertTrue(analytics.signupProviders.isEmpty())
            }
        }
    }

    @Test
    fun `회원가입 성공 후 제공자를 기록하고 완료 이벤트를 발행한다`() =
        withViewModel { viewModel, authRepository, analytics ->
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Kakao,
            )
            viewModel.signup(
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
            )

            assertEquals(
                listOf("auth_signup", "analytics"),
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
            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
            assertTrue(viewModel.uiState.hasPendingSignup)
        }

    @Test
    fun `완료 이벤트를 소비하기 전 다시 요청해도 회원가입과 분석을 중복하지 않는다`() =
        withViewModel { viewModel, authRepository, analytics ->
            viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))
            assertNotNull(viewModel.uiState.completedEventId)

            viewModel.signup(setOf(TermId.Service, TermId.Privacy))

            assertEquals(1, authRepository.signupRequests.size)
            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `회원가입 API가 실패하면 분석과 완료 처리를 실행하지 않는다`() =
        withViewModel { viewModel, authRepository, analytics ->
            authRepository.signupFailure = IllegalStateException("signup failed")
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Apple,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
            )

            assertTrue(analytics.signupProviders.isEmpty())
            assertEquals("signup failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completedEventId)
            assertFalse(viewModel.uiState.isSaving)
        }

    @Test
    fun `회원가입 실패 후 재시도하면 같은 토큰으로 가입하고 완료를 알린다`() =
        withViewModel { viewModel, authRepository, analytics ->
            authRepository.signupFailure = IllegalStateException("signup failed")
            viewModel.startSignup("signup-token", AnalyticsAuthProvider.Google)
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))
            assertNull(viewModel.uiState.completedEventId)

            authRepository.signupFailure = null
            viewModel.signup(setOf(TermId.Service, TermId.Privacy))

            assertEquals(
                listOf("signup-token", "signup-token"),
                authRepository.signupRequests.map { it.signupToken },
            )
            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertNull(viewModel.uiState.errorMessage)
            assertFalse(viewModel.uiState.isSaving)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `분석 기록이 실패해도 완료 처리를 계속한다`() =
        withViewModel { viewModel, _, analytics ->
            analytics.signupFailure = IllegalStateException("analytics failed")
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Apple,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
            )

            assertEquals(
                listOf("auth_signup", "analytics"),
                analytics.order,
            )
            assertNull(viewModel.uiState.errorMessage)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `회원가입 요청이 진행 중이면 중복 요청을 무시한다`() =
        withViewModel { viewModel, authRepository, analytics ->
            val signupGate = CompletableDeferred<Unit>()
            authRepository.signupGate = signupGate
            viewModel.startSignup(
                signupToken = "first-token",
                provider = AnalyticsAuthProvider.Google,
            )

            viewModel.signup(
                agreedTermIds = setOf(TermId.Service, TermId.Privacy),
            )
            viewModel.signup(
                agreedTermIds = setOf(TermId.Privacy),
            )

            assertTrue(viewModel.uiState.isSaving)
            assertEquals(1, authRepository.signupRequests.size)

            signupGate.complete(Unit)

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.signupProviders)
            assertFalse(viewModel.uiState.isSaving)
            assertNotNull(viewModel.uiState.completedEventId)
        }

    @Test
    fun `회원가입 시작 정보가 없으면 회원가입 요청을 실행하지 않는다`() =
        withViewModel { viewModel, authRepository, analytics ->
            viewModel.signup(agreedTermIds = setOf(TermId.Service, TermId.Privacy))

            assertTrue(authRepository.signupRequests.isEmpty())
            assertTrue(analytics.signupProviders.isEmpty())
            assertFalse(viewModel.uiState.isSaving)
            assertFalse(viewModel.uiState.hasPendingSignup)
        }

    @Test
    fun `회원가입 상태를 초기화하면 진행 중인 요청 결과를 반영하지 않는다`() =
        withViewModel { viewModel, authRepository, analytics ->
            val signupGate = CompletableDeferred<Unit>()
            authRepository.signupGate = signupGate
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Kakao,
            )
            viewModel.signup(agreedTermIds = setOf(TermId.Service, TermId.Privacy))

            viewModel.resetSignup()
            signupGate.complete(Unit)

            assertFalse(viewModel.uiState.hasPendingSignup)
            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.completedEventId)
            assertTrue(analytics.signupProviders.isEmpty())
        }

    @Test
    fun `완료 이벤트를 소비하면 회원가입 시작 정보를 제거한다`() =
        withViewModel { viewModel, _, _ ->
            viewModel.startSignup(
                signupToken = "signup-token",
                provider = AnalyticsAuthProvider.Google,
            )
            viewModel.signup(agreedTermIds = setOf(TermId.Service, TermId.Privacy))
            val completedEventId = requireNotNull(viewModel.uiState.completedEventId)

            viewModel.consumeCompletedEvent(completedEventId)

            assertFalse(viewModel.uiState.hasPendingSignup)
            assertNull(viewModel.uiState.completedEventId)
        }
}

private inline fun withViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    block: (
        SignupViewModel,
        FakeAuthRepository,
        RecordingProductAnalyticsRecorder,
    ) -> Unit,
) {
    val order = mutableListOf<String>()
    val authRepository = FakeAuthRepository(order)
    val analytics = RecordingProductAnalyticsRecorder(order)
    val viewModel = SignupViewModel(
        authRepository = authRepository,
        productAnalyticsRecorder = analytics,
        currentDate = { "2026-08-19" },
        coroutineScope = scope,
    )
    try {
        block(viewModel, authRepository, analytics)
    } finally {
        scope.cancel()
    }
}

private fun clearViewModel(viewModel: SignupViewModel) {
    val store = ViewModelStore()
    store.put("signup", viewModel)
    store.clear()
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
    var signupAction: (suspend () -> Unit)? = null

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
        signupAction?.invoke()
        signupGate?.await()
        signupFailure?.let { throw it }
        order += "auth_signup"
    }

    override suspend fun logout() = Unit
}

private class RecordingProductAnalyticsRecorder(
    val order: MutableList<String>,
) : ProductAnalyticsRecorder,
    OnboardingAnalyticsRecorder by NoOpOnboardingAnalyticsRecorder {
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
