package com.joon.ringout.domain.member

interface MemberRepository {
    suspend fun updateNickname(nickname: String): String
}
