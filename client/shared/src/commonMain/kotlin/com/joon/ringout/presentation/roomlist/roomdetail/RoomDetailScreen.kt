package com.joon.ringout.presentation.roomlist.roomdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.component.ConfirmationDialog
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailBackButton
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailHero
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailInformation
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailJoinButton
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailPreviewRoom
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomDetailScreen(
    room: RoomUiModel,
    authSessionState: AuthSessionState,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onJoinRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoginDialogVisible by rememberSaveable(room.id) { mutableStateOf(false) }
    val joinAction = roomJoinAction(room, authSessionState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            RoomDetailHero(room = room, onBackClick = onBackClick)
            RoomDetailInformation(room)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            RoomDetailJoinButton(
                isJoined = room.isJoined,
                enabled = joinAction != RoomJoinAction.Disabled,
                onClick = {
                    when (joinAction) {
                        RoomJoinAction.Disabled -> Unit
                        RoomJoinAction.RequestLogin -> isLoginDialogVisible = true
                        RoomJoinAction.Join -> onJoinRoom(room.id)
                    }
                },
            )
        }
    }

    if (isLoginDialogVisible) {
        ConfirmationDialog(
            title = "로그인이 필요해요",
            description = "모임 기능을 사용하려면 로그인이 필요해요.\n지금 로그인을 하러 가볼까요?",
            confirmLabel = "로그인 하러 가기",
            confirmColor = MaterialTheme.colorScheme.primary,
            onDismiss = { isLoginDialogVisible = false },
            onConfirm = {
                isLoginDialogVisible = false
                onLoginClick()
            },
            cancelLabel = "지금은 안해요",
        )
    }
}

@Composable
internal fun RoomDetailStatusScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        RoomDetailBackButton(onClick = onBackClick)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isLoading -> "모임 정보를 불러오는 중이에요."
                        errorMessage != null -> errorMessage
                        else -> "모임을 찾을 수 없어요."
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!isLoading && errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "다시 시도",
                        modifier = Modifier.clickable(role = Role.Button, onClick = onRetry),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Preview(name = "비로그인 · 로그인 필요", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        RoomDetailScreen(
            room = RoomDetailPreviewRoom,
            authSessionState = AuthSessionState.Unauthenticated,
            onBackClick = {},
            onLoginClick = {},
            onJoinRoom = {},
        )
    }
}

@Preview(name = "이미 가입한 모임", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailScreenJoinedPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        RoomDetailScreen(
            room = RoomDetailPreviewRoom.copy(isJoined = true),
            authSessionState = AuthSessionState.Authenticated,
            onBackClick = {},
            onLoginClick = {},
            onJoinRoom = {},
        )
    }
}

@Preview(name = "로그인 · 가입 가능", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        RoomDetailScreen(
            room = RoomDetailPreviewRoom,
            authSessionState = AuthSessionState.Authenticated,
            onBackClick = {},
            onLoginClick = {},
            onJoinRoom = {},
        )
    }
}

@Preview(widthDp = 360, heightDp = 700)
@Composable
private fun RoomDetailScreenLongDescriptionPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        RoomDetailScreen(
            room = RoomDetailPreviewRoom.copy(
                description = RoomDetailPreviewRoom.description.repeat(8),
            ),
            authSessionState = AuthSessionState.Authenticated,
            onBackClick = {},
            onLoginClick = {},
            onJoinRoom = {},
        )
    }
}

@Preview(name = "모임 정보 로딩", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailStatusScreenLoadingPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        RoomDetailStatusScreen(
            isLoading = true,
            errorMessage = null,
            onBackClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "모임 정보 오류", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailStatusScreenErrorPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        RoomDetailStatusScreen(
            isLoading = false,
            errorMessage = "모임 정보를 불러오지 못했어요.",
            onBackClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "모임을 찾을 수 없음", widthDp = 402, heightDp = 941)
@Composable
private fun RoomDetailStatusScreenNotFoundPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        RoomDetailStatusScreen(
            isLoading = false,
            errorMessage = null,
            onBackClick = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun RoomDetailLoginDialogPreview() {
    RingoutTheme {
        ConfirmationDialog(
            title = "로그인이 필요해요",
            description = "모임 기능을 사용하려면 로그인이 필요해요.\n지금 로그인을 하러 가볼까요?",
            confirmLabel = "로그인 하러 가기",
            confirmColor = MaterialTheme.colorScheme.primary,
            onDismiss = {},
            onConfirm = {},
            cancelLabel = "지금은 안해요",
        )
    }
}
