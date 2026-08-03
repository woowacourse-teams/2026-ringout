package com.joon.ringout.presentation.alarmsetup

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDeviceAlarmSoundController(): DeviceAlarmSoundController {
    val applicationContext = LocalContext.current.applicationContext
    val sounds = remember(applicationContext) {
        loadDeviceAlarmSounds(applicationContext)
    }
    val previewPlayer = remember(applicationContext) {
        AndroidAlarmSoundPreviewPlayer(applicationContext)
    }

    DisposableEffect(previewPlayer) {
        onDispose(previewPlayer::stop)
    }

    return remember(sounds, previewPlayer) {
        DeviceAlarmSoundController(
            sounds = sounds,
            previewSound = previewPlayer::preview,
            stopSoundPreview = previewPlayer::stop,
        )
    }
}

private fun loadDeviceAlarmSounds(context: Context): List<AlarmSoundSelection> =
    buildList {
        add(
            AlarmSoundSelection(
                name = DEFAULT_ALARM_SOUND_NAME,
                uri = null,
            ),
        )

        val ringtoneManager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        val seenUris = mutableSetOf<String>()

        runCatching {
            ringtoneManager.cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = ringtoneManager
                        .getRingtoneUri(cursor.position)
                        ?.toString()
                        ?: continue
                    if (!seenUris.add(uri)) continue

                    val name = cursor
                        .getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        .orEmpty()
                        .ifBlank { FALLBACK_ALARM_SOUND_NAME }
                    add(
                        AlarmSoundSelection(
                            name = name,
                            uri = uri,
                        ),
                    )
                }
            }
        }
    }

private class AndroidAlarmSoundPreviewPlayer(
    private val context: Context,
) {
    private var ringtone: Ringtone? = null

    fun preview(selection: AlarmSoundSelection) {
        stop()

        val soundUri = selection.uri
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, soundUri)?.apply {
                audioAttributes = ALARM_AUDIO_ATTRIBUTES
                play()
            }
        }.getOrNull()
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }
}

private val ALARM_AUDIO_ATTRIBUTES: AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

private const val DEFAULT_ALARM_SOUND_NAME = "기본 알람음"
private const val FALLBACK_ALARM_SOUND_NAME = "알람음"
