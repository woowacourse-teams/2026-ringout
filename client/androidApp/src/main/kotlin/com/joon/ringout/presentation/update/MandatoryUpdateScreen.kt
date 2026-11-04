package com.joon.ringout.presentation.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.update.AppUpdateAccess
import com.joon.ringout.presentation.update.component.MandatoryUpdateDialog

/** No app content is mounted behind the gate, including during local config initialization. */
@Composable
internal fun MandatoryUpdateScreen(
    uiState: MandatoryUpdateUiState,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {}
    Surface(modifier = modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            if (uiState.access == AppUpdateAccess.Required) {
                MandatoryUpdateDialog(
                    onUpdateClick = onUpdateClick,
                    hasStoreOpenError = uiState.hasStoreOpenError,
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Preview
@Composable
private fun MandatoryUpdateScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        MandatoryUpdateScreen(MandatoryUpdateUiState(AppUpdateAccess.Required), {})
    }
}

@Preview
@Composable
private fun MandatoryUpdateScreenLoadingPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        MandatoryUpdateScreen(MandatoryUpdateUiState(AppUpdateAccess.Checking), {})
    }
}
