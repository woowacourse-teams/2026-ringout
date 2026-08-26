package com.joon.ringout.presentation.mypage

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageViewModelTest {
    @Test
    fun `화면에 진입할 때마다 달력을 다시 불러오고 한 번만 기록한다`() =
        withViewModel { viewModel, repository, analytics ->
            val firstEntry = MyPageEntryToken()
            viewModel.onScreenEntered(firstEntry, AnalyticsLoginState.LoggedOut)

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(emptySet(), viewModel.uiState.successDates)
            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedOut)),
                analytics.calendarViewed,
            )

            viewModel.onScreenEntered(firstEntry, AnalyticsLoginState.LoggedIn)

            assertEquals(listOf(August2026), repository.queries)
            assertEquals(1, analytics.calendarViewed.size)

            val successDate = MissionDate.parse("2026-08-05")
            repository.entries += MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = successDate,
                occurrenceId = "occurrence-success",
            )
            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

            assertEquals(setOf(successDate), viewModel.uiState.successDates)
            assertEquals(listOf(August2026, August2026), repository.queries)
            assertEquals(
                listOf(
                    CalendarViewed(August2026, AnalyticsLoginState.LoggedOut),
                    CalendarViewed(August2026, AnalyticsLoginState.LoggedIn),
                ),
                analytics.calendarViewed,
            )
        }

    @Test
    fun `첫 조회가 실패하면 재시도 성공 후 달력 조회를 한 번 기록한다`() =
        withViewModel { viewModel, repository, analytics ->
            repository.failuresRemaining = 1

            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

            assertNotNull(viewModel.uiState.errorMessage)
            assertEquals(emptyList(), analytics.calendarViewed)

            viewModel.retryCalendar()

            assertNull(viewModel.uiState.errorMessage)
            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedIn)),
                analytics.calendarViewed,
            )

            viewModel.retryCalendar()

            assertEquals(1, analytics.calendarViewed.size)
            assertEquals(listOf(August2026, August2026, August2026), repository.queries)
        }

    @Test
    fun `월 이동을 모두 기록하고 달력 조회를 중복 기록하지 않는다`() = withViewModel(
        entries = mutableListOf(
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-07-03"),
                occurrenceId = "july-success",
            ),
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-08-05"),
                occurrenceId = "august-success",
            ),
        ),
    ) { viewModel, repository, analytics ->
        viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedOut)
        viewModel.onPreviousMonthClick(AnalyticsLoginState.LoggedOut)

        assertEquals(July2026, viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-07-03")),
            viewModel.uiState.successDates,
        )

        viewModel.onNextMonthClick(AnalyticsLoginState.LoggedOut)

        assertEquals(August2026, viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-08-05")),
            viewModel.uiState.successDates,
        )
        assertEquals(
            listOf(
                MonthChanged(
                    direction = StampMonthChangeDirection.Previous,
                    month = July2026,
                    loginState = AnalyticsLoginState.LoggedOut,
                ),
                MonthChanged(
                    direction = StampMonthChangeDirection.Next,
                    month = August2026,
                    loginState = AnalyticsLoginState.LoggedOut,
                ),
            ),
            analytics.monthChanged,
        )
        assertEquals(1, analytics.calendarViewed.size)
        assertEquals(listOf(August2026, July2026, August2026), repository.queries)
    }

    @Test
    fun `12월과 1월 경계에서 월 이동을 올바르게 기록한다`() = withViewModel(
        initialMonth = December2025,
    ) { viewModel, _, analytics ->
        viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

        viewModel.onNextMonthClick(AnalyticsLoginState.LoggedIn)
        viewModel.onPreviousMonthClick(AnalyticsLoginState.LoggedIn)

        assertEquals(
            listOf(
                MonthChanged(
                    direction = StampMonthChangeDirection.Next,
                    month = January2026,
                    loginState = AnalyticsLoginState.LoggedIn,
                ),
                MonthChanged(
                    direction = StampMonthChangeDirection.Previous,
                    month = December2025,
                    loginState = AnalyticsLoginState.LoggedIn,
                ),
            ),
            analytics.monthChanged,
        )
    }

    @Test
    fun `저장소 요청이 실패해도 월 이동을 기록한다`() =
        withViewModel { viewModel, repository, analytics ->
            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)
            repository.failuresRemaining = 1

            viewModel.onNextMonthClick(AnalyticsLoginState.LoggedIn)

            assertEquals(
                listOf(
                    MonthChanged(
                        direction = StampMonthChangeDirection.Next,
                        month = September2026,
                        loginState = AnalyticsLoginState.LoggedIn,
                    ),
                ),
                analytics.monthChanged,
            )
            assertNotNull(viewModel.uiState.errorMessage)
            assertEquals(1, analytics.calendarViewed.size)
        }

    @Test
    fun `월 이동은 현재 로그인 상태를 사용하고 진입 조회 상태는 유지한다`() =
        withViewModel { viewModel, _, analytics ->
            val entryToken = MyPageEntryToken()
            viewModel.onScreenEntered(entryToken, AnalyticsLoginState.LoggedIn)

            viewModel.onScreenEntered(entryToken, AnalyticsLoginState.LoggedOut)
            viewModel.onNextMonthClick(AnalyticsLoginState.LoggedOut)

            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedIn)),
                analytics.calendarViewed,
            )
            assertEquals(
                listOf(
                    MonthChanged(
                        direction = StampMonthChangeDirection.Next,
                        month = September2026,
                        loginState = AnalyticsLoginState.LoggedOut,
                    ),
                ),
                analytics.monthChanged,
            )
        }

    @Test
    fun `월을 이동해도 로그인 회원 정보 상태를 유지한다`() =
        withViewModel { viewModel, _, _ ->
            viewModel.onAuthenticated()
            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

            viewModel.onNextMonthClick(AnalyticsLoginState.LoggedIn)

            assertEquals(
                MyPageAccountStatus.LoggedIn(
                    nickname = "링아웃",
                    email = "ringout@example.com",
                ),
                viewModel.uiState.accountStatus,
            )
        }

    @Test
    fun `인증되면 회원 정보를 조회해 로그인 상태로 표시한다`() = runTest {
        val repository = FakeMemberRepository()
        val viewModel = createViewModel(
            memberRepository = repository,
            coroutineScope = this,
        )

        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(1, repository.profileRequestCount)
        assertEquals(
            MyPageAccountStatus.LoggedIn(
                nickname = "링아웃",
                email = "ringout@example.com",
            ),
            viewModel.uiState.accountStatus,
        )
    }

    @Test
    fun `이메일이 없으면 대체 문구를 표시하고 수정된 닉네임을 반영한다`() = runTest {
        val repository = FakeMemberRepository().apply {
            profileLoader = {
                MemberProfile(
                    nickname = "기존닉네임",
                    email = null,
                )
            }
        }
        val viewModel = createViewModel(
            memberRepository = repository,
            coroutineScope = this,
        )

        viewModel.onAuthenticated()
        runCurrent()
        viewModel.onNicknameUpdated("새닉네임")

        assertEquals(
            MyPageAccountStatus.LoggedIn(
                nickname = "새닉네임",
                email = "이메일 정보 없음",
            ),
            viewModel.uiState.accountStatus,
        )
    }

    @Test
    fun `회원 조회 실패 후 다시 시도할 수 있다`() = runTest {
        val repository = FakeMemberRepository().apply {
            profileLoader = { error("회원 조회 실패") }
        }
        val viewModel = createViewModel(
            memberRepository = repository,
            coroutineScope = this,
        )

        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(MyPageAccountStatus.Error, viewModel.uiState.accountStatus)

        repository.profileLoader = {
            MemberProfile(
                nickname = "재시도성공",
                email = "retry@example.com",
            )
        }
        viewModel.retryAccount()
        runCurrent()

        assertEquals(2, repository.profileRequestCount)
        assertEquals(
            MyPageAccountStatus.LoggedIn(
                nickname = "재시도성공",
                email = "retry@example.com",
            ),
            viewModel.uiState.accountStatus,
        )
    }

    @Test
    fun `로그아웃하면 진행 중인 조회 결과를 반영하지 않는다`() = runTest {
        val pendingProfile = CompletableDeferred<MemberProfile>()
        val repository = FakeMemberRepository().apply {
            profileLoader = { pendingProfile.await() }
        }
        val viewModel = createViewModel(
            memberRepository = repository,
            coroutineScope = this,
        )

        viewModel.onAuthenticated()
        runCurrent()
        viewModel.onLoggedOut()
        pendingProfile.complete(
            MemberProfile(
                nickname = "늦은응답",
                email = "late@example.com",
            ),
        )
        runCurrent()

        assertEquals(MyPageAccountStatus.LoggedOut, viewModel.uiState.accountStatus)
    }

    @Test
    fun `로그아웃에 성공하면 완료 이벤트를 생성한다`() = runTest {
        val authRepository = FakeAuthRepository()
        val viewModel = createViewModel(
            authRepository = authRepository,
            coroutineScope = this,
        )

        viewModel.logout()
        runCurrent()

        assertEquals(1, authRepository.logoutRequestCount)
        assertEquals(
            MyPageAccountActionState.Completed(
                eventId = 1L,
                action = MyPageAccountAction.Logout,
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `로그아웃 요청 중에는 중복 요청을 무시한다`() = runTest {
        val logoutGate = CompletableDeferred<Unit>()
        val authRepository = FakeAuthRepository().apply {
            this.logoutGate = logoutGate
        }
        val viewModel = createViewModel(
            authRepository = authRepository,
            coroutineScope = this,
        )

        viewModel.logout()
        viewModel.logout()
        runCurrent()

        assertEquals(1, authRepository.logoutRequestCount)
        assertEquals(
            MyPageAccountActionState.InProgress(MyPageAccountAction.Logout),
            viewModel.uiState.accountAction,
        )

        logoutGate.complete(Unit)
        runCurrent()

        assertEquals(1, authRepository.logoutRequestCount)
    }

    @Test
    fun `로그아웃에 실패하면 오류 상태를 표시한다`() = runTest {
        val authRepository = FakeAuthRepository().apply {
            logoutFailure = IllegalStateException("로그아웃 실패")
        }
        val viewModel = createViewModel(
            authRepository = authRepository,
            coroutineScope = this,
        )

        viewModel.logout()
        runCurrent()

        assertEquals(
            MyPageAccountActionState.Error(
                action = MyPageAccountAction.Logout,
                message = "로그아웃 실패",
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `회원 탈퇴는 탈퇴 분석 로그아웃 순으로 실행한다`() = runTest {
        val order = mutableListOf<String>()
        val memberRepository = FakeMemberRepository(order)
        val authRepository = FakeAuthRepository(order)
        val analytics = RecordingProductAnalyticsRecorder(order)
        val viewModel = createViewModel(
            memberRepository = memberRepository,
            authRepository = authRepository,
            analytics = analytics,
            coroutineScope = this,
        )

        viewModel.withdraw()
        runCurrent()

        assertEquals(listOf("withdraw", "analytics", "logout"), order)
        assertEquals(1, memberRepository.withdrawRequestCount)
        assertEquals(1, analytics.withdrawalEventCount)
        assertEquals(1, authRepository.logoutRequestCount)
        assertEquals(
            MyPageAccountActionState.Completed(
                eventId = 1L,
                action = MyPageAccountAction.Withdraw,
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `회원 탈퇴에 실패하면 분석과 로그아웃을 실행하지 않는다`() = runTest {
        val order = mutableListOf<String>()
        val memberRepository = FakeMemberRepository(order).apply {
            withdrawFailure = IllegalStateException("회원 탈퇴 실패")
        }
        val authRepository = FakeAuthRepository(order)
        val analytics = RecordingProductAnalyticsRecorder(order)
        val viewModel = createViewModel(
            memberRepository = memberRepository,
            authRepository = authRepository,
            analytics = analytics,
            coroutineScope = this,
        )

        viewModel.withdraw()
        runCurrent()

        assertEquals(listOf("withdraw"), order)
        assertEquals(0, analytics.withdrawalEventCount)
        assertEquals(0, authRepository.logoutRequestCount)
        assertEquals(
            MyPageAccountActionState.Error(
                action = MyPageAccountAction.Withdraw,
                message = "회원 탈퇴 실패",
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `회원 탈퇴 후 로그아웃에 실패하면 실행 순서와 오류를 보존한다`() = runTest {
        val order = mutableListOf<String>()
        val authRepository = FakeAuthRepository(order).apply {
            logoutFailure = IllegalStateException("로그아웃 실패")
        }
        val viewModel = createViewModel(
            memberRepository = FakeMemberRepository(order),
            authRepository = authRepository,
            analytics = RecordingProductAnalyticsRecorder(order),
            coroutineScope = this,
        )

        viewModel.withdraw()
        runCurrent()

        assertEquals(listOf("withdraw", "analytics", "logout"), order)
        assertEquals(
            MyPageAccountActionState.Error(
                action = MyPageAccountAction.Withdraw,
                message = "로그아웃 실패",
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `분석 기록에 실패해도 회원 탈퇴 완료 처리를 계속한다`() = runTest {
        val order = mutableListOf<String>()
        val authRepository = FakeAuthRepository(order)
        val analytics = RecordingProductAnalyticsRecorder(order).apply {
            withdrawalFailure = IllegalStateException("분석 실패")
        }
        val viewModel = createViewModel(
            memberRepository = FakeMemberRepository(order),
            authRepository = authRepository,
            analytics = analytics,
            coroutineScope = this,
        )

        viewModel.withdraw()
        runCurrent()

        assertEquals(listOf("withdraw", "analytics", "logout"), order)
        assertEquals(1, authRepository.logoutRequestCount)
        assertEquals(
            MyPageAccountActionState.Completed(
                eventId = 1L,
                action = MyPageAccountAction.Withdraw,
            ),
            viewModel.uiState.accountAction,
        )
    }

    @Test
    fun `계정 액션 오류를 확인하면 초기 상태로 돌아간다`() = runTest {
        val authRepository = FakeAuthRepository().apply {
            logoutFailure = IllegalStateException("로그아웃 실패")
        }
        val viewModel = createViewModel(
            authRepository = authRepository,
            coroutineScope = this,
        )
        viewModel.logout()
        runCurrent()

        viewModel.clearAccountActionError()

        assertEquals(MyPageAccountActionState.Idle, viewModel.uiState.accountAction)
    }

    @Test
    fun `완료 이벤트를 소비하면 초기 상태로 돌아간다`() = runTest {
        val viewModel = createViewModel(coroutineScope = this)
        viewModel.logout()
        runCurrent()
        val completed = viewModel.uiState.accountAction as MyPageAccountActionState.Completed

        viewModel.consumeAccountActionCompletedEvent(completed.eventId)

        assertEquals(MyPageAccountActionState.Idle, viewModel.uiState.accountAction)
    }

    @Test
    fun `상태를 초기화하면 진행 중인 회원 탈퇴 결과를 반영하지 않는다`() = runTest {
        val withdrawGate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val memberRepository = FakeMemberRepository(order).apply {
            this.withdrawGate = withdrawGate
        }
        val viewModel = createViewModel(
            memberRepository = memberRepository,
            authRepository = FakeAuthRepository(order),
            analytics = RecordingProductAnalyticsRecorder(order),
            coroutineScope = this,
        )
        viewModel.withdraw()
        runCurrent()

        viewModel.resetAccountActionFlow()
        withdrawGate.complete(Unit)
        runCurrent()

        assertEquals(MyPageAccountActionState.Idle, viewModel.uiState.accountAction)
        assertEquals(listOf("withdraw"), order)
    }
}

private inline fun withViewModel(
    entries: MutableList<MissionHistoryEntry> = mutableListOf(),
    initialMonth: MissionYearMonth = August2026,
    block: (
        MyPageViewModel,
        FakeMissionHistoryRepository,
        RecordingProductAnalyticsRecorder,
    ) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeMissionHistoryRepository(entries)
    val analytics = RecordingProductAnalyticsRecorder()
    val viewModel = MyPageViewModel(
        getMissionSuccessDates = GetMissionSuccessDates(repository),
        memberRepository = FakeMemberRepository(),
        authRepository = FakeAuthRepository(),
        productAnalyticsRecorder = analytics,
        initialMonth = initialMonth,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository, analytics)
    } finally {
        scope.cancel()
    }
}

private fun createViewModel(
    memberRepository: FakeMemberRepository = FakeMemberRepository(),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    analytics: RecordingProductAnalyticsRecorder = RecordingProductAnalyticsRecorder(),
    coroutineScope: CoroutineScope,
): MyPageViewModel = MyPageViewModel(
    getMissionSuccessDates = GetMissionSuccessDates(FakeMissionHistoryRepository()),
    memberRepository = memberRepository,
    authRepository = authRepository,
    productAnalyticsRecorder = analytics,
    initialMonth = August2026,
    coroutineScope = coroutineScope,
)

private class FakeMissionHistoryRepository(
    val entries: MutableList<MissionHistoryEntry> = mutableListOf(),
) : MissionHistoryRepository {
    val queries = mutableListOf<MissionYearMonth>()
    var failuresRemaining = 0

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        queries += month
        if (failuresRemaining > 0) {
            failuresRemaining--
            error("mission history failed")
        }
        return entries.filter { entry -> entry.completedAt.belongsTo(month) }
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean {
        if (entries.any { it.occurrenceId == entry.occurrenceId }) return false
        entries += entry
        return true
    }
}

private class FakeMemberRepository(
    private val order: MutableList<String> = mutableListOf(),
) : MemberRepository {
    var profileRequestCount = 0
    var withdrawRequestCount = 0
    var withdrawGate: CompletableDeferred<Unit>? = null
    var withdrawFailure: Throwable? = null
    var profileLoader: suspend () -> MemberProfile = {
        MemberProfile(
            nickname = "링아웃",
            email = "ringout@example.com",
        )
    }

    override suspend fun getProfile(): MemberProfile {
        profileRequestCount++
        return profileLoader()
    }

    override suspend fun updateNickname(nickname: String): String = nickname

    override suspend fun withdraw() {
        withdrawRequestCount++
        order += "withdraw"
        withdrawGate?.await()
        withdrawFailure?.let { throw it }
    }
}

private class FakeAuthRepository(
    private val order: MutableList<String> = mutableListOf(),
) : AuthRepository {
    var logoutRequestCount = 0
    var logoutGate: CompletableDeferred<Unit>? = null
    var logoutFailure: Throwable? = null

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
    ) = Unit

    override suspend fun logout() {
        logoutRequestCount++
        order += "logout"
        logoutGate?.await()
        logoutFailure?.let { throw it }
    }
}

private class RecordingProductAnalyticsRecorder(
    private val order: MutableList<String> = mutableListOf(),
) : ProductAnalyticsRecorder {
    val calendarViewed = mutableListOf<CalendarViewed>()
    val monthChanged = mutableListOf<MonthChanged>()
    var withdrawalEventCount = 0
    var withdrawalFailure: Throwable? = null

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
    ) {
        calendarViewed += CalendarViewed(MissionYearMonth(year, month), loginState)
    }

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) {
        monthChanged += MonthChanged(direction, MissionYearMonth(year, month), loginState)
    }

    override fun recordAccountWithdrawalCompleted() {
        withdrawalEventCount++
        order += "analytics"
        withdrawalFailure?.let { throw it }
    }

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = Unit

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = Unit

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = Unit
}

private data class CalendarViewed(
    val month: MissionYearMonth,
    val loginState: AnalyticsLoginState,
)

private data class MonthChanged(
    val direction: StampMonthChangeDirection,
    val month: MissionYearMonth,
    val loginState: AnalyticsLoginState,
)

private val July2026 = MissionYearMonth(2026, 7)
private val August2026 = MissionYearMonth(2026, 8)
private val September2026 = MissionYearMonth(2026, 9)
private val December2025 = MissionYearMonth(2025, 12)
private val January2026 = MissionYearMonth(2026, 1)
