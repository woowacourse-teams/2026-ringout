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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyPageAppVersionRow(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "앱",
            modifier = Modifier.semantics { heading() },
            color = colors.primaryText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(start = 10.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(MyPageAppInfoIconResource),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = "앱 버전",
                modifier = Modifier.weight(1f),
                color = colors.primaryText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = appVersion,
                color = colors.primaryText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun MyPageAppVersionRowPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageAppVersionRow(
            appVersion = "1.0.0",
            modifier = Modifier.padding(20.dp),
        )
    }
}
