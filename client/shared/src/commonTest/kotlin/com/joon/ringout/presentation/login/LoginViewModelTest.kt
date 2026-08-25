package com.joon.ringout.presentation.login

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginViewModelTest {
    @Test
    fun googleExistingMemberRecordsTheProviderLifecycle() =
        withViewModel { viewModel, repository, analytics ->
            repository.googleOutcome = SocialLoginOutcome.Authenticated

            assertTrue(viewModel.beginGoogleSignIn())
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("google-access-token"),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.startedProviders)
            assertEquals(
                listOf(LoginCompletedRecord(AnalyticsAuthProvider.Google, isNewUser = false)),
                analytics.completedRecords,
            )
            assertEquals(listOf("google-access-token"), repository.googleAccessTokens)
            assertTrue(repository.kakaoAccessTokens.isEmpty())
            assertIs<LoginCompletion.Authenticated>(viewModel.uiState.completion)
            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
        }

    @Test
    fun kakaoNewMemberRecordsCompletionAndPreservesTheProvider() =
        withViewModel { viewModel, repository, analytics ->
            repository.kakaoOutcome = SocialLoginOutcome.SignupRequired("signup-token")

            assertTrue(viewModel.beginKakaoSignIn())
            viewModel.handleKakaoAccessTokenResult(
                KakaoAccessTokenResult.Success("kakao-access-token"),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Kakao), analytics.startedProviders)
            assertEquals(
                listOf(LoginCompletedRecord(AnalyticsAuthProvider.Kakao, isNewUser = true)),
                analytics.completedRecords,
            )
            assertEquals(listOf("kakao-access-token"), repository.kakaoAccessTokens)
            assertTrue(repository.googleAccessTokens.isEmpty())
            val completion = assertIs<LoginCompletion.SignupRequired>(viewModel.uiState.completion)
            assertEquals("signup-token", completion.signupToken)
            assertEquals(AnalyticsAuthProvider.Kakao, completion.provider)
        }

    @Test
    fun repeatedOrDifferentProviderTapsWhileLoadingAreIgnored() =
        withViewModel { viewModel, repository, analytics ->
            assertTrue(viewModel.beginGoogleSignIn())
            assertFalse(viewModel.beginGoogleSignIn())
            assertFalse(viewModel.beginKakaoSignIn())

            viewModel.handleKakaoAccessTokenResult(
                KakaoAccessTokenResult.Success("wrong-provider-token"),
            )
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("accepted-token"),
            )
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("duplicate-token"),
            )
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("duplicate-token"),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Google), analytics.startedProviders)
            assertEquals(listOf("accepted-token"), repository.googleAccessTokens)
            assertTrue(repository.kakaoAccessTokens.isEmpty())
            assertEquals(1, analytics.completedRecords.size)
        }

    @Test
    fun sdkFailureOrCancellationDoesNotRecordLoginCompletion() =
        withViewModel { viewModel, repository, analytics ->
            assertTrue(viewModel.beginGoogleSignIn())
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Failure("Google SDK failed"),
            )

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Google SDK failed", viewModel.uiState.errorMessage)

            assertTrue(viewModel.beginKakaoSignIn())
            viewModel.handleKakaoAccessTokenResult(KakaoAccessTokenResult.Cancelled)

            assertEquals(
                listOf(AnalyticsAuthProvider.Google, AnalyticsAuthProvider.Kakao),
                analytics.startedProviders,
            )
            assertTrue(analytics.completedRecords.isEmpty())
            assertTrue(repository.googleAccessTokens.isEmpty())
            assertTrue(repository.kakaoAccessTokens.isEmpty())
            assertFalse(viewModel.uiState.isLoading)
        }

    @Test
    fun apiFailureDoesNotRecordLoginCompletion() =
        withViewModel { viewModel, repository, analytics ->
            repository.kakaoFailure = IllegalStateException("API failed")

            assertTrue(viewModel.beginKakaoSignIn())
            viewModel.handleKakaoAccessTokenResult(
                KakaoAccessTokenResult.Success("kakao-access-token"),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Kakao), analytics.startedProviders)
            assertTrue(analytics.completedRecords.isEmpty())
            assertEquals("API failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.completion)
            assertFalse(viewModel.uiState.isLoading)
        }

    @Test
    fun analyticsFailuresDoNotChangeTheLoginResult() =
        withViewModel { viewModel, _, analytics ->
            analytics.startedFailure = IllegalStateException("started analytics failed")
            analytics.completedFailure = IllegalStateException("completed analytics failed")

            assertTrue(viewModel.beginGoogleSignIn())
            assertTrue(viewModel.uiState.isLoading)
            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("google-access-token"),
            )

            assertIs<LoginCompletion.Authenticated>(viewModel.uiState.completion)
            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
        }

    @Test
    fun appleExistingMemberRecordsTheProviderLifecycle() =
        withViewModel { viewModel, repository, analytics ->
            assertTrue(viewModel.beginAppleSignIn())
            viewModel.handleAppleIdTokenResult(
                AppleIdTokenResult.Success("apple-id-token"),
            )

            assertEquals(listOf(AnalyticsAuthProvider.Apple), analytics.startedProviders)
            assertEquals(
                listOf(LoginCompletedRecord(AnalyticsAuthProvider.Apple, isNewUser = false)),
                analytics.completedRecords,
            )
            assertEquals(listOf("apple-id-token"), repository.appleIdTokens)
            assertIs<LoginCompletion.Authenticated>(viewModel.uiState.completion)
            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
        }

    @Test
    fun appleNewMemberPreservesTheProviderForSignup() =
        withViewModel { viewModel, repository, _ ->
            repository.appleOutcome = SocialLoginOutcome.SignupRequired("signup-token")

            assertTrue(viewModel.beginAppleSignIn())
            viewModel.handleAppleIdTokenResult(
                AppleIdTokenResult.Success("apple-id-token"),
            )

            val completion = assertIs<LoginCompletion.SignupRequired>(viewModel.uiState.completion)
            assertEquals("signup-token", completion.signupToken)
            assertEquals(AnalyticsAuthProvider.Apple, completion.provider)
        }

    @Test
    fun appleCancellationAndFailureDoNotCallTheRepository() =
        withViewModel { viewModel, repository, analytics ->
            assertTrue(viewModel.beginAppleSignIn())
            viewModel.handleAppleIdTokenResult(AppleIdTokenResult.Cancelled)

            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            assertTrue(repository.appleIdTokens.isEmpty())

            assertTrue(viewModel.beginAppleSignIn())
            viewModel.handleAppleIdTokenResult(AppleIdTokenResult.Failure("Apple SDK failed"))

            assertEquals("Apple SDK failed", viewModel.uiState.errorMessage)
            assertTrue(repository.appleIdTokens.isEmpty())
            assertEquals(
                listOf(AnalyticsAuthProvider.Apple, AnalyticsAuthProvider.Apple),
                analytics.startedProviders,
            )
            assertTrue(analytics.completedRecords.isEmpty())
        }

    @Test
    fun appleRepeatedStartAndMismatchedResultAreIgnored() =
        withViewModel { viewModel, repository, _ ->
            assertTrue(viewModel.beginAppleSignIn())
            assertFalse(viewModel.beginAppleSignIn())
            assertFalse(viewModel.beginGoogleSignIn())

            viewModel.handleGoogleAccessTokenResult(
                GoogleAccessTokenResult.Success("wrong-provider-token"),
            )
            viewModel.handleAppleIdTokenResult(AppleIdTokenResult.Success("apple-id-token"))
            viewModel.handleAppleIdTokenResult(AppleIdTokenResult.Success("late-token"))

            assertEquals(listOf("apple-id-token"), repository.appleIdTokens)
            assertTrue(repository.googleAccessTokens.isEmpty())
        }
}

