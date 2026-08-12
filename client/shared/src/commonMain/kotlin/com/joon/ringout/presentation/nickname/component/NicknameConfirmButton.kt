package com.joon.ringout.presentation.nickname.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun NicknameConfirmButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = nicknameChangeColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NicknameConfirmTouchHeight)
            .semantics { stateDescription = if (enabled) "사용 가능" else "사용 불가" }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "닉네임 변경 확인",
                onClick = onClick,
            )
            .padding(vertical = NicknameConfirmTouchPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NicknameConfirmVisualHeight)
                .background(
                    color = if (enabled) colors.primaryAction else colors.disabledAction,
                    shape = RoundedCornerShape(NicknameConfirmCornerRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "확인",
                color = colors.actionContent,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 19.2.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private val NicknameConfirmTouchHeight = 57.dp
private val NicknameConfirmTouchPadding = 2.dp
private val NicknameConfirmVisualHeight = 53.dp
private val NicknameConfirmCornerRadius = 8.dp

@Preview(name = "Nickname confirm - Enabled", widthDp = 402)
@Composable
private fun NicknameConfirmEnabledPreview() {
    RingoutTheme(ThemeMode.Dark) {
        NicknameConfirmButton(
            enabled = true,
            onClick = {},
            modifier = Modifier
                .background(nicknameChangeColors().background)
                .padding(horizontal = 29.dp),
        )
    }
}

@Preview(name = "Nickname confirm - Disabled", widthDp = 402)
@Composable
private fun NicknameConfirmDisabledPreview() {
    RingoutTheme(ThemeMode.Dark) {
        NicknameConfirmButton(
            enabled = false,
            onClick = {},
            modifier = Modifier
                .background(nicknameChangeColors().background)
                .padding(horizontal = 29.dp),
        )
    }
}
