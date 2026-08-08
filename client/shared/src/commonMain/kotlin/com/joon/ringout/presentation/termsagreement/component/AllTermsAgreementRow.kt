package com.joon.ringout.presentation.termsagreement.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
fun AllTermsAgreementRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(
                color = MaterialTheme.ringoutColors.elevatedSurface,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgreementCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = "전체 동의",
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 13.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview(name = "All terms unchecked", widthDp = 360)
@Composable
private fun AllTermsAgreementRowUncheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AllTermsAgreementRow(checked = false, onCheckedChange = {})
    }
}

@Preview(name = "All terms checked", widthDp = 360)
@Composable
private fun AllTermsAgreementRowCheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AllTermsAgreementRow(checked = true, onCheckedChange = {})
    }
}
