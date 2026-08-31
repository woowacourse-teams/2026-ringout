package com.joon.ringout.presentation.navigation

import com.joon.ringout.presentation.mypage.model.MyPageAccountAction

internal fun MyPageAccountAction.completionDestination(): AppRoute = when (this) {
    MyPageAccountAction.Logout -> AppRoute.Login
    MyPageAccountAction.Withdraw -> AppRoute.MyPage
}
