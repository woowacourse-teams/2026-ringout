package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.AppScreen
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsTracker
import com.joon.ringout.analytics.DefaultProductAnalyticsRecorder
import com.joon.ringout.analytics.ProductAnalyticsUsageStore
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.presentation.login.GoogleAccessTokenResult
import com.joon.ringout.presentation.login.LoginCompletion
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.signup.SignupViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    fun `신규 회원은 로그인 결과로 약관에 진입하고 가입 완료 후 홈으로 돌아온다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        fixture.authRepository.loginOutcome = SocialLoginOutcome.SignupRequired("signup-token")
        fixture.login.beginGoogleSignIn()
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.SignupRequired>(fixture.login.uiState.completion)

        assertTrue(
            fixture.navigation.onSignupRequired(
                screen = AppScreen.Login,
                signupToken = completion.signupToken,
                provider = completion.provider,
            ),
        )
        fixture.login.consumeCompletion(completion.eventId)

        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
        fixture.signup.signup(setOf(TermId.Service, TermId.Privacy))
        runCurrent()
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        assertEquals(listOf("signup-token"), fixture.authRepository.signupTokens)
        assertTrue(fixture.navigation.onSignupCompleted(AppScreen.TermsAgreement))
        fixture.signup.consumeCompletedEvent(completedEventId)

        assertEquals(listOf(AppRoute.Home), fixture.state.backStack.toList())
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `기존 회원 로그인 완료는 남은 가입 정보를 지우고 홈으로 이동한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.signup.startSignup("previous-token", AnalyticsAuthProvider.Apple)
        fixture.state.navigate(AppRoute.Login)
        fixture.login.beginGoogleSignIn()
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        assertIs<LoginCompletion.Authenticated>(fixture.login.uiState.completion)

        assertTrue(fixture.navigation.onAuthenticated(AppScreen.Login))

        assertEquals(listOf(AppRoute.Home), fixture.state.backStack.toList())
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `약관 뒤로 가기는 가입 정보를 버리고 재진입은 새 토큰을 사용한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms("cancelled-token")

        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppScreen.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )

        assertEquals(AppScreen.Login, fixture.state.requestedScreen)
        assertFalse(fixture.signup.uiState.hasPendingSignup)
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppScreen.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )
        assertEquals(AppScreen.Login, fixture.state.requestedScreen)
        fixture.navigation.onBack(AppRoute.Login, AppScreen.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppScreen.MyPage, fixture.state.requestedScreen)

        fixture.enterTerms("new-token")
        fixture.signup.signup(setOf(TermId.Service))
        runCurrent()

        assertEquals(listOf("new-token"), fixture.authRepository.signupTokens)
    }

    @Test
    fun `로그인 SDK 응답 대기 중에는 뒤로 갈 수 없고 취소 후에는 돌아간다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        fixture.login.beginGoogleSignIn()

        assertTrue(fixture.navigation.isBackBlocked(AppScreen.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppScreen.Login, AuthSessionState.Unauthenticated)

        assertEquals(AppScreen.Login, fixture.state.requestedScreen)
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Cancelled)
        assertFalse(fixture.navigation.isBackBlocked(AppScreen.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppScreen.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppScreen.MyPage, fixture.state.requestedScreen)
    }

    @Test
    fun `로그인 응답이 끝나도 완료 이벤트 처리 전에는 뒤로 갈 수 없다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        fixture.login.beginGoogleSignIn()
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        assertFalse(fixture.login.uiState.isLoading)
        val completion = assertNotNull(fixture.login.uiState.completion)

        assertTrue(fixture.navigation.isBackBlocked(AppScreen.Login, AuthSessionState.Authenticated))
        fixture.navigation.onBack(AppRoute.Login, AppScreen.Login, AuthSessionState.Authenticated)

        assertEquals(AppScreen.Login, fixture.state.requestedScreen)
        assertEquals(completion, fixture.login.uiState.completion)
    }

    @Test
    fun `회원가입 요청과 완료 이벤트 처리 중에는 약관 뒤로 가기를 막는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()
        val signupGate = CompletableDeferred<Unit>()
        fixture.authRepository.signupGate = signupGate
        fixture.signup.signup(setOf(TermId.Service))
        runCurrent()
        assertTrue(fixture.signup.uiState.isSaving)

        assertTrue(
            fixture.navigation.isBackBlocked(AppScreen.TermsAgreement, AuthSessionState.Unauthenticated),
        )
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppScreen.TermsAgreement,
            AuthSessionState.Unauthenticated,
        )
        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertTrue(fixture.signup.uiState.hasPendingSignup)

        signupGate.complete(Unit)
        runCurrent()
        assertFalse(fixture.signup.uiState.isSaving)
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        assertTrue(
            fixture.navigation.isBackBlocked(AppScreen.TermsAgreement, AuthSessionState.Authenticated),
        )
        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppScreen.TermsAgreement,
            AuthSessionState.Authenticated,
        )
        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertEquals(completedEventId, fixture.signup.uiState.completedEventId)
    }

    @Test
    fun `재인증으로 강제 표시하는 로그인에서는 이전 화면으로 돌아가지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.NicknameChange)
        val previousStack = fixture.state.backStack.toList()

        assertFalse(fixture.navigation.isActive(AppRoute.Login, AppScreen.Login))
        assertTrue(
            fixture.navigation.isBackBlocked(AppScreen.Login, AuthSessionState.ReauthenticationRequired),
        )
        fixture.navigation.onBack(
            AppRoute.Login,
            AppScreen.Login,
            AuthSessionState.ReauthenticationRequired,
        )
        assertEquals(previousStack, fixture.state.backStack.toList())

        fixture.state.navigate(AppRoute.Login)
        assertTrue(fixture.navigation.isActive(AppRoute.Login, AppScreen.Login))
        fixture.navigation.onBack(
            AppRoute.Login,
            AppScreen.Login,
            AuthSessionState.ReauthenticationRequired,
        )
        assertEquals(AppScreen.Login, fixture.state.requestedScreen)

        assertFalse(fixture.navigation.isBackBlocked(AppScreen.Login, AuthSessionState.Unauthenticated))
        fixture.navigation.onBack(AppRoute.Login, AppScreen.Login, AuthSessionState.Unauthenticated)
        assertEquals(AppScreen.MyPage, fixture.state.requestedScreen)
    }

    @Test
    fun `알람에 가려진 로그인 완료는 보관하고 화면 복귀 후 처리한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        fixture.login.beginGoogleSignIn()
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.Authenticated>(fixture.login.uiState.completion)

        assertFalse(fixture.navigation.isActive(AppRoute.Login, AppScreen.AlarmRinging))
        assertFalse(fixture.navigation.onAuthenticated(AppScreen.AlarmRinging))
        assertEquals(completion, fixture.login.uiState.completion)
        assertEquals(AppScreen.Login, fixture.state.requestedScreen)

        assertTrue(fixture.navigation.onAuthenticated(AppScreen.Login))
        assertEquals(AppScreen.Home, fixture.state.requestedScreen)
        assertFalse(fixture.navigation.onAuthenticated(AppScreen.Login))
    }

    @Test
    fun `알람 중 신규 회원 결과는 가입 상태를 바꾸지 않고 로그인 이벤트에 남긴다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.Login)
        fixture.authRepository.loginOutcome = SocialLoginOutcome.SignupRequired("deferred-token")
        fixture.login.beginGoogleSignIn()
        fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
        runCurrent()
        val completion = assertIs<LoginCompletion.SignupRequired>(fixture.login.uiState.completion)

        assertFalse(
            fixture.navigation.onSignupRequired(
                AppScreen.AlarmRinging,
                completion.signupToken,
                completion.provider,
            ),
        )
        assertFalse(fixture.signup.uiState.hasPendingSignup)
        assertEquals(completion, fixture.login.uiState.completion)

        assertTrue(
            fixture.navigation.onSignupRequired(
                AppScreen.Login,
                completion.signupToken,
                completion.provider,
            ),
        )
        assertFalse(
            fixture.navigation.onSignupRequired(AppScreen.Login, "stale-token", completion.provider),
        )
        fixture.signup.signup(setOf(TermId.Service))
        runCurrent()

        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertEquals(listOf("deferred-token"), fixture.authRepository.signupTokens)
    }

    @Test
    fun `알람에 가린 가입 완료는 유지하고 완료 후 늦은 약관 콜백은 무시한다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()
        fixture.signup.signup(setOf(TermId.Service))
        runCurrent()
        val completedEventId = assertNotNull(fixture.signup.uiState.completedEventId)

        assertFalse(fixture.navigation.onSignupCompleted(AppScreen.AlarmRinging))
        assertEquals(completedEventId, fixture.signup.uiState.completedEventId)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)

        assertTrue(fixture.navigation.onSignupCompleted(AppScreen.TermsAgreement))
        fixture.signup.consumeCompletedEvent(completedEventId)
        fixture.navigation.onMissingSignup(AppScreen.TermsAgreement)

        assertEquals(AppScreen.Home, fixture.state.requestedScreen)
        assertFalse(fixture.navigation.onSignupCompleted(AppScreen.TermsAgreement))
        assertFalse(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `약관 경로만 복원되고 가입 정보가 없으면 로그인으로 돌아간다`() = runTest {
        val backStack = NavBackStack<AppRoute>(
            AppRoute.Home,
            AppRoute.MyPage,
            AppRoute.Login,
            AppRoute.TermsAgreement,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, backStack))
        val fixture = AuthNavigationFixture(backgroundScope, AppNavigationState(restored))

        fixture.navigation.onMissingSignup(AppScreen.AlarmRinging)
        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        fixture.navigation.onMissingSignup(AppScreen.TermsAgreement)

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login),
            fixture.state.backStack.toList(),
        )
        assertNull(fixture.signup.uiState.completedEventId)
        assertTrue(fixture.authRepository.signupTokens.isEmpty())
    }

    @Test
    fun `가입 정보가 존재하면 오래된 정보 누락 콜백으로 약관에서 벗어나지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()

        fixture.navigation.onMissingSignup(AppScreen.TermsAgreement)

        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
    }

    @Test
    fun `알람에 가려진 약관의 뒤로 가기는 가입 정보를 지우지 않는다`() = runTest {
        val fixture = AuthNavigationFixture(backgroundScope)
        fixture.enterTerms()

        fixture.navigation.onBack(
            AppRoute.TermsAgreement,
            AppScreen.AlarmRinging,
            AuthSessionState.Unauthenticated,
        )

        assertEquals(AppScreen.TermsAgreement, fixture.state.requestedScreen)
        assertTrue(fixture.signup.uiState.hasPendingSignup)
    }
}

private class AuthNavigationFixture(
    scope: CoroutineScope,
    val state: AppNavigationState = AppNavigationState(),
) {
    val authRepository = NavigationAuthRepository()
    private val analytics = DefaultProductAnalyticsRecorder(
        tracker = AnalyticsTracker {},
        usageStore = ProductAnalyticsUsageStore { null },
    )
    val login = LoginViewModel(
        authRepository = authRepository,
        productAnalyticsRecorder = analytics,
        coroutineScope = scope,
    )
    val signup = SignupViewModel(
        authRepository = authRepository,
        destinationRepository = NavigationDestinationRepository(),
        productAnalyticsRecorder = analytics,
        currentDate = { "2026-08-27" },
        coroutineScope = scope,
    )
    val navigation = AuthNavigation(state, login, signup)

    fun enterTerms(signupToken: String = "signup-token") {
        state.navigate(AppRoute.Login)
        check(navigation.onSignupRequired(AppScreen.Login, signupToken, AnalyticsAuthProvider.Google))
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

private class NavigationDestinationRepository : DestinationRepository {
    override fun observeAll(): Flow<List<SavedDestination>> = flowOf(emptyList())

    override suspend fun fetchAll(): List<SavedDestination> = emptyList()

    override suspend fun sync(): List<SavedDestination> = emptyList()

    override suspend fun save(destination: SavedDestination): SavedDestination = destination

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}
