package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.validateForStorage
import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val IosAlarmStoreMutationMutex = Mutex()

internal class IosAlarmStore(
    private val dataSource: AlarmDataSource,
    private val scheduler: IosAlarmScheduler,
    private val reconciler: IosAlarmReconciler? = null,
) {
    suspend fun save(request: AlarmScheduleRequest): Unit =
        IosAlarmStoreMutationMutex.withLock {
            request.validateForStorage()
            val previous = dataSource.getById(request.id)
            val replacement = SavedAlarmSchedule(
                request = request,
                enabled = previous?.enabled ?: true,
            )
            if (!replacement.enabled) {
                dataSource.replace(replacement)
                return@withLock
            }

            ensureAlarmAuthorization()
            if (previous?.enabled == true) {
                scheduler.cancelAwait(request.id)
            }
            try {
                scheduler.scheduleAwait(request)
            } catch (error: Exception) {
                val restoreFailure = previous?.restoreScheduledFailure()
                if (restoreFailure != null) {
                    reconciler?.reconcileBestEffort()
                    throw IllegalStateException(
                        "이전 알람 예약 복구에 실패했습니다. 다음 앱 활성화 시 다시 복구합니다.",
                        error,
                    )
                }
                throw error
            }
            try {
                dataSource.replace(replacement)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    scheduler.cancelBestEffort(request.id)
                    previous?.restoreScheduledBestEffort()
                    reconciler?.reconcileBestEffort()
                }
                throw error
            }
        }

    suspend fun setEnabled(
        alarmId: String,
        enabled: Boolean,
    ): Unit = IosAlarmStoreMutationMutex.withLock {
        val previous = dataSource.getById(alarmId)
            ?: throw IllegalStateException("저장된 알람을 찾지 못했습니다.")
        if (previous.enabled == enabled) return@withLock

        if (enabled) {
            ensureAlarmAuthorization()
            scheduler.scheduleAwait(previous.request)
        } else {
            scheduler.cancelAwait(alarmId)
        }
        try {
            check(dataSource.setEnabled(alarmId, enabled)) {
                "저장된 알람을 찾지 못했습니다."
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                if (enabled) {
                    scheduler.cancelBestEffort(alarmId)
                } else if (previous.enabled) {
                    previous.restoreScheduledBestEffort()
                }
                reconciler?.reconcileBestEffort()
            }
            throw error
        }
    }

    suspend fun delete(alarmId: String): Unit = IosAlarmStoreMutationMutex.withLock {
        val previous = dataSource.getById(alarmId)
        if (previous?.enabled == true) {
            scheduler.cancelAwait(alarmId)
        }
        val deleted = try {
            dataSource.delete(alarmId)
        } catch (error: Exception) {
            withContext(NonCancellable) {
                previous?.restoreScheduledBestEffort()
                reconciler?.reconcileBestEffort()
            }
            throw error
        }
        if (!deleted && previous != null) {
            withContext(NonCancellable) {
                previous.restoreScheduledBestEffort()
                reconciler?.reconcileBestEffort()
            }
            error("저장된 알람을 삭제하지 못했습니다.")
        }
    }

    fun observeAll(): Flow<List<SavedAlarmSchedule>> = dataSource.observeAll()

    private suspend fun ensureAlarmAuthorization() {
        when (scheduler.authorizationState()) {
            IosAlarmAuthorizationState.AUTHORIZED -> return
            IosAlarmAuthorizationState.DENIED -> throw IosAlarmOperationException(
                IosAlarmOperationCode.DENIED,
                "알람 권한이 거부되어 예약할 수 없습니다.",
            )
            IosAlarmAuthorizationState.NOT_DETERMINED -> {
                val result = scheduler.requestAuthorizationAwait()
                result.requireAuthorizationSuccess()
                if (result.state != IosAlarmAuthorizationState.AUTHORIZED) {
                    throw IosAlarmOperationException(
                        IosAlarmOperationCode.DENIED,
                        "알람 권한이 허용되지 않았습니다.",
                    )
                }
            }
        }
    }

    private suspend fun SavedAlarmSchedule.restoreScheduledBestEffort() {
        if (!enabled) return
        withContext(NonCancellable) {
            scheduler.scheduleBestEffort(request)
        }
    }

    private suspend fun SavedAlarmSchedule.restoreScheduledFailure(): Throwable? {
        if (!enabled) return null
        return withContext(NonCancellable) {
            runCatching { scheduler.scheduleAwait(request) }.exceptionOrNull()
        }
    }

    private fun IosAlarmAuthorizationResult.requireAuthorizationSuccess() {
        if (isSuccess) return
        throw IosAlarmOperationException(
            code = code,
            message = message ?: "알람 권한 상태를 확인하지 못했습니다.",
        )
    }

    private suspend fun IosAlarmScheduler.scheduleBestEffort(request: AlarmScheduleRequest) {
        runCatching { scheduleAwait(request) }
    }

    private suspend fun IosAlarmScheduler.cancelBestEffort(alarmId: String) {
        runCatching { cancelAwait(alarmId) }
    }
}
