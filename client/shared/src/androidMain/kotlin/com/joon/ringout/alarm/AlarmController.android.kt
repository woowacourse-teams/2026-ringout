package com.joon.ringout.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.analytics.AlarmAnalytics
import com.joon.ringout.analytics.AlarmSettingsAnalyticsContext
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.LegacyAlarmPreferencesMigrator
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.alarm.validateForStorage
import com.joon.ringout.data.alarmoccurrence.captureAlarmOccurrenceAccount
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.coroutineContext

@Composable
actual fun rememberAlarmController(
    onSaveCompleted: (AlarmScheduleRequest) -> Unit,
    onSaveError: (AlarmScheduleRequest, String) -> Unit,
    onError: (String) -> Unit,
): AlarmController {
    val context = LocalContext.current
    val scheduler = remember(context) { AndroidAlarmScheduler(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val permissionPreferences = remember(context) {
        context.getSharedPreferences(PERMISSION_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val pendingAction = remember { mutableStateOf<PendingAlarmAction?>(null) }
    val isScheduleInFlight = remember { mutableStateOf(false) }
    val currentOnSaveCompleted = rememberUpdatedState(onSaveCompleted)
    val currentOnSaveError = rememberUpdatedState(onSaveError)
    val currentOnError = rememberUpdatedState(onError)

    fun failPendingAction(message: String) {
        val failedRequest = (pendingAction.value as? PendingAlarmAction.Schedule)?.request
        pendingAction.value = null
        isScheduleInFlight.value = false
        if (failedRequest == null) {
            currentOnError.value(message)
        } else {
            currentOnSaveError.value(failedRequest, message)
        }
    }

    fun requestFullScreenPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.canUseFullScreenIntent()) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }
    }

    fun scheduleNow(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    ) {
        if (!context.hasMissionFineLocationPermission()) {
            failPendingAction(EXACT_LOCATION_PERMISSION_ERROR)
            return
        }
        if (!context.isMissionLocationEnabled()) {
            failPendingAction(LOCATION_SERVICES_ERROR)
            return
        }
        pendingAction.value = null
        isScheduleInFlight.value = true
        coroutineScope.launch {
            try {
                scheduler.schedule(request, analyticsContext)
                pendingAction.value = null
                currentOnSaveCompleted.value(request)
                requestFullScreenPermissionIfNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failPendingAction(error.message ?: "알람 예약 중 오류가 발생했습니다.")
            } finally {
                isScheduleInFlight.value = false
            }
        }
    }

    fun enableNow(alarmId: String) {
        val permissionError = when {
            needsNotificationPermission(context) -> NOTIFICATION_PERMISSION_ERROR
            !context.hasMissionFineLocationPermission() -> EXACT_LOCATION_PERMISSION_ERROR
            !context.isMissionLocationEnabled() -> LOCATION_SERVICES_ERROR
            !Settings.canDrawOverlays(context) -> OVERLAY_PERMISSION_ERROR
            !canScheduleExactAlarms(context) -> EXACT_ALARM_PERMISSION_ERROR
            else -> null
        }
        if (permissionError != null) {
            failPendingAction(permissionError)
            return
        }
        pendingAction.value = null
        coroutineScope.launch {
            try {
                scheduler.setEnabled(alarmId, true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failPendingAction(error.message ?: "알람 상태를 변경하지 못했습니다.")
            }
        }
    }

    fun completePendingAction() {
        when (val action = pendingAction.value) {
            is PendingAlarmAction.Schedule -> scheduleNow(action.request, action.analyticsContext)
            is PendingAlarmAction.Enable -> enableNow(action.alarmId)
            null -> Unit
        }
    }

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingAction.value == null) return@rememberLauncherForActivityResult
        if (canScheduleExactAlarms(context)) {
            completePendingAction()
        } else {
            failPendingAction(EXACT_ALARM_PERMISSION_ERROR)
        }
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingAction.value == null) return@rememberLauncherForActivityResult
        if (!Settings.canDrawOverlays(context)) {
            failPendingAction(OVERLAY_PERMISSION_ERROR)
        } else if (canScheduleExactAlarms(context)) {
            completePendingAction()
        } else {
            exactAlarmPermissionLauncher.launch(exactAlarmPermissionIntent(context))
        }
    }
    val locationServicesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingAction.value == null) return@rememberLauncherForActivityResult
        if (!context.isMissionLocationEnabled()) {
            failPendingAction(LOCATION_SERVICES_ERROR)
        } else if (!Settings.canDrawOverlays(context)) {
            overlayPermissionLauncher.launch(overlayPermissionIntent(context))
        } else if (canScheduleExactAlarms(context)) {
            completePendingAction()
        } else {
            exactAlarmPermissionLauncher.launch(exactAlarmPermissionIntent(context))
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (pendingAction.value == null) return@rememberLauncherForActivityResult
        val fineLocationGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                context.hasMissionFineLocationPermission()
        if (!fineLocationGranted) {
            failPendingAction(EXACT_LOCATION_PERMISSION_ERROR)
        } else if (!context.isMissionLocationEnabled()) {
            locationServicesLauncher.launch(missionLocationSettingsIntent())
        } else if (!Settings.canDrawOverlays(context)) {
            overlayPermissionLauncher.launch(overlayPermissionIntent(context))
        } else if (canScheduleExactAlarms(context)) {
            completePendingAction()
        } else {
            exactAlarmPermissionLauncher.launch(exactAlarmPermissionIntent(context))
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingAction.value == null) return@rememberLauncherForActivityResult
        if (!granted) {
            failPendingAction(NOTIFICATION_PERMISSION_ERROR)
        } else if (!context.hasMissionFineLocationPermission()) {
            locationPermissionLauncher.launch(locationPermissions)
        } else if (!context.isMissionLocationEnabled()) {
            locationServicesLauncher.launch(missionLocationSettingsIntent())
        } else if (!Settings.canDrawOverlays(context)) {
            overlayPermissionLauncher.launch(overlayPermissionIntent(context))
        } else if (canScheduleExactAlarms(context)) {
            completePendingAction()
        } else {
            exactAlarmPermissionLauncher.launch(exactAlarmPermissionIntent(context))
        }
    }

    fun continuePermissionChain() {
        when {
            needsNotificationPermission(context) -> {
                if (NotificationPermissionPrompt.tryAcquire(context)) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    failPendingAction(NOTIFICATION_PERMISSION_ERROR)
                }
            }
            !context.hasMissionFineLocationPermission() -> {
                locationPermissionLauncher.launch(locationPermissions)
            }
            !context.isMissionLocationEnabled() -> {
                locationServicesLauncher.launch(missionLocationSettingsIntent())
            }
            !Settings.canDrawOverlays(context) -> {
                overlayPermissionLauncher.launch(overlayPermissionIntent(context))
            }
            !canScheduleExactAlarms(context) -> {
                exactAlarmPermissionLauncher.launch(exactAlarmPermissionIntent(context))
            }
            else -> completePendingAction()
        }
    }

    LaunchedEffect(scheduler) {
        try {
            scheduler.rescheduleAll()
            cancelAlarmRescheduleRetry(context.applicationContext)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentOnError.value(error.message ?: "저장된 알람을 다시 예약하지 못했습니다.")
            scheduleAlarmRescheduleRetry(
                context = context.applicationContext,
                attempt = 1,
            )
        }
    }

    return remember(
        scheduler,
        notificationPermissionLauncher,
        locationPermissionLauncher,
        locationServicesLauncher,
        overlayPermissionLauncher,
        exactAlarmPermissionLauncher,
        coroutineScope,
    ) {
        AlarmController(
            schedule = { request, analyticsContext ->
                val canStartSchedule =
                    !isScheduleInFlight.value &&
                        pendingAction.value !is PendingAlarmAction.Schedule
                if (canStartSchedule) {
                    pendingAction.value = PendingAlarmAction.Schedule(request, analyticsContext)
                    continuePermissionChain()
                }
            },
            setEnabled = { alarmId, enabled ->
                if (enabled) {
                    pendingAction.value = PendingAlarmAction.Enable(alarmId)
                    continuePermissionChain()
                } else {
                    val action = pendingAction.value
                    if (action is PendingAlarmAction.Enable && action.alarmId == alarmId) {
                        pendingAction.value = null
                    }
                    coroutineScope.launch {
                        try {
                            scheduler.setEnabled(alarmId, false)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            currentOnError.value(
                                error.message ?: "알람 상태를 변경하지 못했습니다.",
                            )
                        }
                    }
                }
            },
            deleteAlarm = { alarmId ->
                val action = pendingAction.value
                if (action is PendingAlarmAction.Enable && action.alarmId == alarmId) {
                    pendingAction.value = null
                }
                coroutineScope.launch {
                    try {
                        scheduler.delete(alarmId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        currentOnError.value(
                            error.message ?: "알람을 삭제하지 못했습니다.",
                        )
                    }
                }
            },
            savedAlarms = scheduler.observeAll().retryWhen { error, attempt ->
                if (error is CancellationException) return@retryWhen false
                if (attempt == 0L) {
                    currentOnError.value(
                        error.message ?: "저장된 알람을 불러오지 못했습니다.",
                    )
                }
                delay(AlarmLoadRetryDelayMillis)
                true
            },
            ensureFullScreenAccess = {
                val hasRequestedOnFirstLaunch = permissionPreferences.getBoolean(
                    KEY_INITIAL_OVERLAY_PERMISSION_REQUESTED,
                    false,
                )
                if (!hasRequestedOnFirstLaunch) {
                    permissionPreferences.edit()
                        .putBoolean(KEY_INITIAL_OVERLAY_PERMISSION_REQUESTED, true)
                        .apply()
                }
                if (!hasRequestedOnFirstLaunch && !Settings.canDrawOverlays(context)) {
                    overlayPermissionLauncher.launch(overlayPermissionIntent(context))
                }
            },
        )
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun exactAlarmPermissionIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun overlayPermissionIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
private const val NOTIFICATION_PERMISSION_ERROR =
    "알람 화면과 소리를 표시하려면 알림 권한이 필요합니다."
private const val EXACT_LOCATION_PERMISSION_ERROR =
    "목적지 도착 확인을 위해 정확한 위치 권한이 필요합니다."
private const val LOCATION_SERVICES_ERROR = "목적지 도착 확인을 위해 기기 위치를 켜 주세요."
private const val OVERLAY_PERMISSION_ERROR =
    "화면이 켜져 있어도 전체 화면 알람을 표시하려면 " +
        "'다른 앱 위에 표시' 권한이 필요합니다."
private const val EXACT_ALARM_PERMISSION_ERROR = "정확한 알람 권한을 허용해 주세요."
private const val PERMISSION_PREFERENCES_NAME = "ringout_permission_prompts"
private const val KEY_INITIAL_OVERLAY_PERMISSION_REQUESTED = "initial_overlay_permission_requested"
private const val AlarmLoadRetryDelayMillis = 1_000L

private sealed interface PendingAlarmAction {
    data class Schedule(
        val request: AlarmScheduleRequest,
        val analyticsContext: AlarmSettingsAnalyticsContext,
    ) : PendingAlarmAction

    data class Enable(val alarmId: String) : PendingAlarmAction
}

private val AlarmSchedulerMutationMutex = Mutex()

internal class AndroidAlarmScheduler(
    private val dataSource: AlarmDataSource,
    private val alarmGateway: AndroidAlarmGateway,
    private val ensureMigrated: suspend () -> Unit = {},
    private val analytics: AndroidAlarmCreationAnalytics? = null,
    private val isSchedulingAllowed: () -> Boolean = { true },
    private val captureOwnerAccountId: suspend () -> String? = { null },
    private val isReservationInUse: (String) -> Boolean = { false },
    private val getAllForMaintenance: (suspend () -> List<SavedAlarmSchedule>)? = null,
    private val getForReauthentication: suspend (String) -> SavedAlarmSchedule? = { null },
) {

    constructor(
        context: Context,
        dataSource: AlarmDataSource = androidAlarmDataSource(context),
        legacyMigrator: LegacyAlarmPreferencesMigrator =
            LegacyAlarmPreferencesMigrator(context, dataSource),
    ) : this(
        dataSource = dataSource,
        alarmGateway = AndroidAlarmManagerGateway(context),
        ensureMigrated = {
            if (!isAlarmSchedulingBlocked(context) && getAuthSession().identity.value == null) {
                createSecureTokenStorage(context).restoreAuthSession(getAuthSession())
            }
            legacyMigrator.ensureMigrated()
        },
        analytics = runCatching { AndroidAlarmAnalyticsAdapter(AlarmAnalytics(context)) }
            .getOrNull(),
        isSchedulingAllowed = { !isAlarmSchedulingBlocked(context) },
        captureOwnerAccountId = {
            captureAlarmOccurrenceAccount(
                createSecureTokenStorage(context),
                getAuthSession(),
            )?.ownerAccountId
        },
        isReservationInUse = { id ->
            AlarmRingingRuntime.isRinging(id) ||
                ActiveAlarmMissionStore(context).readStoredMission()?.let { stored ->
                    stored.mission.alarmId == id &&
                        (stored.phase == AlarmMissionPhase.Tracking || stored.phase == AlarmMissionPhase.RetryPendingRing)
                } == true
        },
        getAllForMaintenance = {
            RoomAlarmDataSource(getRingoutDatabase(context).alarmDao()).getAll()
        },
        getForReauthentication = { id ->
            val session = getAuthSession()
            if (session.state.value == AuthSessionState.ReauthenticationRequired) {
                RoomAlarmDataSource(getRingoutDatabase(context).alarmDao()).getById(id)
                    ?.takeIf { session.state.value == AuthSessionState.ReauthenticationRequired }
            } else null
        },
    )

    suspend fun schedule(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext = AlarmSettingsAnalyticsContext(),
    ): Unit = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        check(isSchedulingAllowed()) { "로그아웃 처리 중에는 알람을 예약할 수 없습니다." }
        request.validateForStorage()
        val previous = dataSource.getById(request.id)
        val ownerAccountId = previous?.request?.ownerAccountId
            ?: if (previous == null) captureOwnerAccountId() else null
        val versionedRequest = request.copy(
            ownerAccountId = ownerAccountId,
            remoteAlarmId = previous?.request?.remoteAlarmId
                ?: if (previous == null) UUID.randomUUID().toString() else null,
            pendingReservationId = previous?.request?.pendingReservationId,
        )
            .withScheduleVersion(previous?.request)
        val replacement = SavedAlarmSchedule(
            request = versionedRequest,
            enabled = true,
        )
        if (previous?.enabled == true) {
            dataSource.replace(previous.copy(enabled = false))
        }
        try {
            scheduleNext(versionedRequest, afterMillis = alarmGateway.currentTimeMillis())
            dataSource.replaceUserAlarm(replacement, previous)
        } catch (error: Exception) {
            restorePreviousAlarm(previous, request.id, error)
            throw error
        }
        if (previous == null) {
            analytics?.recordAlarmCreated(request, analyticsContext)
        } else {
            analytics?.recordAlarmUpdated(request, analyticsContext)
        }
    }

    suspend fun setEnabled(
        alarmId: String,
        enabled: Boolean,
    ): Unit = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        val storedAlarm = dataSource.getById(alarmId) ?: return@withLock
        if (enabled) {
            check(isSchedulingAllowed()) { "로그아웃 처리 중에는 알람을 예약할 수 없습니다." }
            try {
                scheduleNext(storedAlarm.request, afterMillis = alarmGateway.currentTimeMillis())
                check(dataSource.setEnabled(alarmId, true)) {
                    "저장된 알람을 찾지 못했습니다."
                }
            } catch (error: Exception) {
                runCatching { dataSource.setEnabled(alarmId, false) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                cancel(alarmId)
                throw error
            }
        } else {
            dataSource.setEnabled(alarmId, false)
            cancel(alarmId)
        }
    }

    suspend fun delete(alarmId: String): Unit = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        val storedAlarm = dataSource.getById(alarmId)
        cancel(alarmId)
        try {
            dataSource.deleteUserAlarm(alarmId)
        } catch (error: Exception) {
            if (storedAlarm?.enabled == true) {
                runCatching {
                    scheduleNext(storedAlarm.request, afterMillis = alarmGateway.currentTimeMillis())
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    suspend fun ringTriggeredIfCurrent(
        alarmId: String,
        expectedFingerprint: String?,
        legacySnapshotMatches: (AlarmScheduleRequest) -> Boolean = { false },
        startRinging: suspend (AlarmScheduleRequest) -> Unit,
    ): Boolean = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        if (!isSchedulingAllowed()) return@withLock false
        val storedAlarm = (dataSource.getById(alarmId) ?: getForReauthentication(alarmId))
            ?.takeIf(SavedAlarmSchedule::enabled)
            ?: return@withLock false
        val request = storedAlarm.request
        if (
            expectedFingerprint != null &&
            expectedFingerprint != request.scheduleFingerprint() &&
            !(request.scheduleVersion == 1L && legacySnapshotMatches(request))
        ) {
            return@withLock false
        }
        if (!isSchedulingAllowed()) return@withLock false
        startRinging(request)
        if (request.repeatEnabled && request.selectedDays.isNotEmpty()) {
            scheduleNext(request, afterMillis = alarmGateway.currentTimeMillis() + 60_000L)
        } else {
            check(dataSource.setEnabled(alarmId, false)) {
                "발화한 알람의 상태를 갱신하지 못했습니다."
            }
        }
        true
    }

    suspend fun rescheduleAll(): Unit = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        if (!isSchedulingAllowed()) return@withLock
        var firstFailure: Exception? = null
        val ownerAccountId = captureOwnerAccountId()
        if (ownerAccountId != null) {
            getAllForMaintenance?.invoke()?.forEach { stored ->
                val owner = stored.request.ownerAccountId
                if (owner != null && owner != ownerAccountId) cancel(stored.request.id)
            }
        }
        dataSource.getAll().forEach { stored ->
            coroutineContext.ensureActive()
            try {
                val request = stored.request
                val pendingId = request.pendingReservationId
                val scheduledRequest = if (
                    pendingId != null && request.ownerAccountId != null && !isReservationInUse(request.id)
                ) {
                    cancel(request.id)
                    check(dataSource.migrateId(request.id, pendingId)) {
                        "저장된 알람 예약 ID를 전환하지 못했습니다."
                    }
                    request.copy(id = pendingId, pendingReservationId = null)
                } else {
                    request
                }
                if (stored.enabled) {
                    scheduleNext(scheduledRequest, afterMillis = alarmGateway.currentTimeMillis())
                } else {
                    cancel(scheduledRequest.id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val previousFailure = firstFailure
                if (previousFailure == null) {
                    firstFailure = error
                } else {
                    previousFailure.addSuppressed(error)
                }
            }
        }
        firstFailure?.let { error ->
            throw IllegalStateException("일부 저장된 알람을 다시 예약하지 못했습니다.", error)
        }
    }

    suspend fun applyServerChange(
        alarmId: String,
        apply: suspend () -> Boolean,
    ): Boolean = AlarmSchedulerMutationMutex.withLock {
        if (!isSchedulingAllowed() || isReservationInUse(alarmId)) return@withLock false
        val previous = dataSource.getById(alarmId) ?: return@withLock apply()
        cancel(alarmId)
        if (!previous.enabled) return@withLock apply()

        var applied = false
        try {
            applied = apply()
            applied
        } finally {
            if (!applied) {
                withContext(NonCancellable) {
                    if (isSchedulingAllowed() && dataSource.getById(alarmId) == previous) {
                        scheduleNext(previous.request, afterMillis = alarmGateway.currentTimeMillis())
                    }
                }
            }
        }
    }

    suspend fun cancelAllForLogout(): Unit = AlarmSchedulerMutationMutex.withLock {
        ensureMigrated()
        val alarms = getAllForMaintenance?.invoke() ?: dataSource.getAll()
        // One SQL update in Room. Never restore enabled snapshots after cancellation failures.
        dataSource.disableAll()
        var firstFailure: Exception? = null
        alarms.forEach { alarm ->
            try {
                cancel(alarm.request.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                firstFailure?.addSuppressed(error) ?: run { firstFailure = error }
            }
        }
        firstFailure?.let { throw it }
    }

    fun observeAll(): Flow<List<SavedAlarmSchedule>> = flow {
        ensureMigrated()
        emitAll(dataSource.observeAll())
    }

    private suspend fun restorePreviousAlarm(
        previous: SavedAlarmSchedule?,
        alarmId: String,
        schedulingError: Exception,
    ) {
        runCatching {
            cancel(alarmId)
            if (previous == null) {
                dataSource.delete(alarmId)
            } else {
                val restoreFailure = if (previous.enabled) {
                    runCatching {
                        scheduleNext(previous.request, afterMillis = alarmGateway.currentTimeMillis())
                    }.exceptionOrNull()
                } else {
                    null
                }
                if (restoreFailure == null) {
                    dataSource.replace(previous)
                } else {
                    dataSource.replace(previous.copy(enabled = false))
                    throw restoreFailure
                }
            }
        }.exceptionOrNull()
            ?.takeUnless { restoreError -> restoreError === schedulingError }
            ?.let(schedulingError::addSuppressed)
    }

    private fun scheduleNext(request: AlarmScheduleRequest, afterMillis: Long) {
        val triggerAtMillis = calculateNextTrigger(request, afterMillis)
        alarmGateway.schedule(request, triggerAtMillis)
    }

    private fun cancel(alarmId: String) {
        alarmGateway.cancel(alarmId)
    }

    private fun calculateNextTrigger(request: AlarmScheduleRequest, afterMillis: Long): Long {
        request.validateForStorage()
        val parts = request.time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val alarmTime = LocalTime.of(hour, minute)
        val zone = ZoneId.systemDefault()
        val after = Instant.ofEpochMilli(afterMillis).atZone(zone)
        val repeatDays = if (request.repeatEnabled) {
            request.selectedDays.map { day ->
                requireNotNull(DAY_OF_WEEK_BY_KOREAN[day]) {
                    "지원하지 않는 반복 요일입니다: $day"
                }
            }.toSet()
        } else {
            emptySet()
        }

        if (repeatDays.isEmpty()) {
            var candidate = after.toLocalDate().atTime(alarmTime).atZone(zone)
            if (candidate.toInstant().toEpochMilli() <= afterMillis) {
                candidate = candidate.plusDays(1)
            }
            return candidate.toInstant().toEpochMilli()
        }

        for (dayOffset in 0..7L) {
            val candidate = after.toLocalDate()
                .plusDays(dayOffset)
                .atTime(alarmTime)
                .atZone(zone)
            if (
                candidate.dayOfWeek in repeatDays &&
                candidate.toInstant().toEpochMilli() > afterMillis
            ) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        error("다음 알람 시각을 계산하지 못했습니다.")
    }

    private companion object {
        val DAY_OF_WEEK_BY_KOREAN = mapOf(
            "월" to DayOfWeek.MONDAY,
            "화" to DayOfWeek.TUESDAY,
            "수" to DayOfWeek.WEDNESDAY,
            "목" to DayOfWeek.THURSDAY,
            "금" to DayOfWeek.FRIDAY,
            "토" to DayOfWeek.SATURDAY,
            "일" to DayOfWeek.SUNDAY,
        )
    }
}

private fun androidAlarmDataSource(context: Context): AlarmDataSource {
    val session = getAuthSession()
    val tokenStorage = createSecureTokenStorage(context)
    val accountProvider = DestinationAccountProvider(session) {
        captureAlarmOccurrenceAccount(tokenStorage, session)?.ownerAccountId
    }
    return RoomAlarmDataSource(getRingoutDatabase(context), accountProvider)
}

internal interface AndroidAlarmGateway {
    fun currentTimeMillis(): Long

    fun schedule(
        request: AlarmScheduleRequest,
        triggerAtMillis: Long,
    )

    fun cancel(alarmId: String)
}

internal interface AndroidAlarmCreationAnalytics {
    fun recordAlarmCreated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    )

    fun recordAlarmUpdated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    )
}

private class AndroidAlarmAnalyticsAdapter(
    private val analytics: AlarmAnalytics,
) : AndroidAlarmCreationAnalytics {
    override fun recordAlarmCreated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    ) {
        analytics.recordAlarmCreated(request, analyticsContext)
    }

    override fun recordAlarmUpdated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    ) {
        analytics.recordAlarmUpdated(request, analyticsContext)
    }
}

private class AndroidAlarmManagerGateway(
    private val context: Context,
) : AndroidAlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun schedule(
        request: AlarmScheduleRequest,
        triggerAtMillis: Long,
    ) {
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmRuntime.ACTION_RING
            putAlarmExtras(request)
            putExtra(AlarmRuntime.EXTRA_TRIGGER_AT_EPOCH_MILLIS, triggerAtMillis)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            request.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            context,
            request.id.hashCode() xor Int.MIN_VALUE,
            AlarmRingingActivity.intent(context, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            operation,
        )
    }

    override fun cancel(alarmId: String) {
        val operation = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmRuntime.ACTION_RING
                data = Uri.parse("ringout://alarm/${Uri.encode(alarmId)}")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        operation?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        PendingIntent.getActivity(
            context,
            alarmId.hashCode() xor Int.MIN_VALUE,
            Intent(context, AlarmRingingActivity::class.java).apply {
                action = AlarmRuntime.ACTION_RING
                data = Uri.parse("ringout://alarm/${Uri.encode(alarmId)}")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }
}
