package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationAuthorizationState
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
import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.toDestinationSelection
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.GoogleAccessTokenResult
import com.joon.ringout.presentation.login.LoginCompletion
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.signup.SignupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReauthenticationNavigationEffectTest {
    @Test
    fun `재인증은 연결된 진행 상태와 오류를 정리한 뒤 로그인으로 이동한다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.AddAlarm)
        fixture.state.navigate(AppRoute.Destination(1L))
        fixture.prepareProgressAndErrors()
        runCurrent()
        val draft = fixture.alarmSetup.uiState
        val account = fixture.myPage.uiState.accountStatus
        val destinations = fixture.destination.uiState.destinations
        assertNotNull(fixture.alarmSetup.permissionDialog)
        assertIs<MyPageAccountActionState.Error>(fixture.myPage.uiState.accountAction)
        assertNotNull(fixture.destination.uiState.errorMessage)

        var sessionState by mutableStateOf(AuthSessionState.Authenticated)
        withEffectComposition(content = { fixture.Content(sessionState) }) { drain ->
            sessionState = AuthSessionState.ReauthenticationRequired
            drain()

            assertEquals(LoginStack, fixture.state.backStack.toList())
            assertFalse(fixture.signup.uiState.hasPendingSignup)
            assertEquals(
                draft.copy(pendingSaveRequest = null, isScheduling = false, errorMessage = null),
                fixture.alarmSetup.uiState,
            )
            assertTrue(fixture.alarmSetup.hasDraft)
            assertNull(fixture.alarmSetup.permissionDialog)
            assertNull(fixture.home.uiState.errorMessage)
            assertEquals(MyPageAccountActionState.Idle, fixture.myPage.uiState.accountAction)
            assertEquals(account, fixture.myPage.uiState.accountStatus)
            assertNull(fixture.destination.uiState.errorMessage)
            assertEquals(destinations, fixture.destination.uiState.destinations)
            assertEquals(1, fixture.destinationRepository.fetchCount)
        }
    }

    @Test
    fun `재인증이 아닌 세션 상태는 진행 중인 화면과 상태를 정리하지 않는다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.TermsAgreement)
        fixture.prepareProgressAndErrors()
        runCurrent()
        val expectedStack = fixture.state.backStack.toList()
        val expectedUiStates = fixture.uiStateSnapshot()

        var sessionState by mutableStateOf(AuthSessionState.Restoring)
        withEffectComposition(content = { fixture.Content(sessionState) }) { drain ->
            for (
                nextState in listOf(
                    AuthSessionState.Restoring,
                    AuthSessionState.Unauthenticated,
                    AuthSessionState.Authenticated,
                )
            ) {
                sessionState = nextState
                drain()

                assertEquals(expectedStack, fixture.state.backStack.toList())
                assertEquals(expectedUiStates, fixture.uiStateSnapshot())
            }
        }
    }

    @Test
    fun `선택적 스코프 없이 진입한 뒤 연결하거나 교체한 가입 스코프도 정리한다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        fixture.state.navigate(AppRoute.NicknameChange)
        var boundSignup by mutableStateOf<SignupViewModel?>(null)
        withEffectComposition(
            content = {
                ReauthenticationNavigationEffect(
                    authSessionState = AuthSessionState.ReauthenticationRequired,
                    navigationState = fixture.state,
                    homeViewModel = fixture.home,
                    signupViewModel = boundSignup,
                    alarmSetupViewModel = null,
                    myPageViewModel = null,
                    destinationViewModel = null,
                )
            },
        ) { drain ->
            assertEquals(LoginStack, fixture.state.backStack.toList())
            for (viewModel in listOf(fixture.signup, fixture.createSignupViewModel())) {
                viewModel.startSignup("previous-token", AnalyticsAuthProvider.Google)
                boundSignup = viewModel
                drain()

                assertFalse(viewModel.uiState.hasPendingSignup)
                assertEquals(LoginStack, fixture.state.backStack.toList())
            }
        }
    }

    @Test
    fun `늦은 오류와 미션 요청은 각각 재인증 처리를 다시 실행하고 로그인은 중복하지 않는다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        withEffectComposition(
            content = { fixture.Content(AuthSessionState.ReauthenticationRequired) },
        ) { drain ->
            fixture.home.showError("늦게 도착한 홈 오류")
            drain()
            assertNull(fixture.home.uiState.errorMessage)

            fixture.state.navigate(AppRoute.ActiveAlarmTracking("occurrence-1"))
            drain()
            assertEquals(AppRoute.Login, fixture.state.requestedRoute)
            assertTrue(fixture.state.isCurrentRoute(AppRoute.Login))
            assertEquals(LoginStack, fixture.state.backStack.toList())
            drain()
            assertEquals(LoginStack, fixture.state.backStack.toList())
        }
    }

    @Test
    fun `인증 성공 후 홈을 유지하고 다음 재인증은 다시 처리한다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        var sessionState by mutableStateOf(AuthSessionState.ReauthenticationRequired)
        withEffectComposition(content = { fixture.Content(sessionState) }) { drain ->
            sessionState = AuthSessionState.Authenticated
            fixture.state.navigate(AppRoute.Home)
            fixture.home.showError("로그인 이후의 오류")
            drain()

            assertEquals(AppRoute.Home, fixture.state.requestedRoute)
            assertEquals("로그인 이후의 오류", fixture.home.uiState.errorMessage)
            sessionState = AuthSessionState.ReauthenticationRequired
            drain()

            assertEquals(LoginStack, fixture.state.backStack.toList())
            assertNull(fixture.home.uiState.errorMessage)
        }
    }

    @Test
    fun `재인증에서 비인증 상태로 바뀌면 신규 회원의 약관 진행을 허용한다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        var sessionState by mutableStateOf(AuthSessionState.ReauthenticationRequired)
        withEffectComposition(content = { fixture.Content(sessionState) }) { drain ->
            sessionState = AuthSessionState.Unauthenticated
            drain()
            assertTrue(
                fixture.authNavigation.onSignupRequired(
                    AppRoute.Login,
                    "new-token",
                    AnalyticsAuthProvider.Google,
                ),
            )
            drain()

            assertEquals(AppRoute.TermsAgreement, fixture.state.requestedRoute)
            assertTrue(fixture.signup.uiState.hasPendingSignup)
            fixture.signup.signup(setOf(TermId.Service))
            drain()
            assertEquals(listOf("new-token"), fixture.authRepository.signupTokens)
            assertNotNull(fixture.signup.uiState.completedEventId)
        }
    }

    @Test
    fun `재인증 처리를 다시 실행해도 새 로그인 요청은 취소하지 않는다`() = runTest {
        val fixture = ReauthenticationFixture(backgroundScope)
        var sessionState by mutableStateOf(AuthSessionState.ReauthenticationRequired)
        withEffectComposition(content = { fixture.Content(sessionState) }) { drain ->
            fixture.login.beginGoogleSignIn()
            fixture.home.showError("로그인 요청 중 도착한 오류")
            drain()

            assertNull(fixture.home.uiState.errorMessage)
            assertTrue(fixture.login.uiState.isLoading)
            fixture.login.handleGoogleAccessTokenResult(GoogleAccessTokenResult.Success("access-token"))
            drain()
            assertIs<LoginCompletion.Authenticated>(fixture.login.uiState.completion)
            sessionState = AuthSessionState.Authenticated
            assertTrue(fixture.authNavigation.onAuthenticated(AppRoute.Login))
            drain()

            assertEquals(AppRoute.Home, fixture.state.requestedRoute)
        }
    }
}

