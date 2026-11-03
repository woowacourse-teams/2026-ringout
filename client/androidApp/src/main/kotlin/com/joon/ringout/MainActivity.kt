package com.joon.ringout

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.ActiveAlarmMissionStore
import com.joon.ringout.alarm.ForceEndMissionResult
import com.joon.ringout.alarm.AlarmMissionCoordinator
import com.joon.ringout.alarm.AlarmRingingRuntime
import com.joon.ringout.domain.update.AppUpdateAccess
import com.joon.ringout.presentation.activemission.AlarmCompletionRoute
import com.joon.ringout.presentation.update.AppUpdateDialog
import com.joon.ringout.presentation.update.MandatoryUpdateScreen
import com.joon.ringout.presentation.update.MandatoryUpdateViewModel
import com.joon.ringout.presentation.update.PlayStoreUpdateChecker
import com.joon.ringout.push.RoomActivityPushClickViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private data class IncomingIntent(
        val intent: Intent,
        val isNewIntent: Boolean,
    )

    private var incomingIntent by mutableStateOf<IncomingIntent?>(null)
    private lateinit var activeAlarmMissionStore: ActiveAlarmMissionStore
    private lateinit var alarmMissionCoordinator: AlarmMissionCoordinator
    private var activeAlarmMission by mutableStateOf<ActiveAlarmMission?>(null)
    private var activeAlarmMissionLocation by mutableStateOf<ActiveAlarmMissionLocation?>(null)
    private var isUpdateDialogVisible by mutableStateOf(false)
    private lateinit var playStoreUpdateChecker: PlayStoreUpdateChecker
    private val mandatoryUpdateViewModel: MandatoryUpdateViewModel by viewModels {
        viewModelFactory {
            initializer {
                MandatoryUpdateViewModel(
                    remoteConfigState = (application as RingoutApplication).remoteConfigRepository.state,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                )
            }
        }
    }
    private val activeAlarmMissionPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshActiveAlarmMission()
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        incomingIntent = IncomingIntent(intent, isNewIntent = false)
        val appContainer = (application as RingoutApplication).appContainer
        activeAlarmMissionStore = ActiveAlarmMissionStore(applicationContext)
        alarmMissionCoordinator = AlarmMissionCoordinator(applicationContext)
        playStoreUpdateChecker = PlayStoreUpdateChecker(
            activity = this,
            currentVersionCode = BuildConfig.VERSION_CODE,
        )
        refreshActiveAlarmMission()
        lifecycleScope.launch {
            AlarmRingingRuntime.ringingOccurrenceId.collect { refreshActiveAlarmMission() }
        }
        playStoreUpdateChecker.check {
            isUpdateDialogVisible = true
        }

        setContent {
            val updateUiState by mandatoryUpdateViewModel.uiState.collectAsState()
            val ringingOccurrenceId by AlarmRingingRuntime.ringingOccurrenceId.collectAsState()
            val appStateHolder = rememberSaveableStateHolder()
            val roomActivityPushViewModel: RoomActivityPushClickViewModel = viewModel {
                RoomActivityPushClickViewModel(createSavedStateHandle())
            }
            val latestIncomingIntent = incomingIntent
            LaunchedEffect(latestIncomingIntent) {
                latestIncomingIntent?.let { incoming ->
                    roomActivityPushViewModel.receive(
                        incoming.intent,
                        incoming.isNewIntent,
                        preservePendingRequest = updateUiState.access != AppUpdateAccess.Allowed,
                    )
                }
            }
            AppStateHost {
                if (updateUiState.access == AppUpdateAccess.Allowed) {
                    appStateHolder.SaveableStateProvider("app") {
                        PlatformAppHost(appContainer) { extraContent ->
                            App(
                                appContainer = appContainer,
                                appVersion = BuildConfig.VERSION_NAME,
                                myPageExtraContent = extraContent,
                                activeAlarmMission = activeAlarmMission,
                                activeAlarmMissionLocation = activeAlarmMissionLocation,
                                onActiveAlarmMissionExpired = ::handleActiveAlarmMissionExpired,
                                onActiveAlarmMissionForceEnd = { handleActiveAlarmMissionForceEnd(it) },
                                onActiveAlarmMissionForceEndHoldStarted =
                                    alarmMissionCoordinator::recordForceEndHoldStarted,
                                onActiveAlarmMissionForceEndHoldCancelled =
                                    alarmMissionCoordinator::recordForceEndHoldCancelled,
                                onActiveAlarmMissionForceEndHoldCompleted =
                                    alarmMissionCoordinator::recordForceEndHoldCompleted,
                                pendingRoomActivityPush = roomActivityPushViewModel.pendingRequest,
                                onRoomActivityPushHandled = roomActivityPushViewModel::acknowledge,
                            )
                        }
                    }
                    if (isUpdateDialogVisible) {
                        RingoutTheme(themeMode = ThemeMode.Light) {
                            AppUpdateDialog(
                                onDismissRequest = { isUpdateDialogVisible = false },
                                onUpdateClick = {
                                    isUpdateDialogVisible = false
                                    playStoreUpdateChecker.openStore()
                                },
                            )
                        }
                    }
                } else {
                    RingoutTheme(themeMode = ThemeMode.Light) {
                        AlarmCompletionRoute(
                            activeMission = activeAlarmMission,
                            currentLocation = activeAlarmMissionLocation,
                            hasPendingAlarm = updateUiState.access == AppUpdateAccess.DeferredForAlarm,
                            onBackground = { moveTaskToBack(true) },
                            onExpired = ::handleActiveAlarmMissionExpired,
                            onForceEnd = { handleActiveAlarmMissionForceEnd(it) },
                            onForceEndHoldStarted = alarmMissionCoordinator::recordForceEndHoldStarted,
                            onForceEndHoldCancelled = alarmMissionCoordinator::recordForceEndHoldCancelled,
                            onForceEndHoldCompleted = alarmMissionCoordinator::recordForceEndHoldCompleted,
                            onReturnToRinging = if (ringingOccurrenceId != null) {
                                { AlarmRingingRuntime.openRingingActivity(this) }
                            } else {
                                null
                            },
                        ) {
                            MandatoryUpdateScreen(
                                uiState = updateUiState,
                                onUpdateClick = {
                                    mandatoryUpdateViewModel.onStoreOpenResult(playStoreUpdateChecker.openStore())
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent = IncomingIntent(intent, isNewIntent = true)
    }

    override fun onStart() {
        super.onStart()
        activeAlarmMissionStore.registerListener(activeAlarmMissionPreferenceListener)
        refreshActiveAlarmMission()
    }

    override fun onResume() {
        super.onResume()
        refreshActiveAlarmMission()
        alarmMissionCoordinator.resumeTracking()
    }

    override fun onStop() {
        activeAlarmMissionStore.unregisterListener(activeAlarmMissionPreferenceListener)
        super.onStop()
    }

    private fun handleActiveAlarmMissionExpired() {
        alarmMissionCoordinator.handleDeadline(activeAlarmMission?.occurrenceId)
    }

    private fun handleActiveAlarmMissionForceEnd(occurrenceId: String): ForceEndMissionResult {
        val result = alarmMissionCoordinator.forceEnd(occurrenceId)
        refreshActiveAlarmMission()
        return result
    }

    private fun refreshActiveAlarmMission() {
        val mission = activeAlarmMissionStore.read()
        activeAlarmMission = mission
        activeAlarmMissionLocation = mission?.let { activeMission ->
            activeAlarmMissionStore.readLastLocation(activeMission.occurrenceId)
        }
        mandatoryUpdateViewModel.setAlarmInProgress(
            activeAlarmMissionStore.hasPendingMission() || AlarmRingingRuntime.ringingOccurrenceId.value != null,
        )
    }
}
