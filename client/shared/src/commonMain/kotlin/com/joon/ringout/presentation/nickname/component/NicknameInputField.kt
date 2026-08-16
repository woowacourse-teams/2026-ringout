package com.joon.ringout.presentation.nickname.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun NicknameInputField(
    nickname: String,
    hasInput: Boolean,
    isValid: Boolean,
    onNicknameChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = nicknameChangeColors()
    val shape = RoundedCornerShape(NicknameInputCornerRadius)
    val borderColor = when {
        !hasInput -> colors.inputIdleBorder
        isValid -> colors.validInputBorder
        else -> colors.invalidInputBorder
    }

    BasicTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        modifier = modifier
            .fillMaxWidth()
            .height(NicknameInputHeight)
            .background(
                color = colors.inputSurface,
                shape = shape,
            )
            .border(
                width = NicknameInputBorderWidth,
                color = borderColor,
                shape = shape,
            )
            .semantics {
                contentDescription = "닉네임 입력"
                if (hasInput && !isValid) {
                    error("닉네임 조건을 확인해주세요")
                }
            },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.inputText,
            fontSize = 16.sp,
            lineHeight = 19.2.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(colors.primaryAction),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (isValid) onDone() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = NicknameInputHorizontalPadding,
                        end = NicknameInputEndPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(NicknameChangeUserIconResource),
                    contentDescription = null,
                    modifier = Modifier.size(NicknameInputIconSize),
                )
                Box(
                    modifier = Modifier
                        .width(NicknameInputIconSpacing)
                        .height(1.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (nickname.isEmpty()) {
                        Text(
                            text = "닉네임을 입력해주세요",
                            color = colors.inputText,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 19.2.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

private val NicknameInputHeight = 56.dp
private val NicknameInputBorderWidth = 2.dp
private val NicknameInputCornerRadius = 12.dp
private val NicknameInputHorizontalPadding = 16.dp
private val NicknameInputEndPadding = 9.dp
private val NicknameInputIconSize = 20.dp
private val NicknameInputIconSpacing = 12.dp

@Preview(name = "Nickname input - Valid", widthDp = 402)
@Composable
private fun NicknameInputValidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.background(nicknameChangeColors().background).padding(horizontal = 20.dp)) {
            NicknameInputField(
                nickname = "닉네임12",
                hasInput = true,
                isValid = true,
                onNicknameChange = {},
                onDone = {},
            )
        }
    }
}

@Preview(name = "Nickname input - Invalid", widthDp = 402)
@Composable
private fun NicknameInputInvalidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.background(nicknameChangeColors().background).padding(horizontal = 20.dp)) {
            NicknameInputField(
                nickname = "닉네임@#12",
                hasInput = true,
                isValid = false,
                onNicknameChange = {},
                onDone = {},
            )
        }
    }
}