private class ReauthenticationFixture(
    private val scope: CoroutineScope,
    val state: AppNavigationState = AppNavigationState(),
    val home: HomeViewModel = HomeViewModel(),
) {
    private val analytics = DefaultProductAnalyticsRecorder(
        tracker = AnalyticsTracker {},
        usageStore = ProductAnalyticsUsageStore { null },
    )
    val destinationRepository = ReauthenticationDestinationRepository()
    val authRepository = ReauthenticationAuthRepository()
    val signup = createSignupViewModel()
    val login = LoginViewModel(authRepository, analytics, coroutineScope = scope)
    val authNavigation = AuthNavigation(state, login, signup)
    val alarmSetup = AlarmSetupViewModel(createAlarmId = { "alarm-1" })
    val myPage = MyPageViewModel(
        getMissionSuccessDates = GetMissionSuccessDates(EmptyMissionHistoryRepository),
        memberRepository = ReauthenticationMemberRepository,
        authRepository = authRepository,
        productAnalyticsRecorder = analytics,
        initialMonth = MissionYearMonth(2026, 8),
        coroutineScope = scope,
    )
    val destination = DestinationViewModel(destinationRepository, analytics, scope)

    fun createSignupViewModel(): SignupViewModel = SignupViewModel(
        authRepository = authRepository,
        destinationRepository = destinationRepository,
        productAnalyticsRecorder = analytics,
        currentDate = { "2026-08-28" },
        coroutineScope = scope,
    )

    @Composable
    fun Content(authSessionState: AuthSessionState) {
        ReauthenticationNavigationEffect(
            authSessionState = authSessionState,
            navigationState = state,
            homeViewModel = home,
            signupViewModel = signup,
            alarmSetupViewModel = alarmSetup,
            myPageViewModel = myPage,
            destinationViewModel = destination,
        )
    }

    fun prepareProgressAndErrors() {
        signup.startSignup("previous-token", AnalyticsAuthProvider.Google)
        alarmSetup.startCreating("07:00")
        alarmSetup.updateDestination(SavedPlace.toDestinationSelection())
        check(alarmSetup.requestSave())
        alarmSetup.onLocationStateChanged(
            locationState = DefaultMissionLocationState.copy(
                authorization = MissionLocationAuthorizationState.NOT_DETERMINED,
            ),
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )
        alarmSetup.showError("알람 저장 오류")
        home.showError("홈 오류")
        myPage.onAuthenticated()
        myPage.logout()
        destination.onScreenEntered()
    }

    fun uiStateSnapshot(): List<Any?> = listOf(
        home.uiState,
        signup.uiState,
        alarmSetup.uiState,
        alarmSetup.permissionDialog,
        myPage.uiState,
        destination.uiState,
    )
}

