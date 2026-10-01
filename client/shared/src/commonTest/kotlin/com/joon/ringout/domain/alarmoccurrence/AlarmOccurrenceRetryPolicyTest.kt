package com.joon.ringout.domain.alarmoccurrence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmOccurrenceRetryPolicyTest {
    @Test
    fun `재시도 간격은 다섯 초부터 증가하고 십오 분을 넘지 않는다`() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L), (1..3).map(AlarmOccurrenceRetryPolicy::delayMillis))
        assertEquals(900_000L, AlarmOccurrenceRetryPolicy.delayMillis(Int.MAX_VALUE))
    }

    @Test
    fun `일시적인 HTTP 오류만 자동 재시도한다`() {
        listOf(408, 429, 500, 503).forEach { assertTrue(AlarmOccurrenceRetryPolicy.isRetryableHttpStatus(it)) }
        listOf(200, 400, 401, 403, 404, 409).forEach { assertFalse(AlarmOccurrenceRetryPolicy.isRetryableHttpStatus(it)) }
    }
}
