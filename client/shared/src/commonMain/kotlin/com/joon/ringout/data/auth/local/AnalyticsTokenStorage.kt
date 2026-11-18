package com.joon.ringout.data.auth.local

import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage

/** 인증 전환의 토큰 저장이 완료된 뒤, 세션 상태를 공개하기 전에 Analytics ID를 반영한다. */
internal class AnalyticsTokenStorage(
    private val delegate: SecureTokenStorage,
    private val tokenSnapshot: () -> AuthTokens?,
    private val setUserId: (String?) -> Unit,
) : SecureTokenStorage by delegate {
    override suspend fun save(tokens: AuthTokens) {
        delegate.save(tokens)
        updateUserId(tokens)
    }

    override suspend fun clear() {
        delegate.clear()
        updateUserId(null)
    }

    override suspend fun expire() {
        delegate.expire()
        updateUserId(null)
    }

    fun readSnapshot(): AuthTokens? = tokenSnapshot()

    /** 앱 시작 시 인증 작업을 시작하기 전에 한 번 호출한다. 일반 read에는 부수 효과를 넣지 않는다. */
    fun synchronizeUserId() {
        val tokens = try {
            tokenSnapshot()
        } catch (_: Exception) {
            null
        }
        synchronizeUserId(tokens)
    }

    /** 세션 복원 시 인증 전환 mutex 안에서 읽은 토큰으로 동기화한다. */
    fun synchronizeUserId(tokens: AuthTokens?) {
        updateUserId(tokens)
    }

    private fun updateUserId(tokens: AuthTokens?) {
        val userId = tokens?.userId?.takeIf { it > 0 }?.toString()
        try {
            setUserId(userId)
        } catch (_: Exception) {
            // 분석 SDK 실패가 로그인·로그아웃 결과를 바꾸거나 토큰을 노출하지 않게 한다.
        }
    }
}
