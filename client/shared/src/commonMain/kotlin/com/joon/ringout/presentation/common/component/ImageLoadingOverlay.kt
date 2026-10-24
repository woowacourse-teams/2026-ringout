package com.joon.ringout.presentation.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.joon.ringout.RingoutTheme

/** 부모 미리보기의 크기와 모양 안에 표시하며, 입력 차단은 호출 컴포넌트가 담당한다. */
@Composable
internal fun ImageLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp).semantics {
                contentDescription = "사진 처리 중"
                liveRegion = LiveRegionMode.Polite
            },
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@Preview
@Composable
private fun ImageLoadingOverlayPreview() {
    RingoutTheme {
        ImageLoadingOverlay(modifier = Modifier.size(140.dp))
    }
}