private inline fun withViewModel(
    block: (
        LoginViewModel,
        FakeAuthRepository,
        RecordingLoginAnalyticsRecorder,
    ) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeAuthRepository()
    val analytics = RecordingLoginAnalyticsRecorder()
    val viewModel = LoginViewModel(
        authRepository = repository,
        productAnalyticsRecorder = analytics,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository, analytics)
    } finally {
        scope.cancel()
    }
}

private class FakeAuthRepository : AuthRepository {
    val appleIdTokens = mutableListOf<String>()
    val googleAccessTokens = mutableListOf<String>()
    val kakaoAccessTokens = mutableListOf<String>()
    var appleOutcome: SocialLoginOutcome = SocialLoginOutcome.Authenticated
    var googleOutcome: SocialLoginOutcome = SocialLoginOutcome.Authenticated
    var kakaoOutcome: SocialLoginOutcome = SocialLoginOutcome.Authenticated
    var appleFailure: Throwable? = null
    var googleFailure: Throwable? = null
    var kakaoFailure: Throwable? = null

    override suspend fun restoreSession() = Unit

    override suspend fun loginWithApple(idToken: String): SocialLoginOutcome {
        appleIdTokens += idToken
        appleFailure?.let { throw it }
        return appleOutcome
    }

    override suspend fun loginWithGoogle(accessToken: String): SocialLoginOutcome {
        googleAccessTokens += accessToken
        googleFailure?.let { throw it }
        return googleOutcome
    }

    override suspend fun loginWithKakao(accessToken: String): SocialLoginOutcome {
        kakaoAccessTokens += accessToken
        kakaoFailure?.let { throw it }
        return kakaoOutcome
    }

    override suspend fun signup(
        signupToken: String,
        agreedTerms: Set<AuthTerm>,
        agreedAt: String,
    ) = Unit

    override suspend fun logout() = Unit
}

private data class LoginCompletedRecord(
    val provider: AnalyticsAuthProvider,
    val isNewUser: Boolean,
)

private class RecordingLoginAnalyticsRecorder : ProductAnalyticsRecorder {
    val startedProviders = mutableListOf<AnalyticsAuthProvider>()
    val completedRecords = mutableListOf<LoginCompletedRecord>()
    var startedFailure: Throwable? = null
    var completedFailure: Throwable? = null

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) {
        startedFailure?.let { throw it }
        startedProviders += provider
    }

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) {
        completedFailure?.let { throw it }
        completedRecords += LoginCompletedRecord(provider, isNewUser)
    }

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

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = Unit
}
