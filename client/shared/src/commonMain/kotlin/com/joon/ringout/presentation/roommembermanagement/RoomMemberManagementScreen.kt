package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberManagementPreviewMembers
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberManagementStatus
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberManagementTopBar
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberRemoveDialog
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberRow

@Composable
internal fun RoomMemberManagementScreen(
    uiState: RoomMemberManagementUiState,
    onBackClick: () -> Unit,
    onRemoveMemberClick: (String) -> Unit,
    onDismissRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        RoomMemberManagementTopBar(onBackClick = onBackClick)
        when {
            uiState.isLoading -> RoomMemberManagementStatus(
                message = "회원 목록을 불러오는 중이에요.",
                modifier = Modifier.weight(1f),
                isLoading = true,
            )

            !uiState.canManageMembers -> RoomMemberManagementStatus(
                message = uiState.errorMessage ?: "방장만 회원을 관리할 수 있어요.",
                modifier = Modifier.weight(1f),
                onRetry = if (uiState.canRetryLoad) onRetry else null,
            )
            else -> Column(modifier = Modifier.weight(1f)) {
                val message = uiState.refreshErrorMessage ?: uiState.removeErrorMessage
                if (message != null || uiState.isRefreshingMembers) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        if (message != null) {
                            Text(
                                text = message,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "회원 목록을 갱신하고 있어요.",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (uiState.isRefreshingMembers) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                        if (uiState.refreshErrorMessage != null) {
                            TextButton(onClick = onRetry) {
                                Text("다시 시도")
                            }
                        }
                    }
                }
                if (uiState.members.isEmpty()) {
                    RoomMemberManagementStatus(
                        message = "표시할 회원이 없어요.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        items(uiState.members, key = { it.id }) { member ->
                            RoomMemberRow(
                                member = member,
                                canRemove = uiState.canRemoveMembers && !member.isOwner,
                                onRemoveClick = { onRemoveMemberClick(member.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.selectedMember
        ?.takeIf {
            uiState.canRemoveMembers && !it.isOwner && !uiState.isLoading &&
                !uiState.isRefreshingMembers && uiState.errorMessage == null
        }
        ?.let { member ->
            RoomMemberRemoveDialog(
                nickname = member.nickname,
                isRemoving = uiState.isRemoving,
                onDismiss = { if (!uiState.isRemoving) onDismissRemove() },
                onConfirm = onConfirmRemove,
            )
        }
}

@Preview(name = "회원 관리 · 다크", widthDp = 402, heightDp = 874)
@Composable
private fun RoomMemberManagementScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                members = RoomMemberManagementPreviewMembers,
                canManageMembers = true,
                canRemoveMembers = true,
            ),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "회원 관리 · 라이트", widthDp = 402, heightDp = 874)
@Composable
private fun RoomMemberManagementScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                members = RoomMemberManagementPreviewMembers,
                canManageMembers = true,
                canRemoveMembers = true,
            ),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "긴 닉네임 · 작은 화면 · 큰 글씨", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Composable
private fun RoomMemberManagementScreenCompactPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                members = RoomMemberManagementPreviewMembers.map {
                    it.copy(nickname = "아주긴닉네임으로모임에참여한회원입니다")
                },
                canManageMembers = true,
                canRemoveMembers = true,
            ),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "빈 목록", widthDp = 360, heightDp = 700)
@Composable
private fun RoomMemberManagementScreenEmptyPreview() {
    RingoutTheme {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(canManageMembers = true),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "목록 로딩", widthDp = 360, heightDp = 700)
@Composable
private fun RoomMemberManagementScreenLoadingPreview() {
    RingoutTheme {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(canManageMembers = true, isLoading = true),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "목록 오류", widthDp = 360, heightDp = 700)
@Composable
private fun RoomMemberManagementScreenErrorPreview() {
    RingoutTheme {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                canManageMembers = true,
                errorMessage = "회원 목록을 불러오지 못했어요.",
            ),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}

@Preview(name = "회원 관리 권한 없음", widthDp = 360, heightDp = 700)
@Composable
private fun RoomMemberManagementScreenNoPermissionPreview() {
    RingoutTheme {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                members = RoomMemberManagementPreviewMembers,
                canManageMembers = false,
            ),
            onBackClick = {},
            onRemoveMemberClick = {},
            onDismissRemove = {},
            onConfirmRemove = {},
        )
    }
}
