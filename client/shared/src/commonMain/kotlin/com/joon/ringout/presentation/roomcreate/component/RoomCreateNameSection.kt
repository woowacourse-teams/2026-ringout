package com.joon.ringout.presentation.roomcreate.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomcreate.RoomNameValidation
import com.joon.ringout.presentation.roomcreate.validateRoomName

@Composable
internal fun RoomCreateNameSection(
    name: String,
    validation: RoomNameValidation,
    showErrors: Boolean,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val colors = roomCreateColors()
    val fieldShape = RoundedCornerShape(12.dp)
    val fieldHasInput = name.isNotEmpty()
    val borderColor = when {
        validation.isValid -> colors.successBorder
        showErrors || fieldHasInput -> colors.error
        else -> colors.idleBorder
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "모임 이름을\n입력해주세요",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.inputSurface, fieldShape)
                .border(1.dp, borderColor, fieldShape)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
                .semantics {
                    contentDescription = "모임 이름 입력"
                    if ((showErrors || fieldHasInput) && !validation.isValid) {
                        error("모임 이름 조건을 확인해주세요")
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = "모임 이름",
                            color = colors.placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoomCreateCondition(
                label = "2~20자 이내",
                isSatisfied = validation.isLengthValid,
                showError = showErrors || fieldHasInput,
            )
            RoomCreateCondition(
                label = "한글, 영문, 숫자 사용 가능",
                isSatisfied = validation.hasOnlyAllowedCharacters,
                showError = showErrors || fieldHasInput,
            )
        }
    }
}

@Composable
private fun RoomCreateCondition(
    label: String,
    isSatisfied: Boolean,
    showError: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = when {
        isSatisfied -> "충족"
        showError -> "확인 필요"
        else -> "입력 전"
    }
    val colors = roomCreateColors()
    val isInvalid = !isSatisfied && showError
    val color = when {
        isSatisfied -> colors.success
        isInvalid -> colors.error
        else -> colors.error
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, $status"
        },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                isSatisfied -> "✓"
                isInvalid -> "×"
                else -> "·"
            },
            color = color,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Preview(name = "이름 조건 · 오류")
@Composable
private fun RoomCreateNameSectionErrorPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomCreateNameSection(
            name = "가",
            validation = validateRoomName("가"),
            showErrors = true,
            onNameChange = {},
        )
    }
}

@Preview(name = "이름 조건 · 유효")
@Composable
private fun RoomCreateNameSectionValidPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomCreateNameSection(
            name = " 가A2 ",
            validation = validateRoomName(" 가A2 "),
            showErrors = false,
            onNameChange = {},
        )
    }
}
