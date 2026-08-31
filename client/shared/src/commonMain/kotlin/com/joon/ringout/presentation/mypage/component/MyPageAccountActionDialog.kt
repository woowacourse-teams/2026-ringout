package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction

@Composable
fun MyPageAccountActionDialog(
    action: MyPageAccountAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyPageAccountActionDialogPalette
    val content = action.dialogContent
    val shape = RoundedCornerShape(DialogCornerRadius)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = DialogScreenPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = DialogMaxWidth)
                    .fillMaxWidth()
                    .shadow(
                        elevation = DialogShadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.shadow,
                        spotColor = colors.shadow,
                    )
                    .clip(shape)
                    .background(colors.surface)
                    .padding(DialogContentPadding)
                    .semantics {
                        paneTitle = "${content.title} 확인"
                    },
                verticalArrangement = Arrangement.spacedBy(DialogContentSpacing),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DialogInfoSpacing),
                ) {
                    Text(
                        text = content.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() },
                        color = colors.title,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = content.description,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.description,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 19.2.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .widthIn(max = ActionRowMaxWidth)
                        .fillMaxWidth()
                        .height(ActionTouchHeight),
                    horizontalArrangement = Arrangement.spacedBy(ActionButtonSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyPageAccountActionButton(
                        text = "취소",
                        containerColor = colors.cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(CancelButtonWeight),
                    )
                    MyPageAccountActionButton(
                        text = content.confirmLabel,
                        containerColor = when (action) {
                            MyPageAccountAction.Logout -> colors.logout
                            MyPageAccountAction.Withdraw -> colors.withdraw
                        },
                        onClick = onConfirm,
                        modifier = Modifier.weight(ConfirmButtonWeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyPageAccountActionButton(
    text: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ActionCornerRadius)

    Box(
        modifier = modifier
            .height(ActionTouchHeight)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ActionVisualHeight)
                .shadow(
                    elevation = ActionShadowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = MyPageAccountActionDialogPalette.actionShadow,
                    spotColor = MyPageAccountActionDialogPalette.actionShadow,
                )
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = MyPageAccountActionDialogPalette.actionContent,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 19.2.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

private data class MyPageAccountActionDialogContent(
    val title: String,
    val description: String,
    val confirmLabel: String,
)

private val MyPageAccountAction.dialogContent: MyPageAccountActionDialogContent
    get() = when (this) {
        MyPageAccountAction.Logout -> MyPageAccountActionDialogContent(
            title = "로그아웃",
            description = "정말 로그아웃 하시겠습니까?",
            confirmLabel = "로그아웃",
        )

        MyPageAccountAction.Withdraw -> MyPageAccountActionDialogContent(
            title = "회원탈퇴",
            description = "회원 탈퇴 시 모든 정보가 삭제됩니다.\n정말 탈퇴하시겠습니까?",
            confirmLabel = "회원탈퇴",
        )
    }

private val DialogMaxWidth = 335.dp
private val DialogScreenPadding = 24.dp
private val DialogContentPadding = 24.dp
private val DialogContentSpacing = 20.dp
private val DialogInfoSpacing = 12.dp
private val DialogCornerRadius = 16.dp
private val DialogShadowElevation = 12.dp
private val ActionRowMaxWidth = 272.dp
private val ActionTouchHeight = 48.dp
private val ActionVisualHeight = 40.dp
private val ActionButtonSpacing = 10.dp
private val ActionCornerRadius = 12.dp
private val ActionShadowElevation = 1.5.dp
private const val CancelButtonWeight = 99f
private const val ConfirmButtonWeight = 162f

@Preview(name = "My Page logout dialog", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageLogoutDialogPreview() {
    MyPageAccountActionDialogPreview(MyPageAccountAction.Logout)
}

@Preview(name = "My Page withdraw dialog", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageWithdrawDialogPreview() {
    MyPageAccountActionDialogPreview(MyPageAccountAction.Withdraw)
}

@Composable
private fun MyPageAccountActionDialogPreview(action: MyPageAccountAction) {
    RingoutTheme(ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            MyPageAccountActionDialog(
                action = action,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }
}
