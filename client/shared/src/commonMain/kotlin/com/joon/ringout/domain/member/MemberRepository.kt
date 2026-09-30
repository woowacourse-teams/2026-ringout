package com.joon.ringout.domain.member

interface MemberRepository {
    /** 현재 로그인 세션에서 조회에 성공한 프로필. 없으면 null. */
    fun getCachedProfile(): MemberProfile? = null

    suspend fun getProfile(): MemberProfile

    suspend fun updateNickname(nickname: String): String

    suspend fun withdraw()
}
