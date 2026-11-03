package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsTracker
import com.joon.ringout.analytics.DefaultProductAnalyticsRecorder
import com.joon.ringout.analytics.InMemoryProductAnalyticsUsageStore
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.presentation.appbootstrap.AuthFlowEvent
import com.joon.ringout.presentation.appbootstrap.AuthFlowEventResult
import com.joon.ringout.presentation.appbootstrap.AuthFlowEventType
import com.joon.ringout.presentation.login.GoogleAccessTokenResult
import com.joon.ringout.presentation.login.LoginCompletion
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.signup.SignupViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthNavigationTest {
    @Test
    fun `인증 전용 스택은 서비스 화면으로 이동하지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)

        listOf(AppRoute.Home, AppRoute.Social, AppRoute.MyPage, AppRoute.RoomDetail("room-42"))
            .forEach(fixture.state::navigate)

        assertEquals(listOf(AppRoute.Login), fixture.state.backStack.toList())
    }

    @Test
    fun `로그인 시작은 루트에 인증 진행을 알린다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)

        fixture.navigation.onLoginStarted()

        assertEquals(1, fixture.loginStartedCount)
    }

    @Test
    fun `기존 회원 로그인 완료는 가입 정보를 지우고 루트에 완료를 알린다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.signup.startSignup("previous-token", AnalyticsAuthProvider.Apple)
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.login.beginGoogleSignIn(attemptId)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.Authenticated>(fixture.login.uiState.completion)

        assertTrue(
            fixture.navigation.onAuthenticated(
                AppRoute.Login,
                completion.attemptId,
                completion.eventId,
            ),
        )

        assertEquals(1, fixture.completedCount)
        assertEquals(listOf(AppRoute.Login), fixture.state.backStack.toList())
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `신규 회원은 약관에서 가입을 완료하고 루트에 완료를 알린다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.authRepository.loginOutcome = SocialLoginOutcome.SignupRequired("signup-token")
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.login.beginGoogleSignIn(attemptId)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.SignupRequired>(fixture.login.uiState.completion)

        assertTrue(
            fixture.navigation.onSignupRequired(
                AppRoute.Login,
                completion.attemptId,
                completion.eventId,
                completion.signupToken,
                completion.provider,
            ),
        )
        fixture.login.consumeCompletion(completion.eventId)
        assertEquals(1, fixture.loginStartedCount)
        assertEquals(listOf(AppRoute.Login, AppRoute.TermsAgreement), fixture.state.backStack.toList())
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        assertTrue(
            fixture.navigation.onSignupCompleted(
                AppRoute.TermsAgreement,
                assertNotNull(fixture.signup.uiState.authFlowAttemptId),
                completedEventId,
            ),
        )
        fixture.signup.consumeCompletedEvent(completedEventId)

        assertEquals(listOf("signup-token"), fixture.authRepository.signupTokens)
        assertEquals(1, fixture.completedCount)
        assertEquals(listOf(AppRoute.Login, AppRoute.TermsAgreement), fixture.state.backStack.toList())
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `약관 뒤로 가기는 가입 정보를 버리고 재진입은 새 토큰을 사용한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms("cancelled-token")

        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppRoute.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )

        assertEquals(listOf(AppRoute.Login), fixture.state.backStack.toList())
        assertEquals(1, fixture.cancelledCount)
        assertFalse(fixture.signup.uiState.hasPendingSignup)
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppRoute.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
        fixture.navigation.onBack(AppRoute.Login, AppRoute.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)

        fixture.enterTerms("new-token")
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()

        assertEquals(listOf("new-token"), fixture.authRepository.signupTokens)
    }

    @Test
    fun `로그인 SDK 응답을 기다리거나 취소한 뒤에도 로그인 뒤로 가기를 막는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.login.beginGoogleSignIn(attemptId)

        assertTrue(fixture.navigation.isBackBlocked(AppRoute.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppRoute.Login, AuthSessionState.Unauthenticated)

        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Cancelled)
        assertTrue(fixture.navigation.isBackBlocked(AppRoute.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppRoute.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
    }

    @Test
    fun `로그인 응답이 끝나도 완료 이벤트 처리 전에는 뒤로 갈 수 없다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.login.beginGoogleSignIn(attemptId)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        assertFalse(fixture.login.uiState.isLoading)
        val completion = assertNotNull(fixture.login.uiState.completion)

        assertTrue(fixture.navigation.isBackBlocked(AppRoute.Login, AuthSessionState.Authenticated))
        fixture.navigation.onBack(AppRoute.Login, AppRoute.Login, AuthSessionState.Authenticated)

        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
        assertEquals(completion, fixture.login.uiState.completion)
    }

    @Test
    fun `회원가입 요청과 완료 이벤트 처리 중에는 약관 뒤로 가기를 막는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()
        val signupGate = CompletableDeferred<Unit>()
        fixture.authRepository.signupGate = signupGate
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()
        assertTrue(fixture.signup.uiState.isSaving)

        assertTrue(
            fixture.navigation.isBackBlocked(AppRoute.TermsAgreement, AuthSessionState.Unauthenticated),
        )
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppRoute.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )
        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertTrue(fixture.signup.uiState.hasPendingSignup)

        signupGate.complete(Unit)
        runCurrent()
        assertFalse(fixture.signup.uiState.isSaving)
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        assertTrue(
            fixture.navigation.isBackBlocked(AppRoute.TermsAgreement, AuthSessionState.Authenticated),
        )
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppRoute.TermsAgreement,
            AuthSessionState.Authenticated,
        )
        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertEquals(completedEventId, fixture.signup.uiState.completedEventId)
    }

    @Test
    fun `재인증으로 강제 표시하는 로그인에서는 이전 화면으로 돌아가지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        val previousStack = fixture.state.backStack.toList()

        assertTrue(fixture.navigation.isActive(AppRoute.Login, AppRoute.Login))
        assertTrue(
            fixture.navigation.isBackBlocked(AppRoute.Login, AuthSessionState.ReauthenticationRequired),
        )
        fixture.navigation.onBack(
            AppRoute.Login,
            AppRoute.Login,
            AuthSessionState.ReauthenticationRequired,
        )
        assertEquals(previousStack, fixture.state.backStack.toList())

        fixture.state.navigate(AppRoute.Login)
        assertTrue(fixture.navigation.isActive(AppRoute.Login, AppRoute.Login))
        fixture.navigation.onBack(
            AppRoute.Login,
            AppRoute.Login,
            AuthSessionState.ReauthenticationRequired,
        )
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)

        assertTrue(fixture.navigation.isBackBlocked(AppRoute.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppRoute.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
    }

    @Test
    fun `알람에 가려진 로그인 완료는 보관하고 화면 복귀 후 처리한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.login.beginGoogleSignIn(attemptId)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.Authenticated>(fixture.login.uiState.completion)

        assertFalse(fixture.navigation.isActive(AppRoute.Login, RingingRoute))
        assertFalse(
            fixture.navigation.onAuthenticated(
                RingingRoute,
                completion.attemptId,
                completion.eventId,
            ),
        )
        assertEquals(0, fixture.completedCount)
        assertEquals(completion, fixture.login.uiState.completion)
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)

        assertTrue(
            fixture.navigation.onAuthenticated(
                AppRoute.Login,
                completion.attemptId,
                completion.eventId,
            ),
        )
        assertEquals(AppRoute.Login, fixture.state.requestedRoute)
        assertEquals(1, fixture.completedCount)
    }

    @Test
    fun `알람 중 신규 회원 결과는 가입 상태를 바꾸지 않고 로그인 이벤트에 남긴다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        val attemptId = fixture.navigation.onLoginStarted()
        fixture.authRepository.loginOutcome = SocialLoginOutcome.SignupRequired("deferred-token")
        fixture.login.beginGoogleSignIn(attemptId)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.SignupRequired>(fixture.login.uiState.completion)

        assertFalse(
            fixture.navigation.onSignupRequired(
                RingingRoute,
                completion.attemptId,
                completion.eventId,
                completion.signupToken,
                completion.provider,
            ),
        )
        assertFalse(fixture.signup.uiState.hasPendingSignup)
        assertEquals(completion, fixture.login.uiState.completion)

        assertTrue(
            fixture.navigation.onSignupRequired(
                AppRoute.Login,
                completion.attemptId,
                completion.eventId,
                completion.signupToken,
                completion.provider,
            ),
        )
        assertFalse(
            fixture.navigation.onSignupRequired(
                AppRoute.Login,
                completion.attemptId,
                completion.eventId + 1,
                "stale-token",
                completion.provider,
            ),
        )
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()

        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertEquals(listOf("deferred-token"), fixture.authRepository.signupTokens)
    }

    @Test
    fun `알람에 가린 가입 완료는 유지하고 화면 복귀 후 루트에 완료를 알린다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        val attemptId = assertNotNull(fixture.signup.uiState.authFlowAttemptId)
        assertFalse(fixture.navigation.onSignupCompleted(RingingRoute, attemptId, completedEventId))
        assertEquals(0, fixture.completedCount)
        assertEquals(completedEventId, fixture.signup.uiState.completedEventId)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)

        assertTrue(
            fixture.navigation.onSignupCompleted(
                AppRoute.TermsAgreement,
                attemptId,
                completedEventId,
            ),
        )
        fixture.signup.consumeCompletedEvent(completedEventId)

        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertEquals(1, fixture.completedCount)
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `약관 경로만 복원되고 가입 정보가 없으면 로그인으로 돌아간다`() = runTest {
        val backStack = NavBackStack<AppRoute>(
            AppRoute.Login,
            AppRoute.TermsAgreement,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, backStack))
        val fixture = AuthNavigationFixture(
            backgroundScope,
            AppNavigationState(restored, authenticationOnlyMode = true),
        )

        fixture.navigation.onMissingSignup(RingingRoute)
        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        fixture.navigation.onMissingSignup(AppRoute.TermsAgreement)

        assertEquals(
            listOf(AppRoute.Login),
            fixture.state.backStack.toList(),
        )
        assertEquals(0, fixture.cancelledCount)
        assertNull(fixture.signup.uiState.completedEventId)
        assertTrue(fixture.authRepository.signupTokens.isEmpty())
    }

    @Test
    fun `가입 정보가 존재하면 오래된 정보 누락 콜백으로 약관에서 벗어나지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()

        fixture.navigation.onMissingSignup(AppRoute.TermsAgreement)

        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `알람에 가려진 약관의 뒤로 가기는 가입 정보를 지우지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()

        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            RingingRoute,
            AuthSessionState.Unauthenticated,
        )

        assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
    }
}

