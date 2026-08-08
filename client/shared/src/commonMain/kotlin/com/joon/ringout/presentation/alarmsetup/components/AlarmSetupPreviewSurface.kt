package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AlarmSetupPreviewSurface(content: @Composable () -> Unit) {
    val colors = alarmSetupColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(20.dp),
    ) {
        content()
    }
}
