package com.joon.ringout.data.outbox

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncDao
import com.joon.ringout.data.alarmmovement.AlarmMovementOutboxEntity
import com.joon.ringout.data.destination.DestinationOutboxEntity
import com.joon.ringout.data.destination.SavedDestinationDao
import com.joon.ringout.domain.outbox.OutboxReader
import com.joon.ringout.domain.outbox.OutboxRecord
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class RoomOutboxReader(
    private val destinationDao: SavedDestinationDao,
    private val alarmOccurrenceSyncDao: AlarmOccurrenceSyncDao,
) : OutboxReader {
    override suspend fun read(): List<OutboxRecord> = combine(
        destinationDao.observeOutbox(),
        alarmOccurrenceSyncDao.observeUnsentEvents(),
        alarmOccurrenceSyncDao.observeUnsentMovements(),
    ) { destinations, occurrences, movements ->
        buildList {
            destinations.forEach { add(it.toOutboxRecord()) }
            occurrences.forEach { add(it.toOutboxRecord()) }
            movements.forEach { add(it.toOutboxRecord()) }
        }
    }.first()
}

private fun DestinationOutboxEntity.toOutboxRecord() = OutboxRecord(
    source = "저장 목적지",
    id = id.toString(),
    state = state.name,
    details = listOfNotNull(
        "계정 ID: $ownerAccountId",
        "로컬 목적지 ID: $localDestinationId",
        "서버 목적지 ID: $remoteDestinationId",
        "작업: ${operation.name}",
        payloadAlias?.let { "이름: $it" },
        payloadLatitude?.let { "위도: $it" },
        payloadLongitude?.let { "경도: $it" },
        destinationVersion?.let { "버전: $it" },
        "시도 횟수: $attemptCount",
        nextAttemptAtEpochMillis?.let { "다음 시도 시각(ms): $it" },
        lastErrorCode?.let { "오류 코드: $it" },
        lastErrorMessage?.let { "오류 내용: $it" },
    ).joinToString("\n"),
)

private fun AlarmOccurrenceOutboxEntity.toOutboxRecord() = OutboxRecord(
    source = "알람 실행 이벤트",
    id = id.toString(),
    state = state.name,
    details = listOfNotNull(
        "실행 ID: $localExecutionId",
        "울림 ID: $localRingingId",
        "중복 방지 키: $deduplicationKey",
        "이벤트: ${kind.name}",
        occurredAtEpochMillis?.let { "발생 시각(ms): $it" },
        "시도 횟수: $attemptCount",
        nextAttemptAtEpochMillis?.let { "다음 시도 시각(ms): $it" },
        lastErrorCode?.let { "오류 코드: $it" },
        lastErrorMessage?.let { "오류 내용: $it" },
    ).joinToString("\n"),
)

private fun AlarmMovementOutboxEntity.toOutboxRecord() = OutboxRecord(
    source = "모임 이동 이벤트",
    id = id.toString(),
    state = state.name,
    details = listOfNotNull(
        "실행 ID: $localExecutionId",
        "중복 방지 키: $deduplicationKey",
        "작업: ${action.name}",
        roomId?.let { "모임 ID: $it" },
        "발생 시각(ms): $occurredAtEpochMillis",
        "시도 횟수: $attemptCount",
        nextAttemptAtEpochMillis?.let { "다음 시도 시각(ms): $it" },
        lastErrorCode?.let { "오류 코드: $it" },
    ).joinToString("\n"),
)
