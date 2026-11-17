package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.arrival.component.ArrivalSeedAnimation

@Composable
internal fun HomeForestButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(78.dp)
            .semantics(mergeDescendants = true) { contentDescription = "숲 키우기" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        ArrivalSeedAnimation(modifier = Modifier.size(78.dp))
    }
}

@Preview
@Composable
private fun HomeForestButtonPreview() {
    RingoutTheme { HomeForestButton(onClick = {}) }
}
