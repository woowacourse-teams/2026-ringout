package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.RoomHomeTab
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomHomeTabs(
    selectedTab: RoomHomeTab,
    onTabSelected: (RoomHomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 16.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Medium,
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(10.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        RoomHomeTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTabSelected(tab) })
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (tab) {
                        RoomHomeTab.Info -> "정보"
                        RoomHomeTab.Records -> "기록"
                    },
                    color = if (isSelected) colors.content else colors.secondary,
                    style = textStyle,
                )
                Spacer(Modifier.height(10.dp))
                if (isSelected) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Preview(name = "모임 홈 탭 · 다크", widthDp = 402)
@Composable
private fun RoomHomeTabsPreview() {
    RingoutTheme {
        RoomHomeTabs(
            selectedTab = RoomHomeTab.Info,
            onTabSelected = {},
            modifier = Modifier.background(roomHomeColors().background),
        )
    }
}

@Preview(name = "모임 홈 기록 탭 · 라이트", widthDp = 402)
@Composable
private fun RoomHomeTabsLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeTabs(
            selectedTab = RoomHomeTab.Records,
            onTabSelected = {},
            modifier = Modifier.background(roomHomeColors().background),
        )
    }
}
