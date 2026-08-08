package com.joon.ringout.presentation.destination.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DestinationShortcutRow(
    onManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DestinationShortcutRowHeight)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        DestinationManagementButton(onClick = onManagementClick)
    }
}

@Composable
private fun DestinationManagementButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(15.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = DestinationManagementButtonMinWidth)
            .height(DestinationShortcutRowHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "목적지 관리 열기",
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = DestinationManagementButtonMinWidth)
                .height(30.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .background(colors.surface, shape)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(DestinationManagementEditIconResource),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "목적지 관리",
                color = colors.primaryAction,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

private val DestinationShortcutRowHeight = 46.dp
private val DestinationManagementButtonMinWidth = 77.dp

@Preview(widthDp = 402, heightDp = 70)
@Composable
private fun DestinationShortcutRowPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DestinationShortcutRow(onManagementClick = {})
        }
    }
}
