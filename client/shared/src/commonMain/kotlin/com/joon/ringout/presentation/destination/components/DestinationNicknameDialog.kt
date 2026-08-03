package com.joon.ringout.presentation.destination.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

internal const val DestinationNicknameMaxLength = 10

private val NicknameDialogScrim = Color.Black.copy(alpha = 0.5f)
private val NicknameDialogSurface = Color.White
private val NicknameDialogPrimary = Color.Black
private val NicknameDialogSecondary = Color(0xFF8C8C8C)
private val NicknameDialogInput = Color(0xFFF5F5F5)
private val NicknameDialogOrange = Color(0xFFFF6D2E)
private val NicknameDialogShape = RoundedCornerShape(24.dp)

@Composable
internal fun DestinationNicknameDialog(
    address: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nickname by remember(address) { mutableStateOf("") }
    val normalizedNickname = normalizeDestinationNickname(nickname)
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(NicknameDialogScrim)
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    onClickLabel = "목적지 별명 설정 닫기",
                    onClick = onDismissRequest,
                ),
        )

        DestinationNicknameDialogContent(
            address = address,
            nickname = nickname,
            isSaveEnabled = normalizedNickname != null,
            onNicknameChange = {
                nickname = limitDestinationNicknameInput(it)
            },
            onSave = {
                normalizedNickname?.let(onSave)
            },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 17.dp)
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = {},
                ),
        )
    }
}

@Composable
internal fun DestinationNicknameDialogContent(
    address: String,
    nickname: String,
    isSaveEnabled: Boolean,
    onNicknameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 368.dp)
            .fillMaxWidth()
            .height(245.dp)
            .shadow(8.dp, NicknameDialogShape, clip = false)
            .clip(NicknameDialogShape)
            .background(NicknameDialogSurface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "목적지 별명 설정",
                color = NicknameDialogPrimary,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "현재 선택된 주소: $address",
                color = NicknameDialogSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }

        BasicTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NicknameDialogInput)
                .padding(horizontal = 16.dp)
                .semantics {
                    contentDescription = "목적지 별명"
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = NicknameDialogPrimary,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
            ),
            singleLine = true,
            cursorBrush = SolidColor(NicknameDialogOrange),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isSaveEnabled) onSave()
                },
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (nickname.isEmpty()) {
                        Text(
                            text = "헬스장, 집 앞 공원",
                            color = NicknameDialogSecondary,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NicknameDialogOrange)
                .clickable(
                    enabled = isSaveEnabled,
                    role = Role.Button,
                    onClickLabel = "목적지 별명 저장",
                    onClick = onSave,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "저장",
                color = Color.White,
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

internal fun limitDestinationNicknameInput(value: String): String =
    value.take(DestinationNicknameMaxLength)

internal fun normalizeDestinationNickname(value: String): String? =
    value.trim().takeIf {
        it.isNotEmpty() && value.length <= DestinationNicknameMaxLength
    }

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun DestinationNicknameDialogPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8DDD8)),
        ) {
            DestinationNicknameDialog(
                address = "서울 서초구 반포동 748-10",
                onDismissRequest = {},
                onSave = {},
            )
        }
    }
}
