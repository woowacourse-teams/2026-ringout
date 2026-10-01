package com.joon.ringout.data.alarmoccurrence

import android.content.Context
import android.util.Log
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.data.connectivity.AndroidNetworkMonitor
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** 앱, 알람 서비스, Worker가 동일한 저널과 전송기를 공유한다. */
internal class AndroidAlarmOccurrenceRuntime private constructor(private val context: Context) {
    private val tokenStorage = createSecureTokenStorage(context)
    private val authSession = getAuthSession()
    private val dao = getRingoutDatabase(context).alarmOccurrenceSyncDao()
    private val journal = AndroidAlarmOccurrenceJournal(
        context.getSharedPreferences("alarm_occurrence_pending", Context.MODE_PRIVATE),
    )
    private val recorder = AlarmOccurrenceEventRecorder(dao)
    private val syncer = AlarmOccurrenceSyncer(dao, getRingoutHttpClient(), tokenStorage, authSession)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationMutex = Mutex()
    private var initialized = false

    private suspend fun initialize() = initializationMutex.withLock {
        if (initialized) return@withLock
        // UI를 열지 않고 알람/Worker로 프로세스가 시작되어도 저장된 인증을 복원한다.
        tokenStorage.restoreAuthSession(authSession)
        AlarmOccurrenceSyncCoordinator(syncer, dao, authSession, AndroidNetworkMonitor(context),
            onStorageFailure = { Log.e(Tag, "알람 전송 대기열 조회 실패", it) }).start(scope)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authSession.identity.drop(1).collect { identity ->
                if (identity != null) AlarmOccurrenceSyncWorker.enqueue(context)
            }
        }
        initialized = true
    }

    fun recordRinging(
        alarmId: String,
        ringingId: String,
        scheduleVersion: Long,
        scheduledAt: Long?,
        sourceRingingId: String?,
        at: Long,
    ) {
        // 토큰 자체는 저널에 저장하지 않는다. 네트워크 없이 발생 순간의 계정 ID만 고정한다.
        val owner = if (sourceRingingId == null) tokenStorage.readSnapshot()?.accessToken
            ?.let(::alarmOccurrenceTokenOwner) else null
        append(CapturedAlarmOccurrenceEvent.Rang(ringingId, alarmId, scheduleVersion,
            scheduledAt, at, owner, sourceRingingId, UUID.randomUUID().toString()))
    }

    fun recordDismissal(ringingId: String, at: Long) =
        append(CapturedAlarmOccurrenceEvent.Dismissed(ringingId, at))

    fun recordTerminal(ringingId: String, at: Long?, arrived: Boolean) =
        append(CapturedAlarmOccurrenceEvent.Terminal(ringingId, at, arrived))

    private fun append(event: CapturedAlarmOccurrenceEvent) {
        journal.append(event)
        // KEEP이면 실행 중인 Worker가 종료되는 순간 추가된 이벤트를 놓칠 수 있다.
        AlarmOccurrenceSyncWorker.enqueue(context)
        drainInProcess()
    }

    private fun drainInProcess() {
        scope.launch {
            try {
                initialize()
                journal.drain(recorder::record)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(Tag, "알람 이벤트를 보존했습니다. 백그라운드에서 재시도합니다.", error)
            }
        }
    }

    suspend fun flush(): Long? {
        initialize()
        journal.drain(recorder::record)
        return syncer.flush()
    }

    companion object {
        private const val Tag = "AlarmOccurrence"
        @Volatile private var instance: AndroidAlarmOccurrenceRuntime? = null

        fun get(context: Context): AndroidAlarmOccurrenceRuntime = instance ?: synchronized(this) {
            instance ?: AndroidAlarmOccurrenceRuntime(context.applicationContext).also {
                instance = it
                // Worker가 시작시킨 프로세스에서는 기존 작업을 유지해 무한 체인을 만들지 않는다.
                AlarmOccurrenceSyncWorker.enqueue(context, recovery = true)
                it.drainInProcess()
            }
        }
    }
}
