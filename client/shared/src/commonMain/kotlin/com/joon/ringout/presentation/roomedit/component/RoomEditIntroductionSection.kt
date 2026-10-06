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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomedit.model.RoomEditDescriptionValidation
import com.joon.ringout.presentation.roomedit.model.validateRoomEditDescription
import kotlinx.coroutines.launch

@Composable
internal fun RoomEditIntroductionSection(
    value: String,
    validation: RoomEditDescriptionValidation,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val colors = roomEditColors()
    val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val validationMessage = when {
        validation.isValid -> null
        !validation.isLengthValid -> "모임 소개는 최대 300자까지 입력할 수 있어요"
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "모임 소개",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(colors.inputSurface, fieldShape)
                .border(1.dp, colors.idleBorder, fieldShape)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
                .semantics {
                    contentDescription = "모임 소개 입력"
                    if (value.isNotEmpty() && validationMessage != null) error(validationMessage)
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                    innerTextField()
                }
            },
        )
        RoomEditValidationIndicator(
            label = "최대 300글자",
            isSatisfied = validation.isValid,
            hasInput = value.isNotEmpty(),
        )
    }
}

@Preview(name = "모임 소개 수정 · 초기 · 다크")
@Composable
private fun RoomEditIntroductionSectionInitialPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditIntroductionSection(
            value = "함께 달리며 건강한 습관을 만들어요.",
            validation = validateRoomEditDescription("함께 달리며 건강한 습관을 만들어요."),
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "모임 소개 수정 · 유효 · 라이트")
@Composable
private fun RoomEditIntroductionSectionValidPreview() {
    val value = "함께 달리며 건강한 습관을 만들어요."
    RingoutTheme(ThemeMode.Light) {
        RoomEditIntroductionSection(
            value = value,
            validation = validateRoomEditDescription(value),
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
