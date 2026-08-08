package com.joon.ringout.presentation.destination.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.destination.DestinationSelection
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DestinationManagementDialog(
    destinations: List<DestinationSelection>,
    onDismissRequest: () -> Unit,
    onEditClick: (DestinationSelection) -> Unit,
    onDeleteClick: (DestinationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val dialogInteractionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    onClickLabel = "목적지 관리 닫기",
                    onClick = onDismissRequest,
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 21.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
                    .clickable(
                        interactionSource = dialogInteractionSource,
                        indication = null,
                        onClick = {},
                    ),
            )
            DestinationManagementDialogContent(
                destinations = destinations,
                onDismissRequest = onDismissRequest,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier.semantics {
                    paneTitle = "목적지 관리"
                },
            )
        }
    }
}

@Composable
internal fun DestinationManagementDialogContent(
    destinations: List<DestinationSelection>,
    onDismissRequest: () -> Unit,
    onEditClick: (DestinationSelection) -> Unit,
    onDeleteClick: (DestinationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(15.dp)

    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(305.dp)
            .clip(shape)
            .background(colors.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DestinationManagementHeader(onDismissRequest = onDismissRequest)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(destinations) { destination ->
                DestinationManagementRow(
                    destination = destination,
                    onEditClick = { onEditClick(destination) },
                    onDeleteClick = { onDeleteClick(destination) },
                )
            }
        }
    }
}

@Composable
private fun DestinationManagementHeader(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "목적지 관리",
            color = colors.title,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Image(
                painter = painterResource(DestinationManagementCloseIconResource),
                contentDescription = "닫기",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun DestinationManagementRow(
    destination: DestinationSelection,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(51.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = destination.name,
                color = colors.itemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(
                        color = colors.shadow,
                        offset = Offset(0f, 1f),
                        blurRadius = 2f,
                    ),
                ),
            )
        }
        Row(
            modifier = Modifier
                .width(98.dp)
                .height(51.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DestinationManagementAction(
                text = "수정",
                color = colors.secondaryAction,
                onClick = onEditClick,
                contentDescription = "${destination.name} 수정",
            )
            DestinationManagementAction(
                text = "삭제",
                color = colors.primaryAction,
                onClick = onDeleteClick,
                contentDescription = "${destination.name} 삭제",
            )
        }
    }
}

@Composable
private fun DestinationManagementAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 44.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            maxLines = 1,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private val DestinationManagementPreviewItems = listOf(
    DestinationSelection(
        name = "런닝",
        address = "서울 중구 세종대로 110",
        latitude = 37.5665851,
        longitude = 126.9782038,
    ),
    DestinationSelection(
        name = "공부하러가야지",
        address = "서울 서초구 반포대로 201",
        latitude = 37.5001000,
        longitude = 127.0001000,
    ),
    DestinationSelection(
        name = "헬스장을가보아요",
        address = "서울 강남구 테헤란로 123",
        latitude = 37.5012000,
        longitude = 127.0396000,
    ),
)

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun DestinationManagementDialogPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DestinationManagementDialog(
                destinations = DestinationManagementPreviewItems,
                onDismissRequest = {},
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun DestinationManagementDialogEmptyPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DestinationManagementDialog(
                destinations = emptyList(),
                onDismissRequest = {},
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}
