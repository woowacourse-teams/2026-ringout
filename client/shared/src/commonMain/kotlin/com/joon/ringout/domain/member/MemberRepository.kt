package com.joon.ringout.domain.member

interface MemberRepository {
    suspend fun getProfile(): MemberProfile

    suspend fun updateNickname(nickname: String): String

    suspend fun withdraw()
}
