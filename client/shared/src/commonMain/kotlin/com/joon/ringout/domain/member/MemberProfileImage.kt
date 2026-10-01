package com.joon.ringout.domain.member

/** 조회 성공 결과. url이 null이면 등록된 프로필 이미지가 없다. */
data class MemberProfileImage(val url: String?)