private val RingingRoute = AppRoute.AlarmRinging("alarm-1")

private class AuthNavigationFixture(
    scope: CoroutineScope,
    val state: AppNavigationState = AppNavigationState(
        routes = NavBackStack(AppRoute.Login),
        authenticationOnlyMode = true,
    ),
) {
    val authRepository = NavigationAuthRepository()
    private val analytics = DefaultProductAnalyticsRecorder(
        tracker = AnalyticsTracker {},
        usageStore = InMemoryProductAnalyticsUsageStore(),
    )
    val login = LoginViewModel(
        authRepository = authRepository,
        productAnalyticsRecorder = analytics,
        coroutineScope = scope,
    )
    val signup = SignupViewModel(
        authRepository = authRepository,
        productAnalyticsRecorder = analytics,
        currentDate = { "2026-08-27" },
        coroutineScope = scope,
    )
    var loginStartedCount = 0
    var completedCount = 0
    var cancelledCount = 0
    val navigation = AuthNavigation(
        state,
        login,
        signup,
        onLoginStarted = {
            loginStartedCount += 1
            loginStartedCount.toLong()
        },
        onAuthFlowEvent = { event ->
            if (event.type != AuthFlowEventType.SignupRequired) completedCount++
            AuthFlowEventResult.Accepted
        },
        onAuthFlowCancelled = { cancelledCount++ },
    )

    fun enterTerms(signupToken: String = "signup-token") {
        state.navigate(AppRoute.Login)
        val attemptId = navigation.onLoginStarted()
        check(
            navigation.onSignupRequired(
                AppRoute.Login,
                attemptId,
                eventId = 1,
                signupToken = signupToken,
                provider = AnalyticsAuthProvider.Google,
            ),
        )
    }
}

private class NavigationAuthRepository : AuthRepository {
    var loginOutcome: SocialLoginOutcome = SocialLoginOutcome.Authenticated
    var signupGate: CompletableDeferred<Unit>? = null
    val signupTokens = mutableListOf<String>()

    override suspend fun restoreSession() = Unit

    override suspend fun loginWithApple(idToken: String): SocialLoginOutcome = loginOutcome

    override suspend fun loginWithGoogle(accessToken: String): SocialLoginOutcome = loginOutcome

    override suspend fun loginWithKakao(accessToken: String): SocialLoginOutcome = loginOutcome

    override suspend fun signup(
        signupToken: String,
        agreedTerms: Set<AuthTerm>,
        agreedAt: String,
    ) {
        signupTokens += signupToken
        signupGate?.await()
    }

    override suspend fun logout() = Unit
}
