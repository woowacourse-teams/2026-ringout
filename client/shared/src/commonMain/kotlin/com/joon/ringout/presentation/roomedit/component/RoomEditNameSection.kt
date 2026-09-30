package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomcreate.RoomNameValidation
import com.joon.ringout.presentation.roomcreate.validateRoomName
import kotlinx.coroutines.launch

@Composable
internal fun RoomEditNameSection(
    originalPlaceholder: String,
    value: String,
    validation: RoomNameValidation,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val colors = roomEditColors()
    val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "모임 이름",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.inputSurface, fieldShape)
                .border(1.dp, colors.idleBorder, fieldShape)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
                .semantics {
                    contentDescription = "모임 이름 입력"
                    if (value.isNotEmpty() && !validation.isValid) {
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
                imeAction = ImeAction.Next,
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = originalPlaceholder,
                            color = colors.placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RoomEditValidationIndicator(
                label = "2~20자 이내",
                isSatisfied = validation.isLengthValid,
                hasInput = value.isNotEmpty(),
            )
            RoomEditValidationIndicator(
                label = "한글, 영문, 숫자 사용 가능",
                isSatisfied = validation.hasOnlyAllowedCharacters,
                hasInput = value.isNotEmpty(),
            )
        }
    }
}

@Preview(name = "모임 이름 수정 · 초기 · 라이트")
@Composable
private fun RoomEditNameSectionInitialPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditNameSection(
            originalPlaceholder = "아침러닝",
            value = "",
            validation = validateRoomName(""),
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "모임 이름 수정 · 유효 · 다크")
@Composable
private fun RoomEditNameSectionValidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditNameSection(
            originalPlaceholder = "아침러닝",
            value = "새러닝모임",
            validation = validateRoomName("새러닝모임"),
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
