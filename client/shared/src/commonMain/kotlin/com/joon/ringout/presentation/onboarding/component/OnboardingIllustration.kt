package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.OnboardingStep
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.onboarding_departure
import ringout.shared.generated.resources.onboarding_destination
import ringout.shared.generated.resources.onboarding_interval
import ringout.shared.generated.resources.onboarding_introduction

@Composable
internal fun OnboardingIllustration(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
    diameter: Dp = 312.dp,
) {
    val isLight = LocalRingoutThemeMode.current == ThemeMode.Light
    val resource = when (step) {
        OnboardingStep.Introduction -> Res.drawable.onboarding_introduction
        OnboardingStep.Destination -> Res.drawable.onboarding_destination
        OnboardingStep.Interval -> Res.drawable.onboarding_interval
        OnboardingStep.Departure -> Res.drawable.onboarding_departure
        OnboardingStep.LocationPermission, OnboardingStep.NotificationPermission -> return
    }
    // Figma uses a smaller introduction image in light mode and a cropped departure image.
    val imageWidth = when {
        step == OnboardingStep.Introduction && isLight -> diameter * (220f / 312f)
        step == OnboardingStep.Departure -> diameter * (280f / 312f)
        else -> diameter
    }
    val imageHeight = if (step == OnboardingStep.Departure) {
        diameter * (321f / 312f)
    } else {
        imageWidth
    }

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.ringoutColors.onboardingIllustrationBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.requiredSize(imageWidth, imageHeight).clipToBounds()) {
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.FillHeight,
            )
        }
    }
}

@Preview(name = "Dark illustration", widthDp = 312, heightDp = 312)
@Composable
private fun OnboardingIllustrationPreview() {
    RingoutTheme(ThemeMode.Dark) {
        OnboardingIllustration(OnboardingStep.Introduction)
    }
}

@Preview(name = "Light illustration", widthDp = 312, heightDp = 312)
@Composable
private fun OnboardingIllustrationLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        OnboardingIllustration(OnboardingStep.Introduction)
    }
}
