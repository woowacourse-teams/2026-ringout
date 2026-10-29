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
    private val recoveryInbox: IosAlarmMissionEventInbox? = null,
) {
    suspend fun save(request: AlarmScheduleRequest): Unit =
        IosAlarmStoreMutationMutex.withLock {
            request.validateForStorage()
            val previous = dataSource.getById(request.id)
            val scheduleRequest = request.withScheduleVersion(previous?.request)
            // Each native configuration needs an immutable mission snapshot, including
            // changes that leave the calendar time/weekday schedule unchanged.
            val versionedRequest = if (previous != null &&
                scheduleRequest.scheduleVersion == previous.request.scheduleVersion &&
                scheduleRequest != previous.request
            ) scheduleRequest.copy(scheduleVersion = previous.request.scheduleVersion + 1)
            else scheduleRequest
            val replacement = SavedAlarmSchedule(
                request = versionedRequest,
                enabled = true,
            )

            ensureAlarmAuthorization()
            try {
                if (previous?.enabled == true) {
                    dataSource.replace(previous.copy(enabled = false))
                    recoveryInbox?.setSourceEligibilityAwait(
                        alarmId = previous.request.id,
                        scheduleVersion = previous.request.scheduleVersion,
                        eligibility = IosAlarmRecoveryEligibility.DISABLED,
                    )
                    scheduler.cancelAwait(request.id)
                }
                recoveryInbox?.upsertSourceSnapshotAwait(versionedRequest.toRecoverySnapshot())
                recoveryInbox?.setSourceEligibilityAwait(
                    alarmId = versionedRequest.id,
                    scheduleVersion = versionedRequest.scheduleVersion,
                    eligibility = IosAlarmRecoveryEligibility.ENABLED,
                )
                scheduler.scheduleAwait(versionedRequest)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    scheduler.cancelBestEffort(request.id)
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        request.id, versionedRequest.scheduleVersion, IosAlarmRecoveryEligibility.DISABLED,
                    )
                    previous?.takeIf(SavedAlarmSchedule::enabled)?.let { alarm ->
                        recoveryInbox?.setSourceEligibilityBestEffort(
                            alarm.request.id,
                            alarm.request.scheduleVersion,
                            IosAlarmRecoveryEligibility.ENABLED,
                        )
                    }
                }
                val restoreFailure = previous?.restoreScheduledFailure()
                if (restoreFailure != null) {
                    dataSource.restoreDisabledSnapshotBestEffort(previous, request.id)
                    reconciler?.reconcileBestEffort()
                    throw IllegalStateException(
                        "이전 알람 예약 복구에 실패했습니다. 다음 앱 활성화 시 다시 복구합니다.",
                        error,
                    )
                }
                dataSource.restoreSnapshotBestEffort(previous, request.id)
                throw error
            }
            try {
                dataSource.replace(replacement)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    scheduler.cancelBestEffort(request.id)
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        request.id, versionedRequest.scheduleVersion, IosAlarmRecoveryEligibility.DISABLED,
                    )
                    val restoredSchedule = previous?.restoreScheduledFailure()
                    if (restoredSchedule == null) {
                        dataSource.restoreSnapshotBestEffort(previous, request.id)
                    } else {
                        dataSource.restoreDisabledSnapshotBestEffort(previous, request.id)
                    }
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

        try {
            if (enabled) {
                ensureAlarmAuthorization()
                recoveryInbox?.upsertSourceSnapshotAwait(previous.request.toRecoverySnapshot())
                recoveryInbox?.setSourceEligibilityAwait(
                    alarmId = alarmId,
                    scheduleVersion = previous.request.scheduleVersion,
                    eligibility = IosAlarmRecoveryEligibility.ENABLED,
                )
                scheduler.scheduleAwait(previous.request)
            } else {
                recoveryInbox?.setSourceEligibilityAwait(
                    alarmId = alarmId,
                    scheduleVersion = previous.request.scheduleVersion,
                    eligibility = IosAlarmRecoveryEligibility.DISABLED,
                )
                scheduler.cancelAwait(alarmId)
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                recoveryInbox?.setSourceEligibilityBestEffort(
                    alarmId, previous.request.scheduleVersion,
                    if (previous.enabled) IosAlarmRecoveryEligibility.ENABLED else IosAlarmRecoveryEligibility.DISABLED,
                )
            }
            throw error
        }
        try {
            check(dataSource.setEnabled(alarmId, enabled)) {
                "저장된 알람을 찾지 못했습니다."
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                if (enabled) {
                    scheduler.cancelBestEffort(alarmId)
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        alarmId,
                        previous.request.scheduleVersion,
                        IosAlarmRecoveryEligibility.DISABLED,
                    )
                } else if (previous.enabled) {
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        alarmId,
                        previous.request.scheduleVersion,
                        IosAlarmRecoveryEligibility.ENABLED,
                    )
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
            recoveryInbox?.setSourceEligibilityAwait(
                alarmId = alarmId,
                scheduleVersion = previous.request.scheduleVersion,
                eligibility = IosAlarmRecoveryEligibility.DELETED,
            )
            try {
                scheduler.cancelAwait(alarmId)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        alarmId, previous.request.scheduleVersion, IosAlarmRecoveryEligibility.ENABLED,
                    )
                }
                throw error
            }
        } else if (previous != null) {
            recoveryInbox?.setSourceEligibilityAwait(
                alarmId = alarmId,
                scheduleVersion = previous.request.scheduleVersion,
                eligibility = IosAlarmRecoveryEligibility.DELETED,
            )
        }
        val deleted = try {
            dataSource.delete(alarmId)
        } catch (error: Exception) {
            withContext(NonCancellable) {
                previous?.let { alarm ->
                    recoveryInbox?.setSourceEligibilityBestEffort(
                        alarmId,
                        alarm.request.scheduleVersion,
                        if (alarm.enabled) {
                            IosAlarmRecoveryEligibility.ENABLED
                        } else {
                            IosAlarmRecoveryEligibility.DISABLED
                        },
                    )
                }
                previous?.restoreScheduledBestEffort()
                reconciler?.reconcileBestEffort()
            }
            throw error
        }
        if (!deleted && previous != null) {
            withContext(NonCancellable) {
                recoveryInbox?.setSourceEligibilityBestEffort(
                    alarmId,
                    previous.request.scheduleVersion,
                    if (previous.enabled) {
                        IosAlarmRecoveryEligibility.ENABLED
                    } else {
                        IosAlarmRecoveryEligibility.DISABLED
                    },
                )
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

    private suspend fun AlarmDataSource.restoreSnapshotBestEffort(
        previous: SavedAlarmSchedule?,
        alarmId: String,
    ) {
        withContext(NonCancellable) {
            runCatching {
                if (previous == null) {
                    delete(alarmId)
                } else {
                    replace(previous)
                }
            }
        }
    }

    private suspend fun AlarmDataSource.restoreDisabledSnapshotBestEffort(
        previous: SavedAlarmSchedule,
        alarmId: String,
    ) {
        withContext(NonCancellable) {
            runCatching {
                replace(previous.copy(enabled = false))
            }.onFailure {
                runCatching { setEnabled(alarmId, false) }
            }
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

    private suspend fun IosAlarmMissionEventInbox.setSourceEligibilityBestEffort(
        alarmId: String,
        scheduleVersion: Long,
        eligibility: IosAlarmRecoveryEligibility,
    ) {
        runCatching { setSourceEligibilityAwait(alarmId, scheduleVersion, eligibility) }
    }
}

internal fun AlarmScheduleRequest.toRecoverySnapshot(): IosAlarmRecoverySourceSnapshotDto =
    IosAlarmRecoverySourceSnapshotDto(
        sourceAlarmId = id,
        scheduleVersion = scheduleVersion,
        destination = IosAlarmRecoveryDestinationSnapshotDto(
            name = destinationLabel,
            latitude = destinationLatitude,
            longitude = destinationLongitude,
        ),
        limitSeconds = limitMinutes * 60L,
        originalTime = time,
        arrivalRadiusMeters = DefaultArrivalRadiusMeters,
        repeatRule = if (repeatEnabled) selectedDays.joinToString(",") else null,
        title = destinationLabel.ifBlank { "Ringout" },
        soundName = alarmSoundName,
        soundUri = alarmSoundUri,
        owner = IosAlarmRecoveryOwnerDto(IosAlarmRecoveryOwnerKind.UNCAPTURED),
    )
