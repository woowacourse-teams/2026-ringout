package com.joon.ringout.presentation.profilechange

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberProfile
import com.joon.ringout.domain.member.MemberProfileImage
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.member.ProfileImageUpload
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileChangeRouteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `인증이 풀리면 이전 프로필 상태가 남아 있어도 화면을 이탈한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var backClicks = 0
        try {
            activity.get().setContent {
                RingoutTheme {
                    ProfileChangeRoute(
                        accountStatus = MyPageAccountStatus.LoggedIn(
                            nickname = "링아웃",
                            email = "ringout@example.com",
                        ),
                        authSessionState = AuthSessionState.Unauthenticated,
                        memberRepository = UnusedMemberRepository,
                        onBackClick = { backClicks++ },
                        onNicknameChanged = {},
                    )
                }
            }

            compose.waitForIdle()

            assertEquals(1, backClicks)
        } finally {
            activity.pause().stop().destroy()
        }
    }
}

private object UnusedMemberRepository : MemberRepository {
    override suspend fun getProfile(): MemberProfile =
        error("프로필 조회를 호출하면 안 됩니다.")

    override suspend fun getProfileImage(): MemberProfileImage =
        error("이미지 조회를 호출하면 안 됩니다.")

    override suspend fun uploadProfileImage(image: ProfileImageUpload): MemberProfileImage =
        error("이미지 업로드를 호출하면 안 됩니다.")

    override suspend fun updateNickname(nickname: String): String =
        error("닉네임 변경을 호출하면 안 됩니다.")

    override suspend fun withdraw() = error("회원 탈퇴를 호출하면 안 됩니다.")
}
