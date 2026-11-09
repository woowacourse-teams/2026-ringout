package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
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
import com.joon.ringout.presentation.common.component.DialogWithoutRipple

@Composable
internal fun MyPageLogoutBlockedDialog(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyPageAccountActionDialogPalette
    val shape = RoundedCornerShape(16.dp)

    DialogWithoutRipple {
        Dialog(
            onDismissRequest = onConfirm,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = modifier
                        .widthIn(max = 335.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = colors.shadow,
                            spotColor = colors.shadow,
                        )
                        .clip(shape)
                        .background(colors.surface)
                        .padding(24.dp)
                        .semantics { paneTitle = "로그아웃 차단 안내" },
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "로그아웃할 수 없어요",
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
                            text = "진행중인 미션을 종료한 뒤 시도해주세요.",
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.description,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.logout)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "로그아웃 차단 안내 확인",
                                onClick = onConfirm,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "확인",
                            color = colors.actionContent,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "My Page logout blocked", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageLogoutBlockedDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageLogoutBlockedDialog(
            onConfirm = {},
        )
    }
}
