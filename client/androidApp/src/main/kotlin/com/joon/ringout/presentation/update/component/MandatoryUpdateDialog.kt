package com.joon.ringout.presentation.update.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.component.ConfirmationDialog
import com.joon.ringout.presentation.common.component.ConfirmationDialogLayout

@Composable
internal fun MandatoryUpdateDialog(
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasStoreOpenError: Boolean = false,
) {
    ConfirmationDialog(
        title = "업데이트 공지",
        description = "새로운 버전이 출시되었습니다.\n스토어에서 업데이트 해주세요.",
        confirmLabel = "스토어로 이동",
        confirmColor = MaterialTheme.colorScheme.primary,
        cancelLabel = null,
        onDismiss = {},
        onConfirm = onUpdateClick,
        modifier = modifier,
        layout = ConfirmationDialogLayout(
            maxWidth = 334.dp,
            actionAlignment = Alignment.CenterHorizontally,
            singleActionMaxWidth = 162.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
        ),
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        content = if (hasStoreOpenError) {
            {
                Text(
                    text = "스토어를 열 수 없습니다.\n스토어 또는 브라우저를 확인하고\n다시 시도해주세요.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            null
        },
    )
}

@Preview(showBackground = true, widthDp = 383, heightDp = 248)
@Preview(name = "큰 글자 · 작은 화면", widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun MandatoryUpdateDialogPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        MandatoryUpdateDialog(onUpdateClick = {})
    }
}

@Preview(showBackground = true, widthDp = 383, heightDp = 360)
@Composable
private fun MandatoryUpdateDialogStoreErrorPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        MandatoryUpdateDialog(onUpdateClick = {}, hasStoreOpenError = true)
    }
}
