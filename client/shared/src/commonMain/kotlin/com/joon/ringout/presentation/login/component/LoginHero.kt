package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LoginHero(modifier: Modifier = Modifier) {
    val colors = loginColors()
    val dimensions = loginDimensions()
    val description = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.accent)) {
            append("링아웃")
        }
        append("과 함께 꾸준히 실천하는\n나를 만들어가요.")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(LoginWelcomeLogoResource),
            contentDescription = null,
            modifier = Modifier.size(dimensions.heroLogoSize),
        )
        Spacer(Modifier.height(dimensions.logoToGreetingSpacing))
        Text(
            text = "안녕하세요!",
            color = colors.primaryText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = dimensions.greetingFontSize,
                lineHeight = dimensions.greetingLineHeight,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(dimensions.greetingToDescriptionSpacing))
        Text(
            text = description,
            modifier = Modifier.fillMaxWidth(),
            color = colors.primaryText,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = dimensions.descriptionFontSize,
                lineHeight = dimensions.descriptionLineHeight,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(widthDp = 402)
@Composable
private fun LoginHeroPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Column(Modifier.background(loginColors().background).padding(40.dp)) {
            LoginHero()
        }
    }
}
