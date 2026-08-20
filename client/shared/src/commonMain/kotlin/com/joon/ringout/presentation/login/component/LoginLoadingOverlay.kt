package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun LoginLoadingOverlay(modifier: Modifier = Modifier) {
    val colors = loginColors()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.loadingScrim)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .clearAndSetSemantics {
                contentDescription = "로그인 처리 중"
                stateDescription = "진행 중"
                liveRegion = LiveRegionMode.Polite
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colors.loadingIndicator,
            strokeWidth = 4.dp,
        )
    }
}

@Preview(name = "Login Loading Overlay", widthDp = 402, heightDp = 941)
@Composable
private fun LoginLoadingOverlayPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(loginColors().background),
        ) {
            LoginHero(Modifier.align(Alignment.Center))
            LoginLoadingOverlay()
        }
    }
}
