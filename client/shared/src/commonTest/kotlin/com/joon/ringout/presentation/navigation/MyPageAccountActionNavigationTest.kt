package com.joon.ringout.presentation.navigation

import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
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

    @Test
    fun `계정 작업 완료 시 가입 상태를 초기화하고 이동한 뒤 이벤트를 소비한다`() {
        val calls = mutableListOf<Any>()
        val handler = MyPageAccountActionCompletionHandler(
            resetSignup = { calls += "resetSignup" },
            navigate = { route -> calls += route },
            consumeCompletedEvent = { eventId -> calls += eventId },
        )

        handler.handle(
            MyPageAccountActionState.Completed(
                eventId = 7L,
                action = MyPageAccountAction.Logout,
            ),
        )

        assertEquals(
            listOf("resetSignup", AppRoute.Login, 7L),
            calls,
        )
    }
}
