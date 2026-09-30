package com.joon.ringout.presentation.roomcreate.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomcreate.RoomIntroductionMaxLength
import com.joon.ringout.presentation.roomcreate.RoomIntroductionValidation
import com.joon.ringout.presentation.roomcreate.validateRoomIntroduction
import kotlinx.coroutines.launch

@Composable
internal fun RoomCreateIntroductionSection(
    introduction: String,
    validation: RoomIntroductionValidation,
    showErrors: Boolean,
    onIntroductionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val colors = roomCreateColors()
    val fieldShape = RoundedCornerShape(12.dp)
    val showError = (showErrors || introduction.isNotEmpty()) && !validation.isValid
    val borderColor = when {
        validation.isValid -> colors.successBorder
        showError -> colors.error
        else -> colors.idleBorder
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "모임 소개를\n입력해주세요",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        BasicTextField(
            value = introduction,
            onValueChange = onIntroductionChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(colors.inputSurface, fieldShape)
                .border(1.dp, borderColor, fieldShape)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
                .semantics {
                    contentDescription = "모임 소개 입력"
                    if (showError) {
                        error(
                            if (!validation.isNotBlank) {
                                "모임 소개를 입력해주세요"
                            } else {
                                "모임 소개는 최대 ${RoomIntroductionMaxLength}자까지 입력할 수 있어요"
                            },
                        )
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
            ),
            cursorBrush = SolidColor(colors.error),
            minLines = 8,
            maxLines = 10,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (introduction.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "모임 소개",
                                color = colors.placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            )
                        }
                    }
                    innerTextField()
                }
            },
        )
        val status = when {
            validation.isValid -> "충족"
            showError -> "확인 필요"
            else -> "입력 전"
        }
        val statusColor = if (validation.isValid) colors.success else colors.error
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "최대 ${RoomIntroductionMaxLength}자, ${validation.characterCount}자 입력, $status"
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    validation.isValid -> "✓"
                    showError -> "×"
                    else -> "·"
                },
                color = statusColor,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "최대 ${RoomIntroductionMaxLength}자",
                color = statusColor,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
    }
}

@Preview(name = "소개 조건 · 초과")
@Composable
private fun RoomCreateIntroductionSectionOverLimitPreview() {
    val introduction = "가".repeat(RoomIntroductionMaxLength + 1)
    RingoutTheme(ThemeMode.Dark) {
        RoomCreateIntroductionSection(
            introduction = introduction,
            validation = validateRoomIntroduction(introduction),
            showErrors = true,
            onIntroductionChange = {},
        )
    }
}

@Preview(name = "소개 조건 · 유효")
@Composable
private fun RoomCreateIntroductionSectionValidPreview() {
    val introduction = "매주 함께 걸으며 좋은 습관을 만들어요."
    RingoutTheme(ThemeMode.Light) {
        RoomCreateIntroductionSection(
            introduction = introduction,
            validation = validateRoomIntroduction(introduction),
            showErrors = false,
            onIntroductionChange = {},
        )
    }
}
