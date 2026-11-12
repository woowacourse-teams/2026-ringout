package com.joon.ringout.data.alarm

import kotlinx.serialization.Serializable

interface AlarmRemoteDataSource {
    suspend fun fetchAll(ownerAccountId: String): List<RemoteAlarm>

    suspend fun upsert(ownerAccountId: String, alarm: RemoteAlarm): RemoteAlarm

    suspend fun delete(ownerAccountId: String, alarmId: String)
}

@Serializable
data class RemoteAlarm(
    val alarmId: String,
    val time: String,
    val dayOfWeek: List<RemoteAlarmDay>,
    val repeatTime: Int,
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val alarmVersion: Long,
)

@Serializable
enum class RemoteAlarmDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}
