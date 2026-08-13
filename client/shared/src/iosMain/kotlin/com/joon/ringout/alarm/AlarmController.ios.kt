package com.joon.ringout.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.platform.LocalIosNativeServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

@Composable
actual fun rememberAlarmController(
    onSaveCompleted: (AlarmScheduleRequest) -> Unit,
    onError: (String) -> Unit,
): AlarmController {
    val nativeServices = LocalIosNativeServices.current
    val store = remember {
        val dataSource = RoomAlarmDataSource(getRingoutDatabase().alarmDao())
        val scheduler = nativeServices.alarmScheduler()
        val reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = scheduler,
            normalizeAlarmId = nativeServices::normalizeAlarmId,
        )
        IosAlarmStore(
            dataSource = dataSource,
            scheduler = scheduler,
            reconciler = reconciler,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val currentOnSaveCompleted = rememberUpdatedState(onSaveCompleted)
    val currentOnError = rememberUpdatedState(onError)
    return remember(store, coroutineScope) {
        AlarmController(
            schedule = { request ->
                coroutineScope.launch {
                    runIosAlarmMutation(
                        fallbackErrorMessage = "알람을 저장하지 못했습니다.",
                        onError = { message -> currentOnError.value(message) },
                        mutation = { store.save(request) },
                        onSuccess = { currentOnSaveCompleted.value(request) },
                    )
                }
            },
            setEnabled = { alarmId, enabled ->
                coroutineScope.launch {
                    runIosAlarmMutation(
                        fallbackErrorMessage = "알람 상태를 변경하지 못했습니다.",
                        onError = { message -> currentOnError.value(message) },
                        mutation = { store.setEnabled(alarmId, enabled) },
                    )
                }
            },
            deleteAlarm = { alarmId ->
                coroutineScope.launch {
                    runIosAlarmMutation(
                        fallbackErrorMessage = "알람을 삭제하지 못했습니다.",
                        onError = { message -> currentOnError.value(message) },
                        mutation = { store.delete(alarmId) },
                    )
                }
            },
            savedAlarms = store.observeAll().recoveringAlarmObservation(
                onError = { error ->
                    currentOnError.value(
                        error.message ?: "저장된 알람을 불러오지 못했습니다.",
                    )
                },
            ),
        )
    }
}

internal suspend fun runIosAlarmMutation(
    fallbackErrorMessage: String,
    onError: (String) -> Unit,
    mutation: suspend () -> Unit,
    onSuccess: () -> Unit = {},
) {
    try {
        mutation()
        onSuccess()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onError(error.message ?: fallbackErrorMessage)
    }
}

internal fun Flow<List<SavedAlarmSchedule>>.recoveringAlarmObservation(
    onError: (Throwable) -> Unit,
    retryDelayMillis: Long = AlarmLoadRetryDelayMillis,
): Flow<List<SavedAlarmSchedule>> = flow {
    var errorReportedForCurrentSeries = false
    while (true) {
        try {
            emitAll(
                this@recoveringAlarmObservation.onEachSuccessfulEmission {
                    errorReportedForCurrentSeries = false
                },
            )
            return@flow
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!errorReportedForCurrentSeries) {
                onError(error)
                errorReportedForCurrentSeries = true
            }
            delay(retryDelayMillis)
        }
    }
}

private fun Flow<List<SavedAlarmSchedule>>.onEachSuccessfulEmission(
    onEmission: () -> Unit,
): Flow<List<SavedAlarmSchedule>> = flow {
    collect { alarms ->
        onEmission()
        emit(alarms)
    }
}

private const val AlarmLoadRetryDelayMillis = 1_000L
