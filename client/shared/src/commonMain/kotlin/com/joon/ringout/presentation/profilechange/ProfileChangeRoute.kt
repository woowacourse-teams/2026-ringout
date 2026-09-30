package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
    var profileImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var profileImageError by remember { mutableStateOf<String?>(null) }
    var showProfileImageSizeLimitDialog by remember { mutableStateOf(false) }
    val launchProfileImagePicker = rememberProfileImagePicker { result ->
        when (result) {
            is ProfileImagePickResult.Selected -> {
                profileImage = result.image
                profileImageError = null
                showProfileImageSizeLimitDialog = false
            }
            ProfileImagePickResult.TooLarge -> showProfileImageSizeLimitDialog = true
            ProfileImagePickResult.Cancelled -> profileImageError = null
            ProfileImagePickResult.Failure -> {
                profileImageError = "사진을 불러오지 못했어요. 다시 선택해 주세요."
            }
        }
    }
    LaunchedEffect(viewModel, uiState.completedNickname) {
        val updatedNickname = uiState.completedNickname ?: return@LaunchedEffect
        onNicknameChanged(updatedNickname)
        viewModel.consumeCompletedNickname()
    }

    ProfileChangeScreen(
        uiState = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onBackClick = onBackClick,
        profileImage = profileImage,
        profileImageError = profileImageError,
        showProfileImageSizeLimitDialog = showProfileImageSizeLimitDialog,
        onProfileImageSizeLimitConfirm = { showProfileImageSizeLimitDialog = false },
        onProfileImageChangeClick = {
            launchProfileImagePicker()
        },
        onConfirmClick = viewModel::confirm,
        modifier = modifier,
    )
}
