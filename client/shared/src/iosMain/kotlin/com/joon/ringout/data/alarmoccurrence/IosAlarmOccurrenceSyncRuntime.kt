@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.data.connectivity.IosNetworkMonitor
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLog
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIApplicationState.UIApplicationStateBackground
import kotlin.time.Clock

/** OS가 허용한 실행 시간 안에 전송하고, 남은 outbox는 다음 앱 실행/연결 복구 시 이어 보낸다. */
internal object IosAlarmOccurrenceSyncRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ ->
        NSLog("AlarmOccurrence: sync initialization failed; retry on next activation")
    })
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val tokens = createSecureTokenStorage()
            val session = getAuthSession()
            tokens.restoreAuthSession(session)
            val dao = getRingoutDatabase().alarmOccurrenceSyncDao()
            val syncer = AlarmOccurrenceSyncer(dao, getRingoutHttpClient(), tokens, session)
            AlarmOccurrenceSyncCoordinator(syncer, dao, session, IosNetworkMonitor(),
                onStorageFailure = { NSLog("AlarmOccurrence: outbox persistence failed") },
                flush = { withBackgroundTime { syncer.flush() } },
            ).start(this).join()
        }
    }
}

private suspend fun withBackgroundTime(flush: suspend () -> Long?): Long? = coroutineScope {
    val attempt = async(start = CoroutineStart.LAZY) { flush() }
    var task = UIBackgroundTaskInvalid
    var expired = false
    fun endTask() {
        if (task != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
    }
    withContext(Dispatchers.Main) {
        task = UIApplication.sharedApplication.beginBackgroundTaskWithName("alarm-occurrence-sync") {
            expired = true
            attempt.cancel()
            endTask()
        }
        if (task == UIBackgroundTaskInvalid && UIApplication.sharedApplication.applicationState == UIApplicationStateBackground) {
            expired = true
            attempt.cancel()
        }
    }
    try {
        withTimeout(25_000) { attempt.await() }
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()
        if (expired || withContext(Dispatchers.Main) {
                UIApplication.sharedApplication.applicationState == UIApplicationStateBackground
            }) {
            // 만료 후 백그라운드 시간을 반복 요청하지 않는다. ON_RESUME에서 start()로 다시 시작한다.
            throw CancellationException("iOS background execution time ended")
        }
        Clock.System.now().toEpochMilliseconds() + 5_000
    } finally {
        attempt.cancel()
        withContext(NonCancellable + Dispatchers.Main) { endTask() }
    }
}
