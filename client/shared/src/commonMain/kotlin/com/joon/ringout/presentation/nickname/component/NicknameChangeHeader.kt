package com.joon.ringout.presentation.nickname.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun NicknameChangeHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = nicknameChangeColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NicknameChangeHeaderHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(NicknameChangeBackTouchSize)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            Image(
                painter = painterResource(NicknameChangeBackIconResource),
                contentDescription = null,
                modifier = Modifier.size(NicknameChangeBackIconSize),
                colorFilter = ColorFilter.tint(colors.primaryText),
            )
        }
        Text(
            text = "닉네임 변경",
            color = colors.primaryText,
            maxLines = 1,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private val NicknameChangeHeaderHeight = 48.dp
private val NicknameChangeBackTouchSize = 48.dp
private val NicknameChangeBackIconSize = 24.dp

@Preview(name = "Nickname header - Dark", widthDp = 402)
@Composable
private fun NicknameChangeHeaderDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.background(nicknameChangeColors().background)) {
            NicknameChangeHeader(onBackClick = {})
        }
    }
}

@Preview(name = "Nickname header - Light", widthDp = 402)
@Composable
private fun NicknameChangeHeaderLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        Box(Modifier.background(nicknameChangeColors().background)) {
            NicknameChangeHeader(onBackClick = {})
        }
    }
}
