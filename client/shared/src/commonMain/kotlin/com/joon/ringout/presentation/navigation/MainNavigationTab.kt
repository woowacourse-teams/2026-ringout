package com.joon.ringout.presentation.navigation

import com.joon.ringout.domain.room.RoomFeature

internal enum class MainNavigationTab(val title: String, val route: AppRoute) {
    Home("홈", AppRoute.Home),
    Social("소셜", AppRoute.Social),
    Records("기록", AppRoute.Records),
    MyPage("마이페이지", AppRoute.MyPage),
}

internal fun AppRoute.mainNavigationTab(): MainNavigationTab? =
    MainNavigationTab.entries.firstOrNull { it.route == this }

internal fun visibleMainNavigationTabs(roomsEnabled: Boolean = RoomFeature.isEnabled): List<MainNavigationTab> =
    MainNavigationTab.entries.filter { roomsEnabled || it != MainNavigationTab.Social }
