package com.joon.ringout.alarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.joon.ringout.RingoutTheme
import com.joon.ringout.SystemBarAppearanceEffect
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.ringing.AlarmRingingScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {
    private val alarmMissionCoordinator by lazy {
        AlarmMissionCoordinator(applicationContext)
    }
    private val ringingSessionStore by lazy {
        AlarmRingingSessionStore(applicationContext)
    }
    private var isActivityResumed = false
    private var isStopPending = false
    private var isStopHandled = false
    private var isDismissRequested = false
    private var isPrerequisiteRequestInFlight = false
    private val missionLocationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isPrerequisiteRequestInFlight = false
        if (!isDismissRequested) return@registerForActivityResult
        if (applicationContext.isMissionLocationEnabled()) {
            stopAlarmAndOpenApp()
        } else {
            isDismissRequested = false
            showPrerequisiteMessage("제한시간을 시작하려면 기기 위치를 켜 주세요.")
        }
    }
    private val missionLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        isPrerequisiteRequestInFlight = false
        if (!isDismissRequested) return@registerForActivityResult
        if (applicationContext.hasMissionFineLocationPermission()) {
            stopAlarmAndOpenApp()
        } else {
            isDismissRequested = false
            showPrerequisiteMessage("제한시간을 시작하려면 정확한 위치 권한이 필요합니다.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        if (intent.action == AlarmRuntime.ACTION_STOP) {
            isStopPending = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val alarmTime = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_TIME).orEmpty()
        val limitMinutes = intent.getIntExtra(AlarmRuntime.EXTRA_LIMIT_MINUTES, 12)
        val destinationName = intent
            .getStringExtra(AlarmRuntime.EXTRA_DESTINATION_NAME)
            .orEmpty()
            .ifBlank { "선택한 목적지" }
        setContent {
            SystemBarAppearanceEffect(ThemeMode.Dark)
            RingoutTheme(themeMode = ThemeMode.Dark) {
                AlarmRingingScreen(
                    alarmTime = alarmTime,
                    dateText = currentDateText(),
                    limitMinutes = limitMinutes,
                    destinationName = destinationName,
                    onDismissAndNavigateClick = ::stopAlarmAndOpenApp,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            AlarmRuntime.ACTION_STOP -> {
                isStopPending = true
                consumePendingStopIfReady()
            }
            AlarmRuntime.ACTION_RING -> recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        consumePendingStopIfReady()
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    private fun consumePendingStopIfReady() {
        if (!isActivityResumed || !isStopPending) return
        isStopPending = false
        stopAlarmAndOpenApp()
    }

    private fun stopAlarmAndOpenApp() {
        if (isStopHandled) return
        isDismissRequested = true
        if (!applicationContext.hasMissionFineLocationPermission()) {
            if (!isPrerequisiteRequestInFlight) {
                isPrerequisiteRequestInFlight = true
                missionLocationPermissionLauncher.launch(MissionLocationPermissions)
            }
            return
        }
        if (!applicationContext.isMissionLocationEnabled()) {
            if (!isPrerequisiteRequestInFlight) {
                isPrerequisiteRequestInFlight = true
                missionLocationSettingsLauncher.launch(missionLocationSettingsIntent())
            }
            return
        }

        val occurrenceId = intent.getStringExtra(
            AlarmRuntime.EXTRA_OCCURRENCE_ID,
        ) ?: run {
            isDismissRequested = false
            finish()
            return
        }
        var wasCurrentRingingOccurrence = false
        val startedMission = ringingSessionStore.runIfCurrent(occurrenceId) {
            wasCurrentRingingOccurrence = true
            alarmMissionCoordinator.confirmRetryAlarmStarted(intent)
            alarmMissionCoordinator.startFrom(intent)
        }
        if (!wasCurrentRingingOccurrence) {
            isDismissRequested = false
            finish()
            return
        }
        isStopHandled = true
        startService(
            Intent(this, AlarmRingingService::class.java).apply {
                action = AlarmRuntime.ACTION_STOP
                putExtra(AlarmRuntime.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
        )
        if (startedMission?.occurrenceId != occurrenceId) {
            showPrerequisiteMessage(
                "이미 진행 중인 미션이 있어 기존 제한시간을 유지합니다.",
            )
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }
        finish()
    }

    private fun showPrerequisiteMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun currentDateText(): String =
        LocalDate.now().format(AlarmDateFormatter)

    companion object {
        private val MissionLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        private val AlarmDateFormatter =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)

        fun intent(context: Context, request: AlarmScheduleRequest): Intent =
            Intent(context, AlarmRingingActivity::class.java).apply {
                action = AlarmRuntime.ACTION_RING
                putAlarmExtras(request)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }

        fun intentFromRuntime(context: Context, source: Intent): Intent =
            Intent(context, AlarmRingingActivity::class.java).apply {
                action = AlarmRuntime.ACTION_RING
                replaceExtras(source)
                data = source.data
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }

        fun dismissIntentFromRuntime(context: Context, source: Intent): Intent {
            val alarmId = source.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID).orEmpty()
            return Intent(context, AlarmRingingActivity::class.java).apply {
                action = AlarmRuntime.ACTION_STOP
                replaceExtras(source)
                data = Uri.parse(
                    "ringout://alarm/${Uri.encode(alarmId)}/stop",
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
        }
    }
}
