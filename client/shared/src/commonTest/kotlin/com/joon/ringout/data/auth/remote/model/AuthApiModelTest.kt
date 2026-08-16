package com.joon.ringout.data.auth.remote.model

import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthApiModelTest {
    @Test
    fun `기존 회원 로그인 응답을 역직렬화한다`() {
        val response = ApiJson.decodeFromString<ApiResponse<LoginResponse>>(
            """
            {
              "isSuccess": true,
              "code": "AUTH200",
              "message": "로그인에 성공했습니다.",
              "result": {
                "accessToken": "access-token",
                "refreshToken": "refresh-token",
                "isNewUser": false
              }
            }
            """.trimIndent(),
        )

        assertEquals("access-token", response.result?.accessToken)
        assertEquals("refresh-token", response.result?.refreshToken)
        assertNull(response.result?.signupToken)
        assertEquals(false, response.result?.isNewUser)
    }

    @Test
    fun `신규 회원 로그인 응답을 역직렬화한다`() {
        val response = ApiJson.decodeFromString<ApiResponse<LoginResponse>>(
            """
            {
              "isSuccess": true,
              "code": "AUTH200",
              "message": "회원가입이 필요합니다.",
              "result": {
                "signupToken": "signup-token",
                "isNewUser": true
              }
            }
            """.trimIndent(),
        )

        assertEquals("signup-token", response.result?.signupToken)
        assertNull(response.result?.accessToken)
        assertNull(response.result?.refreshToken)
        assertEquals(true, response.result?.isNewUser)
    }

    @Test
    fun `회원가입 요청을 직렬화한다`() {
        val request = SignupRequest(
            termsTypes = listOf(TermsType.SERVICE, TermsType.PRIVACY),
            agreedAt = "2026-08-16",
        )

        val json = ApiJson.parseToJsonElement(ApiJson.encodeToString(request)) as JsonObject

        assertEquals("2026-08-16", json.getValue("agreedAt").jsonPrimitive.content)
        assertEquals(
            "[\"SERVICE\",\"PRIVACY\"]",
            json.getValue("termsTypes").toString(),
        )
    }

    @Test
    fun `형태가 다른 오류 result를 보존한다`() {
        val response = ApiJson.decodeFromString<ApiErrorResponse>(
            """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "termsTypes": "must not be empty"
              }
            }
            """.trimIndent(),
        )

        assertEquals("COMMON400", response.code)
        assertEquals(
            "must not be empty",
            (response.result as JsonObject).getValue("termsTypes").jsonPrimitive.content,
        )
    }
}
