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
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.LegacyAlarmPreferencesMigrator
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.alarm.validateForStorage
import com.joon.ringout.data.database.getRingoutDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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

    fun scheduleNow(request: AlarmScheduleRequest) {
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
                scheduler.schedule(request)
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
            is PendingAlarmAction.Schedule -> scheduleNow(action.request)
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
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            schedule = { request ->
                val canStartSchedule =
                    !isScheduleInFlight.value &&
                        pendingAction.value !is PendingAlarmAction.Schedule
                if (canStartSchedule) {
                    pendingAction.value = PendingAlarmAction.Schedule(request)
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
    data class Schedule(val request: AlarmScheduleRequest) : PendingAlarmAction

    data class Enable(val alarmId: String) : PendingAlarmAction
}

private val AlarmSchedulerMutationMutex = Mutex()

internal class AndroidAlarmScheduler(
    private val context: Context,
    private val dataSource: AlarmDataSource = RoomAlarmDataSource(
        getRingoutDatabase(context).alarmDao(),
    ),
    private val legacyMigrator: LegacyAlarmPreferencesMigrator =
        LegacyAlarmPreferencesMigrator(context, dataSource),
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val analytics = runCatching { AlarmAnalytics(context) }.getOrNull()

    suspend fun schedule(request: AlarmScheduleRequest): Unit = AlarmSchedulerMutationMutex.withLock {
        legacyMigrator.ensureMigrated()
        request.validateForStorage()
        val previous = dataSource.getById(request.id)
        val replacement = SavedAlarmSchedule(
            request = request,
            enabled = previous?.enabled ?: true,
        )
        if (replacement.enabled) {
            try {
                scheduleNext(request, afterMillis = System.currentTimeMillis())
                dataSource.replace(replacement)
            } catch (error: Exception) {
                restorePreviousAlarm(previous, request.id, error)
                throw error
            }
        } else {
            dataSource.replace(replacement)
            cancel(request.id)
        }
        if (previous == null) {
            analytics?.recordAlarmCreated(
                alarmId = request.id,
                repeatEnabled = request.repeatEnabled,
                repeatDayCount = request.selectedDays.distinct().size,
            )
        }
    }

    suspend fun setEnabled(
        alarmId: String,
        enabled: Boolean,
    ): Unit = AlarmSchedulerMutationMutex.withLock {
        legacyMigrator.ensureMigrated()
        val storedAlarm = dataSource.getById(alarmId) ?: return@withLock
        if (enabled) {
            try {
                scheduleNext(storedAlarm.request, afterMillis = System.currentTimeMillis())
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
        legacyMigrator.ensureMigrated()
        val storedAlarm = dataSource.getById(alarmId)
        cancel(alarmId)
        try {
            dataSource.delete(alarmId)
        } catch (error: Exception) {
            if (storedAlarm?.enabled == true) {
                runCatching {
                    scheduleNext(storedAlarm.request, afterMillis = System.currentTimeMillis())
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    suspend fun ringTriggeredIfCurrent(
        alarmId: String,
        expectedFingerprint: String?,
        startRinging: suspend (AlarmScheduleRequest) -> Unit,
    ): Boolean = AlarmSchedulerMutationMutex.withLock {
        legacyMigrator.ensureMigrated()
        val storedAlarm = dataSource.getById(alarmId)
            ?.takeIf(SavedAlarmSchedule::enabled)
            ?: return@withLock false
        val request = storedAlarm.request
        if (
            expectedFingerprint != null &&
            expectedFingerprint != request.scheduleFingerprint()
        ) {
            return@withLock false
        }
        startRinging(request)
        if (request.repeatEnabled && request.selectedDays.isNotEmpty()) {
            scheduleNext(request, afterMillis = System.currentTimeMillis() + 60_000L)
        } else {
            check(dataSource.setEnabled(alarmId, false)) {
                "발화한 알람의 상태를 갱신하지 못했습니다."
            }
        }
        true
    }

    suspend fun rescheduleAll(): Unit = AlarmSchedulerMutationMutex.withLock {
        legacyMigrator.ensureMigrated()
        var firstFailure: Exception? = null
        dataSource.getAll().forEach { stored ->
            coroutineContext.ensureActive()
            try {
                if (stored.enabled) {
                    scheduleNext(stored.request, afterMillis = System.currentTimeMillis())
                } else {
                    cancel(stored.request.id)
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

    fun observeAll(): Flow<List<SavedAlarmSchedule>> = flow {
        legacyMigrator.ensureMigrated()
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
                if (previous.enabled) {
                    scheduleNext(previous.request, afterMillis = System.currentTimeMillis())
                }
                dataSource.replace(previous)
            }
        }.exceptionOrNull()?.let(schedulingError::addSuppressed)
    }

    private fun scheduleNext(request: AlarmScheduleRequest, afterMillis: Long) {
        val triggerAtMillis = calculateNextTrigger(request, afterMillis)
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmRuntime.ACTION_RING
            putAlarmExtras(request)
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

    private fun cancel(alarmId: String) {
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
