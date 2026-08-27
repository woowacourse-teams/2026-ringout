package com.joon.ringout.presentation.nickname

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus

@Composable
internal fun NicknameChangeRoute(
    accountStatus: MyPageAccountStatus,
    authSessionState: AuthSessionState,
    memberRepository: MemberRepository,
    onBackClick: () -> Unit,
    onNicknameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = accountStatus as? MyPageAccountStatus.LoggedIn
    if (account == null) {
        LaunchedEffect(authSessionState, accountStatus) {
            if (
                authSessionState != AuthSessionState.Restoring &&
                accountStatus != MyPageAccountStatus.Loading
            ) {
                onBackClick()
            }
        }
        return
    }

    val viewModel: NicknameChangeViewModel = viewModel {
        NicknameChangeViewModel(account.nickname, memberRepository)
    }
    val uiState = viewModel.uiState
    LaunchedEffect(uiState.completedNickname) {
        val updatedNickname = uiState.completedNickname ?: return@LaunchedEffect
        onNicknameChanged(updatedNickname)
        viewModel.consumeCompletedNickname()
    }

    NicknameChangeScreen(
        uiState = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onBackClick = onBackClick,
        onConfirmClick = viewModel::confirm,
        modifier = modifier,
    )
}