private class ReauthenticationAuthRepository : AuthRepository {
    val signupTokens = mutableListOf<String>()

    override suspend fun restoreSession() = Unit

    override suspend fun loginWithApple(idToken: String): SocialLoginOutcome = SocialLoginOutcome.Authenticated

    override suspend fun loginWithGoogle(accessToken: String): SocialLoginOutcome = SocialLoginOutcome.Authenticated

    override suspend fun loginWithKakao(accessToken: String): SocialLoginOutcome = SocialLoginOutcome.Authenticated

    override suspend fun signup(signupToken: String, agreedTerms: Set<AuthTerm>, agreedAt: String) {
        signupTokens += signupToken
    }

    override suspend fun logout(): Unit = error("계정 처리 오류")
}

private class ReauthenticationDestinationRepository : DestinationRepository {
    var fetchCount = 0
        private set

    override fun observeAll(): Flow<List<SavedDestination>> = flowOf(listOf(SavedPlace))

    override suspend fun fetchAll(): List<SavedDestination> {
        fetchCount++
        error("목적지 조회 오류")
    }

    override suspend fun sync(): List<SavedDestination> = listOf(SavedPlace)

    override suspend fun save(destination: SavedDestination): SavedDestination = destination

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private object ReauthenticationMemberRepository : MemberRepository {
    override suspend fun getProfile(): MemberProfile = MemberProfile("기존 닉네임", null)

    override suspend fun updateNickname(nickname: String): String = nickname

    override suspend fun withdraw() = Unit
}

private object EmptyMissionHistoryRepository : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> = emptyList()

    override suspend fun record(entry: MissionHistoryEntry): Boolean = false
}

private val LoginStack = listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login)

private val SavedPlace = SavedDestination(
    id = 1L,
    name = "회사",
    address = "서울특별시 강남구 테헤란로 1",
    latitude = 37.4979,
    longitude = 127.0276,
)
