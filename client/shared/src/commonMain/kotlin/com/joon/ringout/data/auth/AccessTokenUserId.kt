package com.joon.ringout.data.auth

import com.joon.ringout.data.network.ApiJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

/** 서버가 발급한 JWT의 회원 ID를 로컬 식별에 사용한다. 서명 검증이나 인증 검증을 대체하지 않는다. */
internal fun accessTokenUserId(token: String): String? = runCatching {
    val parts = token.split('.')
    if (parts.size != 3) return null
    val payload = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
    val claims = ApiJson.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()).jsonObject
    if (claims["tokenType"]?.jsonPrimitive?.contentOrNull != "ACCESS") return null
    val userId = claims["userId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
    if (claims["sub"]?.jsonPrimitive?.contentOrNull != userId.toString()) return null
    userId.toString()
}.getOrNull()
