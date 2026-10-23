package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.OnboardingPermissionStatus
import com.joon.ringout.presentation.onboarding.OnboardingStep
import com.joon.ringout.presentation.onboarding.pageContent
import com.joon.ringout.ringoutColors

@Composable
internal fun OnboardingPermissionPage(
    step: OnboardingStep,
    status: OnboardingPermissionStatus,
    modifier: Modifier = Modifier,
) {
    val content = step.pageContent()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.ringoutColors.mainScreenBackground)
            .testTag("onboarding-page-${step.name}")
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 110.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
            text = content.description,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        status.explanation(step)?.let { explanation ->
            Text(
                text = explanation,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun OnboardingPermissionStatus.explanation(step: OnboardingStep): String? = when (this) {
    OnboardingPermissionStatus.SettingsRequired -> if (step == OnboardingStep.LocationPermission) {
        "설정에서 위치 권한을 허용하고\n정확한 위치를 켜주세요."
    } else {
        "권한이 꺼져 있어요. 설정에서 권한을 허용해주세요."
    }
    OnboardingPermissionStatus.Restricted -> "기기 설정에서 권한 사용이 제한되어 있어요. 제한 설정을 확인해주세요."
    OnboardingPermissionStatus.ServicesDisabled -> "기기의 위치 서비스가 꺼져 있어요. 설정에서 위치 서비스를 켜주세요."
    OnboardingPermissionStatus.Unavailable -> "권한을 확인하지 못했어요. 다시 시도해주세요."
    else -> null
}

@Preview(name = "Location permission dark", widthDp = 402, heightDp = 742)
@Preview(name = "Location permission compact", widthDp = 320, heightDp = 420, fontScale = 2f)
@Composable
private fun OnboardingPermissionPagePreview() {
    RingoutTheme(ThemeMode.Dark) {
        OnboardingPermissionPage(OnboardingStep.LocationPermission, OnboardingPermissionStatus.Requestable)
    }
}

@Preview(name = "Notification permission light", widthDp = 402, heightDp = 742)
@Composable
private fun OnboardingPermissionPageLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        OnboardingPermissionPage(OnboardingStep.NotificationPermission, OnboardingPermissionStatus.Requestable)
    }
}

@Preview(name = "Permission denied", widthDp = 320, heightDp = 420, fontScale = 2f)
@Composable
private fun OnboardingPermissionPageDeniedPreview() {
    RingoutTheme(ThemeMode.Dark) {
        OnboardingPermissionPage(OnboardingStep.LocationPermission, OnboardingPermissionStatus.SettingsRequired)
    }
}
