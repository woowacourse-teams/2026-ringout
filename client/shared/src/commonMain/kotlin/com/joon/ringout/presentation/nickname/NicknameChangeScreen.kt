package com.joon.ringout.presentation.nickname

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.nickname.component.NicknameChangeHeader
import com.joon.ringout.presentation.nickname.component.NicknameConfirmButton
import com.joon.ringout.presentation.nickname.component.NicknameInputField
import com.joon.ringout.presentation.nickname.component.NicknameValidationList
import com.joon.ringout.presentation.nickname.component.nicknameChangeColors

@Composable
fun NicknameChangeScreen(
    initialNickname: String,
    onBackClick: () -> Unit,
    onConfirmClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NicknameChangeViewModel = viewModel(key = initialNickname) {
        NicknameChangeViewModel(initialNickname)
    }
    PlatformBackHandler(onBack = onBackClick)
    NicknameChangeScreenContent(
        uiState = viewModel.uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onBackClick = onBackClick,
        onConfirmClick = { onConfirmClick(viewModel.uiState.nickname) },
        modifier = modifier,
    )
}

@Composable
internal fun NicknameChangeScreenContent(
    uiState: NicknameChangeUiState,
    onNicknameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = nicknameChangeColors()
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
                .padding(horizontal = NicknameChangeHorizontalPadding, vertical = 12.dp),
        ) {
            NicknameChangeHeader(onBackClick = onBackClick)
            Spacer(Modifier.height(NicknameHeaderToTitleSpacing))
            Text(
                text = "사용할 닉네임을\n입력해주세요",
                modifier = Modifier
                    .widthIn(max = NicknameChangeMainContentMaxWidth)
                    .fillMaxWidth(),
                color = colors.primaryText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 39.2.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(NicknameTitleToInputSpacing))
            NicknameInputField(
                nickname = uiState.nickname,
                hasInput = hasInput,
                isValid = uiState.validation.isValid,
                onNicknameChange = onNicknameChange,
                onDone = onConfirmClick,
                modifier = Modifier.widthIn(max = NicknameChangeMainContentMaxWidth),
            )
            Spacer(Modifier.height(NicknameInputToValidationSpacing))
            NicknameValidationList(
                isLengthValid = uiState.validation.isLengthValid,
                hasOnlyAllowedCharacters = uiState.validation.hasOnlyAllowedCharacters,
                modifier = Modifier.widthIn(max = NicknameValidationMaxWidth),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NicknameChangeBottomBarHeight)
                .background(colors.background)
                .padding(
                    horizontal = NicknameChangeButtonHorizontalPadding,
                    vertical = NicknameChangeBottomBarPadding,
                ),
        ) {
            NicknameConfirmButton(
                enabled = uiState.validation.isValid,
                onClick = onConfirmClick,
            )
        }
    }
}

private val NicknameChangeHorizontalPadding = 20.dp
private val NicknameChangeMainContentMaxWidth = 345.dp
private val NicknameValidationMaxWidth = 327.dp
private val NicknameHeaderToTitleSpacing = 9.dp
private val NicknameTitleToInputSpacing = 10.dp
private val NicknameInputToValidationSpacing = 10.dp
private val NicknameChangeButtonHorizontalPadding = 29.dp
private val NicknameChangeBottomBarHeight = 77.dp
private val NicknameChangeBottomBarPadding = 10.dp

@Preview(name = "Nickname change - Valid", widthDp = 402, heightDp = 941)
@Composable
private fun NicknameChangeValidPreview() {
    NicknameChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "닉네임닉네임12",
    )
}

@Preview(name = "Nickname change - Invalid", widthDp = 402, heightDp = 941)
@Composable
private fun NicknameChangeInvalidPreview() {
    NicknameChangeInteractivePreview(
        themeMode = ThemeMode.Dark,
        initialNickname = "닉네임@#12",
    )
}

@Preview(name = "Nickname change - Light", widthDp = 402, heightDp = 941)
@Composable
private fun NicknameChangeLightPreview() {
    NicknameChangeInteractivePreview(
        themeMode = ThemeMode.Light,
        initialNickname = "Ringout12",
    )
}

@Composable
private fun NicknameChangeInteractivePreview(
    themeMode: ThemeMode,
    initialNickname: String,
) {
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }

    RingoutTheme(themeMode) {
        NicknameChangeScreenContent(
            uiState = NicknameChangeUiState(
                nickname = nickname,
                validation = validateNickname(nickname),
            ),
            onNicknameChange = { nickname = it },
            onBackClick = {},
            onConfirmClick = {},
        )
    }
}
