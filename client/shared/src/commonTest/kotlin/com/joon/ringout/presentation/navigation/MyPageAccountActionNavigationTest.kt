package com.joon.ringout.presentation.navigation

import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import kotlin.test.Test
import kotlin.test.assertEquals

class MyPageAccountActionNavigationTest {
    @Test
    fun `로그아웃 완료 후 로그인 화면으로 이동한다`() {
        assertEquals(
            AppRoute.Login,
            MyPageAccountAction.Logout.completionDestination(),
        )
    }

    @Test
    fun `회원 탈퇴 완료 후 마이페이지로 이동한다`() {
        assertEquals(
            AppRoute.MyPage,
            MyPageAccountAction.Withdraw.completionDestination(),
        )
    }
}
