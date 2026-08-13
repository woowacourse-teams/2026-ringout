package com.joon.ringout.presentation.nickname.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun NicknameValidationList(
    isLengthValid: Boolean,
    hasOnlyAllowedCharacters: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NicknameValidationItemSpacing),
    ) {
        NicknameValidationItem(
            text = "1-10자 이내",
            isValid = isLengthValid,
        )
        NicknameValidationItem(
            text = "한글, 영문, 숫자 사용 가능",
            isValid = hasOnlyAllowedCharacters,
        )
    }
}

@Composable
private fun NicknameValidationItem(
    text: String,
    isValid: Boolean,
) {
    val colors = nicknameChangeColors()
    val stateText = if (isValid) "충족" else "미충족"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$text, $stateText"
            },
        horizontalArrangement = Arrangement.spacedBy(NicknameValidationIconSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(
                if (isValid) {
                    NicknameChangeValidIconResource
                } else {
                    NicknameChangeInvalidIconResource
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(NicknameValidationIconSize),
        )
        Text(
            text = text,
            color = if (isValid) colors.success else colors.error,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 16.8.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

private val NicknameValidationItemSpacing = 8.dp
private val NicknameValidationIconSpacing = 8.dp
private val NicknameValidationIconSize = 16.dp

@Preview(name = "Nickname validation - Valid", widthDp = 402)
@Composable
private fun NicknameValidationValidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        NicknameValidationList(
            isLengthValid = true,
            hasOnlyAllowedCharacters = true,
            modifier = Modifier
                .background(nicknameChangeColors().background)
                .padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "Nickname validation - Invalid", widthDp = 402)
@Composable
private fun NicknameValidationInvalidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        NicknameValidationList(
            isLengthValid = true,
            hasOnlyAllowedCharacters = false,
            modifier = Modifier
                .background(nicknameChangeColors().background)
                .padding(horizontal = 20.dp),
        )
    }
}
