package com.joon.ringout.presentation.destination.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DestinationShortcutRow(
    destinations: List<SavedDestination>,
    onManagementClick: () -> Unit,
    onDestinationClick: (SavedDestination) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DestinationShortcutRowHeight)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DestinationManagementButton(
            onClick = onManagementClick,
            enabled = enabled,
        )
        if (destinations.isNotEmpty()) {
            Spacer(Modifier.width(DestinationShortcutSpacing))
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .height(DestinationShortcutRowHeight),
                horizontalArrangement = Arrangement.spacedBy(DestinationShortcutSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(
                    items = destinations,
                    key = SavedDestination::id,
                ) { destination ->
                    DestinationShortcutButton(
                        destination = destination,
                        onClick = { onDestinationClick(destination) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationManagementButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(15.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(DestinationShortcutRowHeight)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "목적지 관리 열기",
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(DestinationShortcutHeight)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 4.dp,
                        color = colors.shadow,
                        offset = DpOffset(0.dp, 1.dp),
                    ),
                )
                .background(colors.surface, shape)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterHorizontally,
            ),
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

@Composable
private fun DestinationShortcutButton(
    destination: SavedDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(DestinationShortcutCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(DestinationShortcutRowHeight)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "${destination.name} 목적지 선택",
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(DestinationShortcutHeight)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 4.dp,
                        color = colors.shadow,
                        offset = DpOffset(0.dp, 1.dp),
                    ),
                )
                .background(colors.surface, shape)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = destination.name.toDestinationShortcutLabel(),
                color = colors.itemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

private val DestinationShortcutRowHeight = 48.dp
private val DestinationShortcutHeight = 30.dp
private val DestinationShortcutCornerRadius = 15.dp
private val DestinationShortcutSpacing = 10.dp
internal const val DestinationShortcutVisibleNameLength = 5

internal fun String.toDestinationShortcutLabel(): String =
    if (length <= DestinationShortcutVisibleNameLength) {
        this
    } else {
        take(DestinationShortcutVisibleNameLength) + "…"
    }

private val DestinationShortcutPreviewItems = listOf(
    SavedDestination(
        id = 1L,
        name = "런닝",
        address = "서울 중구 세종대로 110",
        latitude = 37.5665851,
        longitude = 126.9782038,
    ),
    SavedDestination(
        id = 2L,
        name = "공부하러가야지정말",
        address = "서울 서초구 반포대로 201",
        latitude = 37.5001,
        longitude = 127.0001,
    ),
    SavedDestination(
        id = 3L,
        name = "헬스장",
        address = "서울 강남구 테헤란로 123",
        latitude = 37.5012,
        longitude = 127.0396,
    ),
)

@Preview(widthDp = 402, heightDp = 70)
@Composable
private fun DestinationShortcutRowPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DestinationShortcutRow(
                destinations = DestinationShortcutPreviewItems,
                onManagementClick = {},
                onDestinationClick = {},
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 70)
@Composable
private fun DestinationShortcutRowEmptyPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DestinationShortcutRow(
                destinations = emptyList(),
                onManagementClick = {},
                onDestinationClick = {},
            )
        }
    }
}
