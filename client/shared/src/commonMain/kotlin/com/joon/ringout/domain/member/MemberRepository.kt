package com.joon.ringout.domain.member

interface MemberRepository {
    /** 현재 로그인 세션에서 조회에 성공한 프로필. 없으면 null. */
    fun getCachedProfile(): MemberProfile? = null

    suspend fun getProfile(): MemberProfile

    /** null은 미조회 상태이며, 이미지가 없는 조회 결과는 MemberProfileImage(null)이다. */
    fun getCachedProfileImage(): MemberProfileImage? = null

    suspend fun getProfileImage(): MemberProfileImage

    suspend fun uploadProfileImage(image: ProfileImageUpload): MemberProfileImage

    suspend fun updateNickname(nickname: String): String

    suspend fun withdraw()
}
