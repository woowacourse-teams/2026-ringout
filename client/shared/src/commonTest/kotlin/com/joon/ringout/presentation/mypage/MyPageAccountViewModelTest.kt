package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.MemberRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageAccountViewModelTest {
    @Test
    fun `인증되면 회원 정보를 조회해 로그인 상태로 표시한다`() = runTest {
        val repository = FakeMemberRepository()
        val viewModel = MyPageAccountViewModel(repository, this)

        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(1, repository.profileRequestCount)
        assertEquals(
            MyPageAccountUiState.LoggedIn(
                nickname = "링아웃",
                email = "ringout@example.com",
            ),
            viewModel.uiState,
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
        val viewModel = MyPageAccountViewModel(repository, this)

        viewModel.onAuthenticated()
        runCurrent()
        viewModel.onNicknameUpdated("새닉네임")

        assertEquals(
            MyPageAccountUiState.LoggedIn(
                nickname = "새닉네임",
                email = "이메일 정보 없음",
            ),
            viewModel.uiState,
        )
    }

    @Test
    fun `회원 조회 실패 후 다시 시도할 수 있다`() = runTest {
        val repository = FakeMemberRepository().apply {
            profileLoader = { error("회원 조회 실패") }
        }
        val viewModel = MyPageAccountViewModel(repository, this)

        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(MyPageAccountUiState.Error, viewModel.uiState)

        repository.profileLoader = {
            MemberProfile(
                nickname = "재시도성공",
                email = "retry@example.com",
            )
        }
        viewModel.retry()
        runCurrent()

        assertEquals(2, repository.profileRequestCount)
        assertEquals(
            MyPageAccountUiState.LoggedIn(
                nickname = "재시도성공",
                email = "retry@example.com",
            ),
            viewModel.uiState,
        )
    }

    @Test
    fun `로그아웃하면 진행 중인 조회 결과를 반영하지 않는다`() = runTest {
        val pendingProfile = CompletableDeferred<MemberProfile>()
        val repository = FakeMemberRepository().apply {
            profileLoader = { pendingProfile.await() }
        }
        val viewModel = MyPageAccountViewModel(repository, this)

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

        assertEquals(MyPageAccountUiState.LoggedOut, viewModel.uiState)
    }
}

private class FakeMemberRepository : MemberRepository {
    var profileRequestCount = 0
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

    override suspend fun withdraw() = Unit
}
