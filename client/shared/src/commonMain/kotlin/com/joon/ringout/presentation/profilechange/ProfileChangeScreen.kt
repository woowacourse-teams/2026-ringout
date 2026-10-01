package com.joon.ringout.presentation.profilechange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.profilechange.component.ProfileChangeHeader
import com.joon.ringout.presentation.profilechange.component.ProfileConfirmButton
import com.joon.ringout.presentation.profilechange.component.nickname.NicknameInputField
import com.joon.ringout.presentation.profilechange.component.nickname.NicknameValidationList
import com.joon.ringout.presentation.profilechange.component.profileChangeColors
import com.joon.ringout.presentation.profilechange.component.profileimage.ProfileImageEditor
import com.joon.ringout.presentation.profilechange.component.profileimage.ProfileImageSizeLimitDialog

@Composable
internal fun ProfileChangeScreen(
    uiState: ProfileChangeUiState,
    onNicknameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    profileImage: ImageBitmap?,
    profileImageError: String?,
    showProfileImageSizeLimitDialog: Boolean,
    onProfileImageSizeLimitConfirm: () -> Unit,
    onProfileImageChangeClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = profileChangeColors()
    val hasInput = uiState.nickname.isNotEmpty()
    val nicknameSectionRequester = remember { BringIntoViewRequester() }
    var isNicknameFocused by remember { mutableStateOf(false) }
    var contentViewportHeight by remember { mutableIntStateOf(0) }

    // 키보드로 본문 높이가 바뀌면 입력창과 검증 문구를 함께 표시한다.
    LaunchedEffect(isNicknameFocused, contentViewportHeight) {
        if (isNicknameFocused && contentViewportHeight > 0) {
            nicknameSectionRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { contentViewportHeight = it.height }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ProfileChangeHorizontalPadding, vertical = 12.dp),
        ) {
            ProfileChangeHeader(onBackClick = onBackClick)
            Spacer(Modifier.height(ProfileChangeHeaderToProfileImageSpacing))
            ProfileImageEditor(
                profileImage = profileImage,
                profileImageUrl = uiState.profileImageUrl,
                enabled = !uiState.isSaving,
                errorMessage = profileImageError,
                onProfileImageChangeClick = onProfileImageChangeClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(ProfileImageToTitleSpacing))
            Text(
                text = "사용할 닉네임을\n입력해주세요",
                modifier = Modifier
                    .widthIn(max = ProfileChangeMainContentMaxWidth)
                    .fillMaxWidth(),
                color = colors.primaryText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 39.2.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(ProfileChangeTitleToInputSpacing))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(nicknameSectionRequester),
            ) {
                NicknameInputField(
                    nickname = uiState.nickname,
                    hasInput = hasInput,
                    isValid = uiState.validation.isValid,
                    onNicknameChange = onNicknameChange,
                    onDone = onConfirmClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isNicknameFocused = it.isFocused },
                )
                Spacer(Modifier.height(NicknameInputToValidationSpacing))
                NicknameValidationList(
                    isLengthValid = uiState.validation.isLengthValid,
                    hasOnlyAllowedCharacters = uiState.validation.hasOnlyAllowedCharacters,
                    modifier = Modifier.widthIn(max = NicknameValidationMaxWidth),
                )
            }
            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(NicknameInputToValidationSpacing))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProfileChangeBottomBarHeight)
                .background(colors.background)
                .padding(
                    horizontal = ProfileChangeButtonHorizontalPadding,
                    vertical = ProfileChangeBottomBarPadding,
                ),
        ) {
            ProfileConfirmButton(
                enabled = uiState.validation.isValid && !uiState.isSaving,
                isSaving = uiState.isSaving,
                onClick = onConfirmClick,
            )
        }
    }

    if (showProfileImageSizeLimitDialog) {
        ProfileImageSizeLimitDialog(onConfirm = onProfileImageSizeLimitConfirm)
    }
}

private val ProfileChangeHorizontalPadding = 20.dp
private val ProfileChangeMainContentMaxWidth = 345.dp
private val NicknameValidationMaxWidth = 327.dp
private val ProfileChangeHeaderToProfileImageSpacing = 32.dp
private val ProfileImageToTitleSpacing = 28.dp
private val ProfileChangeTitleToInputSpacing = 10.dp
private val NicknameInputToValidationSpacing = 10.dp
private val ProfileChangeButtonHorizontalPadding = 29.dp
private val ProfileChangeBottomBarHeight = 77.dp
private val ProfileChangeBottomBarPadding = 10.dp

@Preview(name = "Nickname change - Valid", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeValidPreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "닉네임닉네임12",
    )
}

@Preview(name = "Nickname change - Invalid", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeInvalidPreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "닉네임@#12",
    )
}

@Preview(name = "Nickname change - Light", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeLightPreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Light,
        initialNickname = "Ringout12",
    )
}

@Preview(name = "Nickname change - Profile photo selected", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeProfileImagePreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "Ringout12",
        showProfileImage = true,
    )
}

@Preview(name = "Profile image size limit - Default", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeProfileImageTooLargeDefaultPreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "Ringout12",
        showProfileImageSizeLimitDialog = true,
    )
}

@Preview(name = "Profile image size limit - Selected", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileChangeProfileImageTooLargeSelectedPreview() {
    ProfileChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "Ringout12",
        showProfileImage = true,
        showProfileImageSizeLimitDialog = true,
    )
}

@Composable
private fun ProfileChangeInteractivePreview(
    themeMode: ThemeMode,
    initialNickname: String,
    showProfileImage: Boolean = false,
    showProfileImageSizeLimitDialog: Boolean = false,
) {
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
    val profileImage = remember(showProfileImage) {
        if (showProfileImage) {
            ImageBitmap(1, 1).also { bitmap ->
                Canvas(bitmap).drawRect(
                    rect = Rect(left = 0f, top = 0f, right = 1f, bottom = 1f),
                    paint = Paint().apply { color = Color(0xFF7D62D9) },
                )
            }
        } else {
            null
        }
    }

    RingoutTheme(themeMode) {
        ProfileChangeScreen(
            uiState = ProfileChangeUiState(
                nickname = nickname,
                validation = validateNickname(nickname),
            ),
            onNicknameChange = { nickname = it },
            onBackClick = {},
            profileImage = profileImage,
            profileImageError = null,
            showProfileImageSizeLimitDialog = showProfileImageSizeLimitDialog,
            onProfileImageSizeLimitConfirm = {},
            onProfileImageChangeClick = {},
            onConfirmClick = {},
        )
    }
}
