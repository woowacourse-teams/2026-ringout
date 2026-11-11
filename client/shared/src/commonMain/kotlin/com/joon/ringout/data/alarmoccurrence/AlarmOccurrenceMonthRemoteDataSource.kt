package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.alarmoccurrence.alarmOccurrenceServerDate
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.time.Instant

internal class AlarmOccurrenceMonthRemoteDataSource(
    private val httpClient: HttpClient,
    tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)

    suspend fun fetch(account: AlarmOccurrenceAccount, month: MissionYearMonth): AlarmOccurrenceMonthSnapshot {
        val monthKey = month.toCacheKey()
        val response = authenticatedRequests.execute { accessToken ->
            account.checkToken(authSession, accessToken)
            httpClient.get(ApiConfig.url("/api/v1/alarm-occurrences")) {
                bearerAuth(accessToken)
                parameter("month", monthKey)
            }
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(responseBody) }.getOrNull()
            throw ApiException(response.status.value, error?.code, error?.message ?: response.status.description, error?.result)
        }
        val body = ApiJson.decodeFromString<ApiResponse<AlarmOccurrenceMonthResponse>>(responseBody)
        if (!body.isSuccess) throw ApiException(response.status.value, body.code, body.message)
        return checkNotNull(body.result).toSnapshot(account.ownerAccountId, month)
    }
}

internal fun MissionYearMonth.toCacheKey(): String =
    year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0')

internal data class AlarmOccurrenceMonthSnapshot(
    val occurrences: List<CachedAlarmOccurrenceEntity>,
    val ringings: List<CachedAlarmRingingEntity>,
)

@Serializable
private data class AlarmOccurrenceMonthResponse(
    val month: String,
    val days: List<AlarmOccurrenceDayResponse>,
) {
    fun toSnapshot(ownerAccountId: String, requestedMonth: MissionYearMonth): AlarmOccurrenceMonthSnapshot {
        require(month == requestedMonth.toCacheKey())
        val expectedDates = (1..requestedMonth.dayCount)
            .map { MissionDate.of(requestedMonth.year, requestedMonth.month, it).iso8601 }
        require(days.size == expectedDates.size && days.map { it.date }.toSet() == expectedDates.toSet())

        val occurrenceIds = mutableSetOf<String>()
        val occurrences = mutableListOf<CachedAlarmOccurrenceEntity>()
        val ringings = mutableListOf<CachedAlarmRingingEntity>()
        for (day in days) {
            for (occurrence in day.occurrences) {
                require(occurrence.alarmOccurrenceId.isNotBlank() && occurrenceIds.add(occurrence.alarmOccurrenceId))
                require(occurrence.alarmId.isNotBlank() && alarmTimeFormat.matches(occurrence.alarmTime))
                val initial = occurrence.ringings.single { it.type == "INITIAL" }
                val startedAt = Instant.parse(initial.ringingAt)
                require(alarmOccurrenceServerDate(startedAt).iso8601 == day.date)
                val endedAt = occurrence.endedAt?.let(Instant::parse)
                require(
                    when (occurrence.endType) {
                        null -> endedAt == null
                        "ARRIVED", "FORCE_ENDED" -> endedAt != null && endedAt >= startedAt
                        else -> false
                    },
                )

                val ringingKeys = mutableSetOf<String>()
                for (ringing in occurrence.ringings) {
                    val key = ringing.key()
                    require(ringingKeys.add(key))
                    val ringingAt = Instant.parse(ringing.ringingAt)
                    val dismissedAt = ringing.dismissedAt?.let(Instant::parse)
                    require(ringingAt >= startedAt && (dismissedAt == null || dismissedAt >= ringingAt))
                    ringings += CachedAlarmRingingEntity(
                        ownerAccountId = ownerAccountId,
                        occurrenceId = occurrence.alarmOccurrenceId,
                        ringingKey = key,
                        eventId = ringing.eventId,
                        ringingAtEpochMillis = ringingAt.toEpochMilliseconds(),
                        dismissedAtEpochMillis = dismissedAt?.toEpochMilliseconds(),
                    )
                }
                occurrences += CachedAlarmOccurrenceEntity(
                    ownerAccountId = ownerAccountId,
                    month = month,
                    occurrenceId = occurrence.alarmOccurrenceId,
                    alarmId = occurrence.alarmId,
                    date = day.date,
                    alarmTime = occurrence.alarmTime,
                    endType = occurrence.endType,
                    endedAtEpochMillis = endedAt?.toEpochMilliseconds(),
                )
            }
        }
        return AlarmOccurrenceMonthSnapshot(occurrences, ringings)
    }
}

@Serializable
private data class AlarmOccurrenceDayResponse(
    val date: String,
    val occurrences: List<AlarmOccurrenceListItemResponse>,
)

@Serializable
private data class AlarmOccurrenceListItemResponse(
    val alarmId: String,
    val alarmOccurrenceId: String,
    val alarmTime: String,
    val ringings: List<AlarmOccurrenceListRingingResponse>,
    val endType: String? = null,
    val endedAt: String? = null,
)

@Serializable
private data class AlarmOccurrenceListRingingResponse(
    val type: String,
    val eventId: String? = null,
    val ringingAt: String,
    val dismissedAt: String? = null,
) {
    fun key(): String = when (type) {
        "INITIAL" -> {
            require(eventId == null)
            "INITIAL"
        }

        "REPEAT" -> {
            require(!eventId.isNullOrBlank())
            "REPEAT:$eventId"
        }

        else -> error("지원하지 않는 알람 울림 유형입니다.")
    }
}

private val alarmTimeFormat = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
