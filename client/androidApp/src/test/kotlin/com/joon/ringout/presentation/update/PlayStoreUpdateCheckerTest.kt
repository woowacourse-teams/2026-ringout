package com.joon.ringout.presentation.update

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayStoreUpdateCheckerTest {
    @Test
    fun `스토어 앱을 열면 웹 주소를 추가로 열지 않는다`() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).get()
        val checker = PlayStoreUpdateChecker(activity, currentVersionCode = 1)

        assertTrue(checker.openStore())

        assertEquals(listOf("market://details?id=${activity.packageName}"), activity.openedUris)
    }

    @Test
    fun `스토어 앱이 없으면 같은 앱의 웹 스토어 주소로 이동한다`() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).get().apply {
            marketFailure = ActivityNotFoundException()
        }
        val checker = PlayStoreUpdateChecker(activity, currentVersionCode = 1)

        assertTrue(checker.openStore())

        assertEquals(
            listOf(
                "market://details?id=${activity.packageName}",
                "https://play.google.com/store/apps/details?id=${activity.packageName}",
            ),
            activity.openedUris,
        )
    }

    @Test
    fun `스토어와 브라우저가 모두 없으면 재시도 가능한 실패를 반환한다`() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).get().apply {
            marketFailure = ActivityNotFoundException()
            browserFailure = ActivityNotFoundException()
        }
        val checker = PlayStoreUpdateChecker(activity, currentVersionCode = 1)

        assertFalse(checker.openStore())

        activity.browserFailure = null
        assertTrue(checker.openStore())
    }

    @Test
    fun `기기 정책이 스토어 실행을 막아도 브라우저로 이동한다`() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).get().apply {
            marketFailure = SecurityException()
        }
        val checker = PlayStoreUpdateChecker(activity, currentVersionCode = 1)

        assertTrue(checker.openStore())
        assertEquals("https", activity.openedUris.last().substringBefore(":"))
    }

    @Test
    fun `기기 정책이 두 경로를 모두 막으면 앱 종료 대신 실패를 반환한다`() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).get().apply {
            marketFailure = SecurityException()
            browserFailure = SecurityException()
        }

        assertFalse(PlayStoreUpdateChecker(activity, currentVersionCode = 1).openStore())
    }

    class RecordingActivity : Activity() {
        val openedUris = mutableListOf<String>()
        var marketFailure: RuntimeException? = null
        var browserFailure: RuntimeException? = null

        override fun startActivity(intent: Intent) {
            openedUris += requireNotNull(intent.data).toString()
            val failure = if (intent.data?.scheme == "market") marketFailure else browserFailure
            failure?.let { throw it }
        }
    }
}
