package com.joon.ringout.presentation.termsreagreement

import com.joon.ringout.domain.auth.*
import com.joon.ringout.domain.connectivity.*
import com.joon.ringout.domain.terms.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TermsReagreementViewModelTest {
    @Test
    fun `비로그인 사용자는 조회 없이 기존 기능을 이용한다`() = runTest {
        val f = Fixture(this, loggedIn = false)
        runCurrent()
        assertEquals(0, f.repo.getCount)
        assertFalse(f.vm.uiState.blocksService)
    }
    @Test
    fun `로그인 세션에서 두 약관이 최신이면 확인 후 진입을 허용한다`() = runTest {
        val f = Fixture(this)
        f.repo.status = terms(current = true)
        runCurrent()
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
        f.session.markAuthenticated()
        runCurrent()
        assertEquals(1, f.repo.getCount)
    }
    @Test
    fun `한 약관이 구버전이면 안내를 먼저 띄우고 필요한 약관만 선택한다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        assertEquals(TermsGatePhase.Notice, f.vm.uiState.phase)
        f.vm.submit()
        assertEquals(0, f.repo.postCount)
        f.vm.openConsent()
        f.vm.setAgreed(RequiredTermType.PRIVACY, true)
        assertTrue(f.vm.uiState.selected.isEmpty())
        f.vm.setAgreed(RequiredTermType.SERVICE, true)
        assertTrue(f.vm.uiState.canSubmit)
    }
    @Test
    fun `오프라인 진입은 확인 완료로 저장하지 않고 연결 복구 후 안내한다`() = runTest {
        val f = Fixture(this, connection = NetworkStatus.Offline)
        runCurrent()
        assertEquals(TermsGatePhase.Offline, f.vm.uiState.phase)
        assertFalse(f.vm.uiState.blocksService)
        assertEquals(0, f.repo.getCount)
        f.network.status.value = NetworkStatus.Online
        runCurrent()
        assertEquals(TermsGatePhase.Notice, f.vm.uiState.phase)
        assertEquals(1, f.repo.getCount)
    }
    @Test
    fun `확인 중 연결이 끊기면 늦은 응답을 무시하고 복구 후 다시 확인한다`() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<RequiredTermsStatus>()
        f.repo.loader = { withContext(NonCancellable) { gate.await() } }
        runCurrent()
        f.network.status.value = NetworkStatus.Offline
        runCurrent()
        gate.complete(terms())
        runCurrent()
        assertEquals(TermsGatePhase.Offline, f.vm.uiState.phase)
        f.repo.loader = { terms() }
        f.network.status.value = NetworkStatus.Online
        runCurrent()
        assertEquals(TermsGatePhase.Notice, f.vm.uiState.phase)
    }
    @Test
    fun `온라인 조회 실패는 진입을 차단하고 재시도를 제공한다`() = runTest {
        val f = Fixture(this)
        f.repo.loader = { error("서버 오류") }
        runCurrent()
        assertEquals(TermsGatePhase.Failure, f.vm.uiState.phase)
        assertTrue(f.vm.uiState.blocksService)
        f.repo.loader = { terms(current = true) }
        f.vm.retry()
        runCurrent()
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
    }
    @Test
    fun `필수 선택을 완료해야 제출하며 중복 제출을 막고 서버 확인 후에만 진입한다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.submit()
        assertEquals(0, f.repo.postCount)
        f.vm.setAllAgreed(true)
        val postGate = CompletableDeferred<Unit>()
        f.repo.poster = { postGate.await(); f.repo.status = terms(current = true) }
        f.vm.submit()
        f.vm.submit()
        runCurrent()
        assertEquals(1, f.repo.postCount)
        assertTrue(f.vm.uiState.blocksService)
        postGate.complete(Unit)
        runCurrent()
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
        assertEquals("2026-09-30", f.repo.lastDate)
        assertEquals(3, f.repo.getCount)
    }
    @Test
    fun `제출 실패 시 선택 상태를 유지하고 다시 제출할 수 있다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.setAllAgreed(true)
        f.repo.poster = { error("실패") }
        f.vm.submit()
        runCurrent()
        assertEquals(TermsGatePhase.Consent, f.vm.uiState.phase)
        assertTrue(f.vm.uiState.canSubmit)
        assertNotNull(f.vm.uiState.errorMessage)
    }
    @Test
    fun `동의 저장 후 확인 실패의 재시도는 동의를 다시 제출하지 않는다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.setAllAgreed(true)
        f.repo.poster = { f.repo.loader = { error("결과 확인 실패") } }
        f.vm.submit()
        runCurrent()
        assertEquals(TermsGatePhase.Failure, f.vm.uiState.phase)
        f.repo.loader = { terms(current = true) }
        f.vm.retry()
        runCurrent()
        assertEquals(1, f.repo.postCount)
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
    }
    @Test
    fun `제출 직전에 약관 버전이 달라지면 선택을 초기화하고 재확인을 요구한다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.setAllAgreed(true)
        f.repo.status = terms(version = "2026-10-01")
        f.vm.submit()
        runCurrent()
        assertEquals(0, f.repo.postCount)
        assertFalse(f.vm.uiState.canSubmit)
        assertEquals(TermsGatePhase.Consent, f.vm.uiState.phase)
        assertNotNull(f.vm.uiState.errorMessage)
    }
    @Test
    fun `다른 기기에서 이미 동의했다면 중복 제출 없이 진입한다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.setAllAgreed(true)
        f.repo.status = terms(current = true)
        f.vm.submit()
        runCurrent()
        assertEquals(0, f.repo.postCount)
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
    }
    @Test
    fun `계정 전환 후 이전 응답이 재동의 상태를 덮어쓰지 않는다`() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<RequiredTermsStatus>()
        f.repo.loader = { withContext(NonCancellable) { gate.await() } }
        runCurrent()
        f.repo.loader = { terms() }
        f.session.startNewSession()
        runCurrent()
        gate.complete(terms(current = true))
        runCurrent()
        assertEquals(TermsGatePhase.Notice, f.vm.uiState.phase)
    }
    @Test
    fun `로그아웃하면 재동의 제한과 선택 상태를 해제한다`() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.vm.openConsent()
        f.vm.setAllAgreed(true)
        f.vm.logout()
        runCurrent()
        assertEquals(TermsGatePhase.Allowed, f.vm.uiState.phase)
        assertTrue(f.vm.uiState.selected.isEmpty())
        assertEquals(AuthSessionState.Unauthenticated, f.session.state.value)
    }
}

