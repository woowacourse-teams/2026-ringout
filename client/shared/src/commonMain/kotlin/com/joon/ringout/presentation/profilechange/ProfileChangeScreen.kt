package com.joon.ringout.presentation.profilechange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

@Composable
internal fun ProfileChangeScreen(
    uiState: ProfileChangeUiState,
    onNicknameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = profileChangeColors()
    val hasInput = uiState.nickname.isNotEmpty()

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
                .padding(horizontal = ProfileChangeHorizontalPadding, vertical = 12.dp),
        ) {
            ProfileChangeHeader(onBackClick = onBackClick)
            Spacer(Modifier.height(ProfileChangeHeaderToTitleSpacing))
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
            NicknameInputField(
                nickname = uiState.nickname,
                hasInput = hasInput,
                isValid = uiState.validation.isValid,
                onNicknameChange = onNicknameChange,
                onDone = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NicknameInputToValidationSpacing))
            NicknameValidationList(
                isLengthValid = uiState.validation.isLengthValid,
                hasOnlyAllowedCharacters = uiState.validation.hasOnlyAllowedCharacters,
                modifier = Modifier.widthIn(max = NicknameValidationMaxWidth),
            )
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
                onClick = onConfirmClick,
            )
        }
    }
}

private val ProfileChangeHorizontalPadding = 20.dp
private val ProfileChangeMainContentMaxWidth = 345.dp
private val NicknameValidationMaxWidth = 327.dp
private val ProfileChangeHeaderToTitleSpacing = 9.dp
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

@Composable
private fun ProfileChangeInteractivePreview(
    themeMode: ThemeMode,
    initialNickname: String,
) {
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }

    RingoutTheme(themeMode) {
        ProfileChangeScreen(
            uiState = ProfileChangeUiState(
                nickname = nickname,
                validation = validateNickname(nickname),
            ),
            onNicknameChange = { nickname = it },
            onBackClick = {},
            onConfirmClick = {},
        )
    }
}
