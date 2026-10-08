package com.joon.ringout.presentation.roomlist.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomListMessage(
    title: String,
    description: String? = null,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.ringoutColors.elevatedSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("다시 시도", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomListMessagePreviewContent(
    title: String,
    description: String? = null,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.ringoutColors.mainScreenBackground)
                .padding(20.dp),
        ) {
            RoomListMessage(
                title = title,
                description = description,
                isLoading = isLoading,
                onRetry = onRetry,
            )
        }
    }
}

@Preview(name = "모임 목록 로딩", widthDp = 402, heightDp = 180, showBackground = true)
@Composable
private fun RoomListMessageLoadingPreview() {
    RoomListMessagePreviewContent(
        title = "모임을 불러오는 중이에요",
        isLoading = true,
    )
}

@Preview(name = "모임 목록 오류", widthDp = 402, heightDp = 180, showBackground = true)
@Composable
private fun RoomListMessageErrorPreview() {
    RoomListMessagePreviewContent(
        title = "모임을 불러오지 못했어요",
        description = "잠시 후 다시 시도해 주세요.",
        onRetry = {},
    )
}

@Preview(name = "모임 목록 비어 있음", widthDp = 402, heightDp = 180, showBackground = true)
@Composable
private fun RoomListMessageEmptyPreview() {
    RoomListMessagePreviewContent(
        title = "아직 등록된 모임이 없어요",
        description = "새 모임을 만들어 함께할 사람을 찾아보세요.",
    )
}
