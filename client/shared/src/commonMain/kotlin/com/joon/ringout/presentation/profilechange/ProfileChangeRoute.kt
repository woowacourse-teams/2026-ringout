package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus

@Composable
internal fun ProfileChangeRoute(
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

    val viewModel: ProfileChangeViewModel = viewModel {
        ProfileChangeViewModel(account.nickname, memberRepository)
    }
    val uiState = viewModel.uiState
    LaunchedEffect(viewModel, uiState.completedNickname) {
        val updatedNickname = uiState.completedNickname ?: return@LaunchedEffect
        onNicknameChanged(updatedNickname)
        viewModel.consumeCompletedNickname()
    }

    ProfileChangeScreen(
        uiState = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onBackClick = onBackClick,
        onConfirmClick = viewModel::confirm,
        modifier = modifier,
    )
}
