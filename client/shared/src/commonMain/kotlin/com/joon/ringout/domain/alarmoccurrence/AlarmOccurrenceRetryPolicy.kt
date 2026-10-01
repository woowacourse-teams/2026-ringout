package com.joon.ringout.domain.alarmoccurrence

/** 5초부터 증가하며 최대 15분 대기한다. 시각과 횟수는 저장소에 남겨 재실행 후에도 유지한다. */
object AlarmOccurrenceRetryPolicy {
    fun delayMillis(attemptCount: Int): Long {
        require(attemptCount > 0)
        return (5_000L * (1L shl (attemptCount - 1).coerceAtMost(8))).coerceAtMost(900_000L)
    }

    fun isRetryableHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 429 || statusCode in 500..599
}
