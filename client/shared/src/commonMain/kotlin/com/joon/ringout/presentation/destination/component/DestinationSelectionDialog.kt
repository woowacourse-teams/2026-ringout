package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.common.component.DialogWithoutRipple
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DestinationSelectionDialog(
    destinations: List<SavedDestination>,
    onDismissRequest: () -> Unit,
    onSelect: (SavedDestination) -> Unit,
    onFindInMap: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DestinationSelectionDialogContent(
            destinations = destinations,
            onDismissRequest = onDismissRequest,
            onSelect = onSelect,
            onFindInMap = onFindInMap,
            modifier = modifier.padding(horizontal = 21.dp),
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun DestinationSelectionDialogContent(
    destinations: List<SavedDestination>,
    onDismissRequest: () -> Unit,
    onSelect: (SavedDestination) -> Unit,
    onFindInMap: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val colors = DestinationManagementPalette
    val listState = rememberLazyListState()
    val showScrollbar = destinations.size > 3
    DialogWithoutRipple {
        Column(
            modifier = modifier.widthIn(max = 360.dp).fillMaxWidth()
                .clip(RoundedCornerShape(15.dp)).background(colors.surface).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "저장된 목적지",
                    color = colors.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Box(
                    modifier = Modifier.size(40.dp).clickable(role = Role.Button, onClick = onDismissRequest),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Image(
                        painter = painterResource(DestinationManagementCloseIconResource),
                        contentDescription = "저장된 목적지 닫기",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(194.dp), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = "저장된 목적지 불러오는 중" },
                        color = colors.primaryAction,
                    )
                    errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("목적지를 불러오지 못했어요.", color = colors.itemText)
                        TextButton(onClick = onRetry) { Text("다시 시도", color = colors.primaryAction) }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(194.dp)
                                .semantics { contentDescription = "저장된 목적지 목록" },
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(destinations, key = SavedDestination::id) { destination ->
                                DestinationSelectionRow(destination, onSelect = { onSelect(destination) })
                            }
                        }
                        if (showScrollbar) {
                            DestinationSearchScrollbar(
                                state = listState,
                                // 행의 기존 오른쪽 패딩 안에서만 터치를 처리한다.
                                modifier = Modifier.align(Alignment.CenterEnd).width(10.dp),
                                scrollDescription = "저장된 목적지 스크롤",
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(51.dp)
                    .clip(RoundedCornerShape(10.dp)).background(colors.primaryAction)
                    .clickable(role = Role.Button, onClick = onFindInMap),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "지도에서 찾기",
                    color = colors.surface,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun DestinationSelectionRow(
    destination: SavedDestination,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    Row(
        modifier = modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = destination.name,
                color = colors.itemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = destination.address.removePrefix("대한민국 ").trimStart(),
                color = colors.secondaryAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        // 관리 플로우는 다음 단계에서 연결한다.
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text("관리", color = colors.secondaryAction, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "${destination.name} 선택" }
                .clickable(role = Role.Button, onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            Text("선택", color = colors.primaryAction, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Preview
@Composable
private fun DestinationSelectionDialogContentPreview() {
    RingoutTheme {
        DestinationSelectionDialogContent(
            destinations = listOf("370-41", "헬스장", "러닝").mapIndexed { index, name ->
                SavedDestination(index.toLong() + 1, name, "대한민국 경기도 수원시 팔달구", 37.28, 127.01)
            },
            onDismissRequest = {}, onSelect = {}, onFindInMap = {},
        )
    }
}

@Preview
@Composable
private fun DestinationSelectionDialogPreview() {
    RingoutTheme {
        DestinationSelectionDialog(emptyList(), {}, {}, {}, isLoading = true)
    }
}

@Preview
@Composable
private fun DestinationSelectionRowPreview() {
    RingoutTheme {
        DestinationSelectionRow(
            destination = SavedDestination(1, "헬스장", "대한민국 경기도 수원시", 37.28, 127.01),
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun DestinationSelectionDialogErrorPreview() {
    RingoutTheme {
        DestinationSelectionDialogContent(emptyList(), {}, {}, {}, errorMessage = "조회 실패")
    }
}
