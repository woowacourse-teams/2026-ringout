package com.joon.ringout.presentation.login.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.component.LoadingOverlay

@Composable
internal fun LoginLoadingOverlay(modifier: Modifier = Modifier) {
    LoadingOverlay(modifier = modifier, message = "로그인 처리 중")
}

@Preview
@Composable
private fun LoginLoadingOverlayPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginLoadingOverlay()
    }
}
