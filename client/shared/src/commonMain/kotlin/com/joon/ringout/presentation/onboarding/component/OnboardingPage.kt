package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.OnboardingStep
import com.joon.ringout.presentation.onboarding.pageContent
import com.joon.ringout.ringoutColors

@Composable
internal fun OnboardingPage(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    val content = step.pageContent()
    val description = buildAnnotatedString {
        append(content.description)
        if (step == OnboardingStep.Interval) {
            val start = content.description.indexOf("링아웃")
            addStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                start,
                start + "링아웃".length,
            )
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.ringoutColors.mainScreenBackground)
            .testTag("onboarding-page-${step.name}"),
    ) {
        val diameter = minOf(312.dp, maxWidth - 40.dp, (maxHeight * 0.58f).coerceAtLeast(180.dp))
        val imageToTextSpacing = (maxHeight - 570.dp).coerceIn(24.dp, 80.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            OnboardingIllustration(step = step, diameter = diameter)
            Spacer(Modifier.height(imageToTextSpacing))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 109.dp).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = content.title,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    text = description,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (step == OnboardingStep.Introduction || step == OnboardingStep.Destination) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.ringoutColors.onboardingDescriptionContent
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = if (step == OnboardingStep.Interval) 25.sp else 19.2.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

@Preview(name = "Dark page", widthDp = 402, heightDp = 742)
@Composable
private fun OnboardingPagePreview() {
    RingoutTheme(ThemeMode.Dark) { OnboardingPage(OnboardingStep.Destination) }
}

@Preview(name = "Light page", widthDp = 402, heightDp = 742)
@Composable
private fun OnboardingPageLightPreview() {
    RingoutTheme(ThemeMode.Light) { OnboardingPage(OnboardingStep.Destination) }
}
