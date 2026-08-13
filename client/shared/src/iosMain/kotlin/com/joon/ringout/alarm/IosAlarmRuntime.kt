package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.platform.IosNativeServices
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IosAlarmRuntime(
    private val missionCoordinator: IosAlarmMissionCoordinator,
    private val reconciler: IosAlarmReconciler,
) {
    private val startMutex = Mutex()
    val activeMissionFlow: StateFlow<ActiveAlarmMission?> =
        missionCoordinator.activeMissionFlow

    suspend fun start() = startMutex.withLock {
        missionCoordinator.processPendingEvents()
        reconciler.reconcile()
    }

    suspend fun clearActiveMission(occurrenceId: String? = null) {
        missionCoordinator.clearActiveMission(occurrenceId)
    }
}

fun createIosAlarmRuntime(nativeServices: IosNativeServices): IosAlarmRuntime {
    val dataSource = RoomAlarmDataSource(getRingoutDatabase().alarmDao())
    return IosAlarmRuntime(
        missionCoordinator = IosAlarmMissionCoordinator(
            dataSource = dataSource,
            inbox = nativeServices.alarmMissionEventInbox(),
        ),
        reconciler = IosAlarmReconciler(
            dataSource = dataSource,
            scheduler = nativeServices.alarmScheduler(),
            normalizeAlarmId = nativeServices::normalizeAlarmId,
        ),
    )
}
