package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.login_lock

@Composable
internal fun LoginHero(modifier: Modifier = Modifier) {
    val colors = loginColors()
    val dimensions = loginDimensions()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.login_lock),
            contentDescription = null,
            modifier = Modifier.size(dimensions.heroImageSize),
        )
        Spacer(Modifier.height(dimensions.imageToTextSpacing))
        Text(
            text = "계정을 연동하면\n데이터를 안전하게 보관할 수 있어요",
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensions.heroTextHeight),
            color = colors.primaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(widthDp = 402)
@Composable
private fun LoginHeroPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginHero(
            modifier = Modifier.background(loginColors().background),
        )
    }
}
