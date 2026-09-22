package com.joon.ringout.presentation.login

import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KakaoAccessTokenResultTest {
    @Test
    fun `카카오 사용자 취소는 실패가 아닌 취소로 전달한다`() {
        assertEquals(
            KakaoAccessTokenResult.Cancelled,
            kakaoAccessTokenResult(null, ClientError(ClientErrorCause.Cancelled, "cancelled")),
        )
    }

    @Test
    fun `빈 토큰이나 SDK 오류는 성공으로 처리하지 않는다`() {
        assertIs<KakaoAccessTokenResult.Failure>(kakaoAccessTokenResult(null, null))
        assertIs<KakaoAccessTokenResult.Failure>(kakaoAccessTokenResult(" ", null))
        assertIs<KakaoAccessTokenResult.Failure>(kakaoAccessTokenResult("token", IllegalStateException()))
        assertEquals(KakaoAccessTokenResult.Success("token"), kakaoAccessTokenResult("token", null))
    }
}
