package com.joon.ringout.push

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PushNotificationsTest {
    @Test
    fun `모임 알림은 표시하지 않고 일반 알림은 계속 표시한다`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = requireNotNull(RemoteMessage(Bundle().apply {
            putString("gcm.n.e", "1")
            putString("gcm.n.title", "링아웃")
            putString("gcm.n.body", "알림 내용")
        }).notification)

        PushNotifications.show(context, notification, mapOf("roomId" to "41", "activityDate" to "2026-11-16"))
        PushNotifications.show(context, notification, mapOf("roomId" to "invalid"))
        assertEquals(0, shadowOf(manager).allNotifications.size)

        PushNotifications.show(context, notification, emptyMap())
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
