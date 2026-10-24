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
import com.joon.ringout.domain.push.RoomActivityPushTarget
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.joon.ringout.MainActivity
import com.joon.ringout.R
import com.joon.ringout.data.push.AndroidFcmTokenSource
import java.util.UUID

internal object PushNotifications {
    private const val CHANNEL_ID = "ringout_push"
    const val EXTRA_REQUEST_ID = "com.joon.ringout.push.request_id"
    private const val ACTION_OPEN_FROM_PUSH = "com.joon.ringout.action.OPEN_FROM_PUSH"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun show(
        context: Context,
        payload: RemoteMessage.Notification,
        data: Map<String, String>,
    ) {
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
        val clickRequestId = UUID.randomUUID().toString()
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            action = "$ACTION_OPEN_FROM_PUSH.$clickRequestId"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_REQUEST_ID, clickRequestId)
            data[RoomActivityPushTarget.RoomIdKey]?.let { value ->
                putExtra(RoomActivityPushTarget.RoomIdKey, value)
            }
            data[RoomActivityPushTarget.ActivityDateKey]?.let { value ->
                putExtra(RoomActivityPushTarget.ActivityDateKey, value)
            }
        }
        val openApp = PendingIntent.getActivity(
            context,
            clickRequestId.hashCode(),
            notificationIntent,
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
        // Background notification+data messages are displayed by FCM; this handles foreground clicks.
        message.notification?.let { PushNotifications.show(this, it, message.data) }
    }
}
