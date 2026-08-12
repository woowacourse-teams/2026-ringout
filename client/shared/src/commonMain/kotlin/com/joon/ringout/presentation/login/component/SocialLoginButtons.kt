package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.login.SocialLoginProvider
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SocialLoginButtons(
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = loginColors()
    val dimensions = loginDimensions()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensions.socialPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.socialButtonSpacing),
    ) {
        SocialLoginProvider.entries.forEach { provider ->
            SocialLoginButton(
                provider = provider,
                colors = colors,
                dimensions = dimensions,
                onClick = { onSocialLoginClick(provider) },
            )
        }
    }
}

@Composable
private fun SocialLoginButton(
    provider: SocialLoginProvider,
    colors: LoginColors,
    dimensions: LoginDimensions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = provider.buttonStyle(colors)
    val shape = RoundedCornerShape(8.dp)
    val borderModifier = if (style.borderColor == null) {
        Modifier
    } else {
        Modifier.border(1.dp, style.borderColor, shape)
    }

    Row(
        modifier = modifier
            .widthIn(max = dimensions.socialButtonWidth)
            .fillMaxWidth()
            .height(dimensions.socialButtonHeight)
            .clip(shape)
            .background(style.backgroundColor)
            .then(borderModifier)
            .clickable(
                role = Role.Button,
                onClickLabel = style.label,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(style.iconSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(style.icon),
                contentDescription = null,
                modifier = Modifier.size(style.iconSize),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = style.label,
            color = style.contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

private data class SocialLoginButtonStyle(
    val label: String,
    val backgroundColor: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val icon: DrawableResource,
    val iconSize: Dp,
    val iconSlotSize: Dp,
)

private fun SocialLoginProvider.buttonStyle(colors: LoginColors): SocialLoginButtonStyle = when (this) {
    SocialLoginProvider.Google -> SocialLoginButtonStyle(
        label = "Google로 시작하기",
        backgroundColor = colors.googleBackground,
        contentColor = colors.googleText,
        borderColor = colors.googleBorder,
        icon = LoginGoogleIconResource,
        iconSize = 25.6.dp,
        iconSlotSize = 32.dp,
    )

    SocialLoginProvider.Kakao -> SocialLoginButtonStyle(
        label = "카카오로 시작하기",
        backgroundColor = colors.kakaoBackground,
        contentColor = colors.kakaoText,
        borderColor = null,
        icon = LoginKakaoIconResource,
        iconSize = 20.dp,
        iconSlotSize = 20.dp,
    )

    SocialLoginProvider.Naver -> SocialLoginButtonStyle(
        label = "네이버로 시작하기",
        backgroundColor = colors.naverBackground,
        contentColor = colors.naverText,
        borderColor = null,
        icon = LoginNaverIconResource,
        iconSize = 20.dp,
        iconSlotSize = 20.dp,
    )
}

@Preview(widthDp = 402)
@Composable
private fun SocialLoginButtonsPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.background(loginColors().background)) {
            SocialLoginButtons(onSocialLoginClick = {})
        }
    }
}
