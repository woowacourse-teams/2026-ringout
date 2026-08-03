package com.joon.ringout.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

class AlarmRingingService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isActivelyRinging = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val ringingSessionStore by lazy {
        AlarmRingingSessionStore(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmRuntime.ACTION_STOP) {
            val occurrenceId = intent.getStringExtra(
                AlarmRuntime.EXTRA_OCCURRENCE_ID,
            ) ?: return START_NOT_STICKY
            if (!ringingSessionStore.clearIfCurrent(occurrenceId)) {
                return START_NOT_STICKY
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            isActivelyRinging = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != AlarmRuntime.ACTION_RING) return START_NOT_STICKY
        val occurrenceId = intent.getStringExtra(
            AlarmRuntime.EXTRA_OCCURRENCE_ID,
        ) ?: return START_NOT_STICKY
        val missionCoordinator = AlarmMissionCoordinator(applicationContext)
        val isRetryAlarm = intent.hasExtra(
            AlarmRuntime.EXTRA_RETRY_SOURCE_OCCURRENCE_ID,
        )
        if (isRetryAlarm && !missionCoordinator.isExpectedRetryAlarm(intent)) {
            if (!isActivelyRinging) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (!ringingSessionStore.markRinging(occurrenceId)) {
            return START_NOT_STICKY
        }

        stopRingingResources()
        val notification = createRingingNotification(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isActivelyRinging = true
        acquireWakeLock()
        startAlarmSound(intent)
        startVibration()
        val retryAlarmConfirmed = missionCoordinator.confirmRetryAlarmStarted(intent)
        if (isRetryAlarm && !retryAlarmConfirmed) {
            ringingSessionStore.clearIfCurrent(occurrenceId)
            stopRingingResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            isActivelyRinging = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (retryAlarmConfirmed && Settings.canDrawOverlays(applicationContext)) {
            runCatching {
                startActivity(
                    AlarmRingingActivity.intentFromRuntime(
                        context = applicationContext,
                        source = intent,
                    ),
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRingingResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isActivelyRinging = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createRingingNotification(intent: Intent): Notification {
        val alarmId = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_ID).orEmpty()
        val alarmTime = intent.getStringExtra(AlarmRuntime.EXTRA_ALARM_TIME).orEmpty()
        val destination = intent.getStringExtra(AlarmRuntime.EXTRA_DESTINATION_NAME).orEmpty()
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            AlarmRingingActivity.intentFromRuntime(this, intent),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            AlarmRingingActivity.dismissIntentFromRuntime(this, intent),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentText = destination.takeIf(String::isNotBlank)
            ?.let { "$it 미션을 시작할 시간입니다." }
            ?: "러닝 미션을 시작할 시간입니다."

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (alarmTime.isBlank()) "알람이 울리고 있어요" else "$alarmTime 알람")
            .setContentText(contentText)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "알람 끄기",
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "알람 울림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "예약한 알람을 전체 화면으로 표시합니다."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Ringout:AlarmRinging",
        ).apply {
            acquire(MAX_RING_DURATION_MILLIS)
        }
    }

    private fun requestAlarmAudioFocus() {
        val attributes = alarmAudioAttributes()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
            .also(audioManager::requestAudioFocus)
    }

    private fun startAlarmSound(intent: Intent) {
        val hasConfiguredSound = intent.getBooleanExtra(AlarmRuntime.EXTRA_HAS_SOUND_URI, false)
        val configuredSound = intent.getStringExtra(AlarmRuntime.EXTRA_SOUND_URI)
        if (hasConfiguredSound && configuredSound.isNullOrBlank()) return
        val soundUri = configuredSound
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return

        requestAlarmAudioFocus()
        mediaPlayer = createMediaPlayer(soundUri)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?.takeUnless { it == soundUri }
                ?.let(::createMediaPlayer)
    }

    private fun createMediaPlayer(soundUri: Uri): MediaPlayer? =
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes())
                setDataSource(this@AlarmRingingService, soundUri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 700L, 350L), 0),
            )
        }
    }

    private fun stopRingingResources() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
    }

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private companion object {
        const val CHANNEL_ID = "ringout_alarm_ringing"
        const val NOTIFICATION_ID = 7_201
        const val MAX_RING_DURATION_MILLIS = 35 * 60 * 1_000L
    }
}
