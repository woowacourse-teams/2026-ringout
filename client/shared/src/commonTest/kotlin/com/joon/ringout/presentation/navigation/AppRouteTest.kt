package com.joon.ringout.presentation.navigation

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppRouteTest {
    @Test
    fun `서로 다른 경로를 공통 타입으로 직렬화하고 순서와 인자를 복원한다`() {
        val routes = listOf<AppRoute>(
            AppRoute.Onboarding,
            AppRoute.Home,
            AppRoute.AddAlarm,
            AppRoute.EditAlarm(alarmId = "alarm/서울?time=07:30&label=\"출근\""),
            AppRoute.Destination(requestId = Long.MAX_VALUE),
            AppRoute.AlarmSound,
            AppRoute.MyPage,
            AppRoute.NicknameChange,
            AppRoute.Login,
            AppRoute.TermsAgreement,
            AppRoute.AlarmRinging(alarmId = "system-alarm-1"),
            AppRoute.ActiveAlarmTracking(occurrenceId = "alarm-1:retry-2"),
            AppRoute.Home,
            AppRoute.EditAlarm(alarmId = "alarm-2"),
        )

        val encoded = Json.encodeToString(routes)
        val restored = Json.decodeFromString<List<AppRoute>>(encoded)

        assertEquals(routes, restored)
    }

    @Test
    fun `필수 식별자가 없는 경로는 복원하지 않는다`() {
        val serializers = listOf(
            AppRoute.EditAlarm.serializer(),
            AppRoute.Destination.serializer(),
            AppRoute.AlarmRinging.serializer(),
            AppRoute.ActiveAlarmTracking.serializer(),
        )

        serializers.forEach { serializer ->
            assertFailsWith<SerializationException> {
                Json.decodeFromString(serializer, "{}")
            }
        }
    }
}
