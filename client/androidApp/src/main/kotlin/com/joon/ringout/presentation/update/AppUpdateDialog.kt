package com.joon.ringout.presentation.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun AppUpdateDialog(
    onDismissRequest: () -> Unit,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .shadow(12.dp, AppUpdateDialogShape)
                .background(AppUpdateDialogColors.surface, AppUpdateDialogShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "업데이트 알림",
                color = AppUpdateDialogColors.title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "새로운 버전이 출시되었어요.\n개선된 기능을 사용하기 위해서\n업데이트 해주세요!",
                modifier = Modifier.padding(top = 12.dp),
                color = AppUpdateDialogColors.description,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 19.2.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .width(272.dp)
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppUpdateActionButton(
                    text = "취소",
                    backgroundColor = AppUpdateDialogColors.cancelButton,
                    onClick = onDismissRequest,
                    modifier = Modifier.width(99.dp),
                )
                AppUpdateActionButton(
                    text = "업데이트",
                    backgroundColor = AppUpdateDialogColors.updateButton,
                    onClick = onUpdateClick,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .width(162.dp),
                )
            }
        }
    }
}

@Composable
private fun AppUpdateActionButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .shadow(1.5.dp, AppUpdateButtonShape)
            .background(backgroundColor, AppUpdateButtonShape)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppUpdateDialogColors.buttonText,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                lineHeight = 19.2.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private object AppUpdateDialogColors {
    val surface = Color(0xFFFFFFFF)
    val title = Color(0xFF1F2937)
    val description = Color(0xFF6B7280)
    val cancelButton = Color(0xFF808080)
    val updateButton = Color(0xFFFF6D2E)
    val buttonText = Color(0xFFFFFFFF)
}

private val AppUpdateDialogShape = RoundedCornerShape(16.dp)
private val AppUpdateButtonShape = RoundedCornerShape(12.dp)

@Preview(showBackground = true, widthDp = 383, heightDp = 248)
@Composable
private fun AppUpdateDialogPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        Box(modifier = Modifier.size(width = 383.dp, height = 248.dp)) {
            AppUpdateDialog(
                onDismissRequest = {},
                onUpdateClick = {},
            )
        }
    }
}
