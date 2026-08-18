package com.joon.ringout.presentation.nickname

import com.joon.ringout.domain.member.MemberRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NicknameChangeViewModelTest {
    @Test
    fun `확인하면 저장된 닉네임으로 완료 상태가 된다`() = withViewModel { viewModel, repository ->
        viewModel.onNicknameChange("새닉네임")

        viewModel.confirm()

        assertEquals(listOf("새닉네임"), repository.requests)
        assertEquals("새닉네임", viewModel.uiState.completedNickname)
        assertFalse(viewModel.uiState.isSaving)

        viewModel.consumeCompletedNickname()

        assertNull(viewModel.uiState.completedNickname)
    }
}

private inline fun withViewModel(
    block: (NicknameChangeViewModel, FakeMemberRepository) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeMemberRepository()
    val viewModel = NicknameChangeViewModel(
        initialNickname = "기존닉네임",
        memberRepository = repository,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository)
    } finally {
        scope.cancel()
    }
}

private class FakeMemberRepository : MemberRepository {
    val requests = mutableListOf<String>()

    override suspend fun updateNickname(nickname: String): String {
        requests += nickname
        return nickname
    }

    override suspend fun withdraw() = Unit
}
