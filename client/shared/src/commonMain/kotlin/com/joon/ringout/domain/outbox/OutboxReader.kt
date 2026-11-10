package com.joon.ringout.domain.outbox

data class OutboxRecord(
    val source: String,
    val id: String,
    val state: String,
    val details: String,
)

fun interface OutboxReader {
    suspend fun read(): List<OutboxRecord>
}

object EmptyOutboxReader : OutboxReader {
    override suspend fun read(): List<OutboxRecord> = emptyList()
}
