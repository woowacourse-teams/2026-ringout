package com.joon.ringout.presentation.termsagreement.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
fun AgreementCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.ringoutColors.elevatedSurface
    }
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = "약관 동의"
                stateDescription = if (checked) "동의함" else "동의하지 않음"
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(containerColor, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = MaterialTheme.ringoutColors.primaryActionContent,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Preview(name = "Agreement checkbox unchecked")
@Composable
private fun AgreementCheckboxUncheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AgreementCheckbox(checked = false, onCheckedChange = {})
    }
}

@Preview(name = "Agreement checkbox checked")
@Composable
private fun AgreementCheckboxCheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AgreementCheckbox(checked = true, onCheckedChange = {})
    }
}
