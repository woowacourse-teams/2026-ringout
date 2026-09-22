package com.joon.ringout.presentation.common.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

/** 플랫폼 다이얼로그의 배경 딤과 입력 차단을 사용하는 공통 로딩 표시. */
@Composable
internal fun LoadingOverlay(
    modifier: Modifier = Modifier,
    message: String = "처리 중",
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = message
                    liveRegion = LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
        }
    }
}

@Preview
@Composable
private fun LoadingOverlayPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoadingOverlay()
    }
}
