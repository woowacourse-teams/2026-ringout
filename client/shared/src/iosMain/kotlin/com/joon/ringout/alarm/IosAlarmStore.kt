package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.validateForStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val IosAlarmStoreMutationMutex = Mutex()

internal class IosAlarmStore(
    private val dataSource: AlarmDataSource,
) {
    suspend fun save(request: AlarmScheduleRequest): Unit =
        IosAlarmStoreMutationMutex.withLock {
            request.validateForStorage()
            val previous = dataSource.getById(request.id)
            dataSource.replace(
                SavedAlarmSchedule(
                    request = request,
                    enabled = previous?.enabled ?: true,
                ),
            )
        }

    suspend fun setEnabled(
        alarmId: String,
        enabled: Boolean,
    ): Unit = IosAlarmStoreMutationMutex.withLock {
        check(dataSource.setEnabled(alarmId, enabled)) {
            "저장된 알람을 찾지 못했습니다."
        }
    }

    suspend fun delete(alarmId: String): Unit = IosAlarmStoreMutationMutex.withLock {
        dataSource.delete(alarmId)
    }

    fun observeAll(): Flow<List<SavedAlarmSchedule>> = dataSource.observeAll()
}
