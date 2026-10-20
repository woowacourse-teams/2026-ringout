package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.common.component.DialogWithoutRipple

@Composable
internal fun DestinationSaveDialog(
    onDismissRequest: () -> Unit,
    onSkipSave: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DestinationSaveDialogContent(onSkipSave, onSave, modifier.padding(horizontal = 13.dp))
    }
}

@Composable
internal fun DestinationSaveDialogContent(
    onSkipSave: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(16.dp)
    DialogWithoutRipple {
        Column(
            modifier = modifier
                .widthIn(max = 334.dp)
                .fillMaxWidth()
                .shadow(12.dp, shape, ambientColor = colors.shadow.copy(alpha = 0.13f), spotColor = colors.shadow.copy(alpha = 0.13f))
                .clip(shape)
                .background(colors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "목적지 저장",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.confirmTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "다음에 편하게 쓸 수 있도록\n목적지를 저장할까요?",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.confirmBody,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp, lineHeight = 19.2.sp, fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Row(
                modifier = Modifier.widthIn(max = 271.dp).fillMaxWidth().height(46.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("저장 안함" to onSkipSave, "저장하기" to onSave).forEachIndexed { index, (label, action) ->
                    Box(
                        modifier = Modifier
                            .weight(if (index == 0) 99f else 162f)
                            .height(40.dp)
                            .shadow(
                                1.5.dp,
                                RoundedCornerShape(12.dp),
                                ambientColor = colors.confirmActionShadow,
                                spotColor = colors.confirmActionShadow,
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (index == 0) colors.cancelAction else colors.primaryAction)
                            .clickable(role = Role.Button, onClick = action),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = colors.surface,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp, lineHeight = 19.2.sp, fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DestinationSaveDialogPreview() {
    RingoutTheme { DestinationSaveDialogContent({}, {}) }
}
