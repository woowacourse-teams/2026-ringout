package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.login_apple

private const val AppleLoginButtonAspectRatio = 307f / 50f
private const val AppleLoginButtonLabel = "Apple로 시작하기"

@Composable
internal fun AppleLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .widthIn(max = 308.dp)
            .fillMaxWidth()
            .aspectRatio(AppleLoginButtonAspectRatio)
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = AppleLoginButtonLabel,
                onClick = onClick,
            ),
    ) {
        Image(
            painter = painterResource(Res.drawable.login_apple),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(widthDp = 402)
@Composable
private fun AppleLoginButtonPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .background(loginColors().background)
                .padding(20.dp),
        ) {
            AppleLoginButton(onClick = {})
        }
    }
}
