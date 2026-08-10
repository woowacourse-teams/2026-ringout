package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res

@Composable
fun MyPageAppVersionRow(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "앱",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ⓘ",
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "앱 버전",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = appVersion,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun MyPageAppVersionRowPreview() {
    RingoutTheme {
        MyPageAppVersionRow(
            appVersion = "1.0.0",
            modifier = Modifier.padding(20.dp),
        )
    }
}
