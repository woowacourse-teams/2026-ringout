package com.joon.ringout.presentation.termsagreement.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.termsagreement.TermAgreementItem
import com.joon.ringout.presentation.termsagreement.TermId

@Composable
fun TermAgreementRow(
    term: TermAgreementItem,
    onAgreedChange: (Boolean) -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgreementCheckbox(
            checked = term.isAgreed,
            onCheckedChange = onAgreedChange,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable { onAgreedChange(!term.isAgreed) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = term.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (term.isRequired) "(필수)" else "(선택)",
                color = if (term.isRequired) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(
            onClick = onDetailClick,
            modifier = Modifier.semantics {
                contentDescription = "${term.title} 상세 보기"
            },
        ) {
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 30.sp,
            )
        }
    }
}

@Preview(name = "Required term unchecked", widthDp = 360)
@Composable
private fun RequiredTermUncheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        TermAgreementRow(
            term = TermAgreementItem(
                id = TermId.Service,
                title = "서비스 이용약관 동의",
                isRequired = true,
            ),
            onAgreedChange = {},
            onDetailClick = {},
        )
    }
}

@Preview(name = "Optional term checked", widthDp = 360)
@Composable
private fun OptionalTermCheckedPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        TermAgreementRow(
            term = TermAgreementItem(
                id = TermId.Privacy,
                title = "마케팅 정보 수신 동의",
                isRequired = false,
                isAgreed = true,
            ),
            onAgreedChange = {},
            onDetailClick = {},
        )
    }
}
