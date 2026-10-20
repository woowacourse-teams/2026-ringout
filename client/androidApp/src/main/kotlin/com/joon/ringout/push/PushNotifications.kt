package com.joon.ringout.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.joon.ringout.MainActivity
import com.joon.ringout.R
import com.joon.ringout.data.push.AndroidFcmTokenSource
import java.util.UUID

internal object PushNotifications {
    private const val CHANNEL_ID = "ringout_push"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun show(context: Context, payload: RemoteMessage.Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("RingoutPush", "Notification permission is not granted; display skipped")
            return
        }
        if (!manager.areNotificationsEnabled()) {
            Log.i("RingoutPush", "Notifications are disabled; display skipped")
            return
        }
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.joon.ringout.shared.R.drawable.ic_notification_ringout)
            .setContentTitle(payload.title ?: context.getString(R.string.app_name))
            .setContentText(payload.body)
            .setStyle(Notification.BigTextStyle().bigText(payload.body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        // A unique tag keeps distinct messages, including across process restarts.
        try {
            manager.notify(UUID.randomUUID().toString(), 0, notification)
        } catch (_: SecurityException) {
            Log.i("RingoutPush", "Notification permission changed before display")
        }
    }
}

class RingoutFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AndroidFcmTokenSource.update(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM routes background notification payloads directly to the system tray.
        // Only foreground notification payloads reach this callback; data is out of scope.
        message.notification?.let { PushNotifications.show(this, it) }
    }
}