private fun terms(current: Boolean = false, version: String = "2026-09-30") = RequiredTermsStatus(listOf(
    RequiredTermStatus(RequiredTermType.SERVICE, 3, version, if (current) version else null, !current),
    RequiredTermStatus(RequiredTermType.PRIVACY, 2, "2026-08-13", "2026-08-13", false),
))
private class Fixture(scope: TestScope, loggedIn: Boolean = true, connection: NetworkStatus = NetworkStatus.Online) {
    val session = AuthSession().apply { if (loggedIn) markAuthenticated() else clear() }
    val network = FakeNetworkMonitor(connection)
    val repo = FakeTermsRepository()
    val auth = object : AuthRepository {
        override suspend fun restoreSession() = Unit
        override suspend fun loginWithApple(idToken: String) = SocialLoginOutcome.Authenticated
        override suspend fun loginWithGoogle(accessToken: String) = SocialLoginOutcome.Authenticated
        override suspend fun loginWithKakao(accessToken: String) = SocialLoginOutcome.Authenticated
        override suspend fun signup(signupToken: String, agreedTerms: Set<AuthTerm>, agreedAt: String) = Unit
        override suspend fun logout() = session.clear()
    }
    val vm = TermsReagreementViewModel(repo, auth, session, network, { "2026-09-30" }, scope.backgroundScope)
}
private class FakeTermsRepository : TermsRepository {
    var status = terms()
    var getCount = 0
    var postCount = 0
    var lastDate: String? = null
    var loader: suspend () -> RequiredTermsStatus = { status }
    var poster: suspend () -> Unit = { status = terms(current = true) }
    override suspend fun getStatus(): RequiredTermsStatus { getCount++; return loader() }
    override suspend fun agreeRequiredTerms(agreedAt: String) { postCount++; lastDate = agreedAt; poster() }
}

private class FakeNetworkMonitor(connection: NetworkStatus) : NetworkMonitor {
    override val status = MutableStateFlow(connection)
}
