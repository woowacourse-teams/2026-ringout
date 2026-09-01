package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun HomeLoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Dark loading Home", widthDp = 402, heightDp = 941)
@Composable
private fun DarkHomeLoadingStatePreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        HomeLoadingState()
    }
}
