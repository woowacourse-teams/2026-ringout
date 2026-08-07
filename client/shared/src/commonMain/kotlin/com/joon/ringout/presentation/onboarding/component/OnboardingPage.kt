package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.OnboardingPageContent
import com.joon.ringout.presentation.onboarding.defaultOnboardingPages
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingPage(
    content: OnboardingPageContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(content.image),
                contentDescription = content.imageContentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = content.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                content.description.forEach { segment ->
                    withStyle(
                        SpanStyle(
                            color = if (segment.isAccent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.ringoutColors.onboardingDescriptionContent
                            },
                            fontWeight = if (segment.isAccent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if(segment.isAccent) 20.sp else 16.sp,
                        ),
                    ) {
                        append(segment.text)
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Dark onboarding page", widthDp = 402, heightDp = 760)
@Composable
private fun DarkOnboardingPagePreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        OnboardingPage(
            content = defaultOnboardingPages.first(),
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Preview(name = "Light onboarding page", widthDp = 402, heightDp = 760)
@Composable
private fun LightOnboardingPagePreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        OnboardingPage(
            content = defaultOnboardingPages.last(),
            modifier = Modifier.padding(24.dp),
        )
    }
}
