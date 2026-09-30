package com.joon.ringout.presentation.profilechange.component.profileimage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.profilechange.component.profileChangeColors

@Composable
internal fun ProfileImageSizeLimitDialog(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = profileChangeColors()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 332.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.dialogBackground)
                    .padding(10.dp)
                    .semantics { paneTitle = "프로필 이미지 용량 안내" },
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "5MB 이하의 사진을 골라주세요.",
                        modifier = Modifier.semantics { heading() },
                        color = colors.dialogText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(51.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primaryAction)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "프로필 이미지 용량 안내 확인",
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

@Preview(name = "Profile image size limit - Light", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileImageSizeLimitDialogLightPreview() {
    ProfileImageSizeLimitDialogPreview(themeMode = ThemeMode.Light)
}

@Preview(name = "Profile image size limit - Dark", widthDp = 402, heightDp = 941)
@Composable
private fun ProfileImageSizeLimitDialogDarkPreview() {
    ProfileImageSizeLimitDialogPreview(themeMode = ThemeMode.Dark)
}

@Composable
private fun ProfileImageSizeLimitDialogPreview(themeMode: ThemeMode) {
    RingoutTheme(themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ProfileImageSizeLimitDialog(onConfirm = {})
        }
    }
}
