package com.joon.ringout.presentation.roommembermanagement.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.component.ConfirmationDialog
import com.joon.ringout.presentation.common.component.ConfirmationDialogLayout

@Composable
internal fun RoomMemberRemoveDialog(
    nickname: String,
    isRemoving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emphasisColor = MaterialTheme.colorScheme.primary
    val description = buildAnnotatedString {
        append("정말 ")
        withStyle(
            SpanStyle(
                color = emphasisColor,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(nickname)
        }
        append(" 님을\n모임에서 추방할까요?")
    }

    ConfirmationDialog(
        title = "회원 추방",
        description = description.text,
        confirmLabel = "추방하기",
        confirmColor = MaterialTheme.colorScheme.primary,
        cancelLabel = if (isRemoving) null else "취소",
        onDismiss = { if (!isRemoving) onDismiss() },
        onConfirm = onConfirm,
        confirmEnabled = !isRemoving,
        dismissOnBackPress = !isRemoving,
        dismissOnClickOutside = !isRemoving,
        modifier = modifier,
        layout = ConfirmationDialogLayout(
            maxWidth = 334.dp,
            screenPadding = 24.dp,
            actionRowMaxWidth = 284.dp,
            cancelButtonWeight = 112f,
            actionAlignment = Alignment.CenterHorizontally,
            titleMaxLines = Int.MAX_VALUE,
            descriptionMaxLines = Int.MAX_VALUE,
        ),
        annotatedDescription = description,
        content = if (isRemoving) {
            {
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(text = "회원을 추방하고 있어요.")
                }
            }
        } else {
            null
        },
    )
}

@Preview(name = "회원 추방 · 라이트")
@Composable
private fun RoomMemberRemoveDialogLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomMemberRemoveDialog(
            nickname = RoomMemberManagementPreviewMembers.first().nickname,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview
@Composable
private fun RoomMemberRemoveDialogPreview() {
    RingoutTheme {
        RoomMemberRemoveDialog(
            nickname = RoomMemberManagementPreviewMembers.first().nickname,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "긴 닉네임 · 큰 글씨", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Composable
private fun RoomMemberRemoveDialogLongNicknamePreview() {
    RingoutTheme {
        RoomMemberRemoveDialog(
            nickname = "아주긴닉네임으로모임에참여한회원입니다",
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "회원 추방 진행 중")
@Composable
private fun RoomMemberRemoveDialogProgressPreview() {
    RingoutTheme {
        RoomMemberRemoveDialog(
            nickname = RoomMemberManagementPreviewMembers.first().nickname,
            isRemoving = true,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
