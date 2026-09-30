package com.joon.ringout.presentation.termsreagreement.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionDialogPalette

@Composable
internal fun TermsUpdateNotice(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyPageAccountActionDialogPalette
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier.widthIn(max = 334.dp).fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape).background(colors.surface).padding(24.dp)
            .semantics { paneTitle = "약관 동의 안내" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("약관 동의", color = colors.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Text("약관이 업데이트되었습니다.\n최신 약관에 동의 후 이용 가능합니다.",
            color = colors.description, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 19.2.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(162.dp).heightIn(min = 46.dp).clickable(role = Role.Button, onClick = onContinue),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                .background(colors.logout).padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("동의하러 가기", color = colors.actionContent,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 19.2.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Preview(name = "최신 약관 안내", widthDp = 402, heightDp = 941)
@Composable
private fun TermsUpdateNoticePreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
            TermsUpdateNotice({})
        }
    }
}
