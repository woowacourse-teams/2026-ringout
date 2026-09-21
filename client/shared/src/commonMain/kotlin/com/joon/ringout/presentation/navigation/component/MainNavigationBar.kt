package com.joon.ringout.presentation.navigation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.navigation.MainNavigationTab
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.navigation_home_vector
import ringout.shared.generated.resources.navigation_social_vector
import ringout.shared.generated.resources.navigation_records_vector
import ringout.shared.generated.resources.navigation_mypage_vector

@Composable
internal fun MainNavigationBar(
    selectedTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(219.dp)
            .height(57.dp)
            .clip(CircleShape)
            .background(MaterialTheme.ringoutColors.elevatedSurface)
            .selectableGroup()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainNavigationTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabSelected(tab) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tab.iconResource()),
                    contentDescription = tab.title,
                    tint = if (selected) MaterialTheme.ringoutColors.primaryActionContent
                        else MaterialTheme.ringoutColors.navigationInactiveContent,
                    modifier = Modifier.size(
                        if (tab == MainNavigationTab.Home || tab == MainNavigationTab.Social) 24.dp else 22.dp,
                    ),
                )
            }
        }
    }
}

private fun MainNavigationTab.iconResource(): DrawableResource = when (this) {
    MainNavigationTab.Home -> Res.drawable.navigation_home_vector
    MainNavigationTab.Social -> Res.drawable.navigation_social_vector
    MainNavigationTab.Records -> Res.drawable.navigation_records_vector
    MainNavigationTab.MyPage -> Res.drawable.navigation_mypage_vector
}

@Preview
@Composable
private fun MainNavigationBarPreview() {
    RingoutTheme { MainNavigationBar(MainNavigationTab.Home, {}) }
}

@Preview
@Composable
private fun MainNavigationBarLightPreview() {
    RingoutTheme(ThemeMode.Light) { MainNavigationBar(MainNavigationTab.MyPage, {}) }
}
