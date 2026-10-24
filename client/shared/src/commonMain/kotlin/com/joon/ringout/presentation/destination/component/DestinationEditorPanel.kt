package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DestinationEditorPanel(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    canSave: Boolean = true,
    isSaving: Boolean = false,
) {
    val colors = DestinationManagementPalette
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(15.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.5.dp)
                .fillMaxWidth().height(226.dp)
                .clip(shape).background(colors.surface).border(1.dp, colors.border, shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "목적지 수정",
                    color = colors.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = !isSaving,
                            role = Role.Button,
                            onClick = {
                                focusManager.clearFocus()
                                onDelete()
                            },
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Image(
                        painter = painterResource(DestinationManagementDeleteIconResource),
                        contentDescription = "목적지 삭제",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            BasicTextField(
                value = nickname,
                onValueChange = { onNicknameChange(applyDestinationNicknameInput(nickname, it)) },
                enabled = !isSaving,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.itemText,
                ),
                cursorBrush = SolidColor(colors.primaryAction),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.padding(10.dp).fillMaxWidth().height(57.dp)
                    .border(1.dp, colors.primaryAction, RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "목적지 별명" },
                decorationBox = { field ->
                    Box(Modifier.padding(10.dp), contentAlignment = Alignment.CenterStart) {
                        if (nickname.isEmpty()) Text("목적지 별명", color = colors.secondaryAction)
                        field()
                    }
                },
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 290.dp).fillMaxWidth().height(51.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canSave && !isSaving) colors.primaryAction else colors.secondaryAction)
                        .clickable(enabled = canSave && !isSaving, role = Role.Button, onClick = {
                            focusManager.clearFocus()
                            onSave()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isSaving) "처리 중…" else "저장",
                        color = colors.surface,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        // 카드와 별개로 시스템 내비게이션 바의 배경도 동일한 표면색을 유지한다.
        Spacer(Modifier.fillMaxWidth().background(colors.surface).windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Preview
@Composable
private fun DestinationEditorPanelPreview() {
    RingoutTheme { DestinationEditorPanel("헬스장", {}, {}, {}) }
}
