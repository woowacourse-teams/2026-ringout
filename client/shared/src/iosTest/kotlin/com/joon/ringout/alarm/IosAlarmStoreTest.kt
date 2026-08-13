package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.platform.IosAlarmAuthorizationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosAlarmStoreTest {
    @Test
    fun savesNewAlarmAndPreservesEnabledStateWhenReplacingSameId() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val scheduler = FakeIosAlarmScheduler()
        val store = IosAlarmStore(dataSource, scheduler)
        val original = request()

        store.save(original)
        assertTrue(dataSource.getById(original.id)!!.enabled)
        assertEquals(listOf("schedule:alarm-1"), scheduler.events)

        store.setEnabled(original.id, false)
        store.save(
            original.copy(
                time = "21:40",
                destinationName = "집",
                selectedDays = listOf("월", "월", "금"),
            ),
        )

        val replaced = dataSource.getById(original.id)!!
        assertFalse(replaced.enabled)
        assertEquals("21:40", replaced.request.time)
        assertEquals("집", replaced.request.destinationName)
        assertEquals(1, dataSource.getAll().size)
        assertEquals(listOf("schedule:alarm-1", "cancel:alarm-1"), scheduler.events)
    }

    @Test
    fun rejectsInvalidRequestBeforeReplacingStoredData() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val store = IosAlarmStore(dataSource, FakeIosAlarmScheduler())

        assertFailsWith<IllegalArgumentException> {
            store.save(request(time = "25:00"))
        }

        assertTrue(dataSource.getAll().isEmpty())
        assertEquals(0, dataSource.replaceCalls)
    }

    @Test
    fun missingToggleFailsAndMissingDeleteIsIdempotent() = runBlocking {
        val store = IosAlarmStore(FakeAlarmDataSource(), FakeIosAlarmScheduler())

        val error = assertFailsWith<IllegalStateException> {
            store.setEnabled("missing", false)
        }
        assertEquals("저장된 알람을 찾지 못했습니다.", error.message)
        store.delete("missing")
    }

    @Test
    fun deletesDisabledAlarmWithoutCancellingAlreadyRemovedSystemSchedule() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val scheduler = FakeIosAlarmScheduler()
        val store = IosAlarmStore(dataSource, scheduler)

        store.save(request())
        store.setEnabled("alarm-1", false)
        store.delete("alarm-1")

        assertEquals(
            listOf("schedule:alarm-1", "cancel:alarm-1"),
            scheduler.events,
        )
        assertEquals(null, dataSource.getById("alarm-1"))
    }

    @Test
    fun processWideMutexSerializesMutationsAcrossStoreInstances() = runBlocking {
        val events = mutableListOf<String>()
        val firstMutationEntered = CompletableDeferred<Unit>()
        val releaseFirstMutation = CompletableDeferred<Unit>()
        val dataSource = FakeAlarmDataSource(
            beforeReplace = { alarm ->
                events += "start:${alarm.request.time}"
                if (alarm.request.time == "07:05") {
                    firstMutationEntered.complete(Unit)
                    releaseFirstMutation.await()
                }
                events += "end:${alarm.request.time}"
            },
        )
        val firstStore = IosAlarmStore(dataSource, FakeIosAlarmScheduler())
        val secondStore = IosAlarmStore(dataSource, FakeIosAlarmScheduler())

        coroutineScope {
            launch { firstStore.save(request(time = "07:05")) }
            firstMutationEntered.await()
            launch { secondStore.save(request(time = "08:10")) }
            releaseFirstMutation.complete(Unit)
        }

        assertEquals(
            listOf("start:07:05", "end:07:05", "start:08:10", "end:08:10"),
            events,
        )
        assertEquals("08:10", dataSource.getById("alarm-1")!!.request.time)
    }

    @Test
    fun propagatesMutationFailureAndCancellation() = runBlocking {
        val failure = IllegalStateException("write failed")
        val failingStore = IosAlarmStore(
            FakeAlarmDataSource(replaceFailure = failure),
            FakeIosAlarmScheduler(),
        )
        assertEquals(
            failure,
            assertFailsWith<IllegalStateException> { failingStore.save(request()) },
        )

        val cancelledStore = IosAlarmStore(
            FakeAlarmDataSource(replaceFailure = CancellationException("cancelled")),
            FakeIosAlarmScheduler(),
        )
        assertFailsWith<CancellationException> { cancelledStore.save(request()) }
        Unit
    }

    @Test
    fun requestsAuthorizationOnlyForNotDeterminedState() = runBlocking {
        val scheduler = FakeIosAlarmScheduler(
            authorizationState = IosAlarmAuthorizationState.NOT_DETERMINED,
            requestedAuthorizationState = IosAlarmAuthorizationState.AUTHORIZED,
        )
        val store = IosAlarmStore(FakeAlarmDataSource(), scheduler)

        store.save(request())

        assertEquals(1, scheduler.authorizationRequests)
        assertEquals(
            listOf("requestAuthorization", "schedule:alarm-1"),
            scheduler.events,
        )
    }

    @Test
    fun deniedAuthorizationStopsBeforeScheduleAndRoomMutation() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val scheduler = FakeIosAlarmScheduler(
            authorizationState = IosAlarmAuthorizationState.DENIED,
        )
        val store = IosAlarmStore(dataSource, scheduler)

        val error = assertFailsWith<IosAlarmOperationException> {
            store.save(request())
        }

        assertEquals(IosAlarmOperationCode.DENIED, error.code)
        assertTrue(dataSource.getAll().isEmpty())
        assertTrue(scheduler.events.isEmpty())
    }

    @Test
    fun roomFailureAfterScheduleCancelsNewAlarmAndRestoresPreviousSchedule() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val scheduler = FakeIosAlarmScheduler()
        val store = IosAlarmStore(dataSource, scheduler)
        store.save(request())
        dataSource.replaceFailure = IllegalStateException("write failed")

        assertFailsWith<IllegalStateException> {
            store.save(request(time = "08:10"))
        }

        assertEquals(
            listOf(
                "schedule:alarm-1",
                "cancel:alarm-1",
                "schedule:alarm-1",
                "cancel:alarm-1",
                "schedule:alarm-1",
            ),
            scheduler.events,
        )
        assertEquals("07:05", dataSource.getById("alarm-1")!!.request.time)
    }

    @Test
    fun editScheduleAndRestoreFailureReportsRecoveryNeeded() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val scheduler = FakeIosAlarmScheduler()
        val store = IosAlarmStore(dataSource, scheduler)
        store.save(request())
        scheduler.scheduleFailure = IllegalStateException("sdk failed")

        val error = assertFailsWith<IllegalStateException> {
            store.save(request(time = "08:10"))
        }

        assertTrue(error.message.orEmpty().contains("복구에 실패"))
        assertEquals(
            listOf(
                "schedule:alarm-1",
                "cancel:alarm-1",
                "schedule:alarm-1",
                "schedule:alarm-1",
            ),
            scheduler.events,
        )
        assertEquals("07:05", dataSource.getById("alarm-1")!!.request.time)
    }

    @Test
    fun falseToggleResultRunsTheSameSchedulerCompensationAsAnException() = runBlocking {
        val enableDataSource = FakeAlarmDataSource()
        val enableScheduler = FakeIosAlarmScheduler()
        val enableStore = IosAlarmStore(enableDataSource, enableScheduler)
        enableStore.save(request())
        enableStore.setEnabled("alarm-1", false)
        enableDataSource.setEnabledResult = false

        assertFailsWith<IllegalStateException> {
            enableStore.setEnabled("alarm-1", true)
        }
        assertEquals(
            listOf("schedule:alarm-1", "cancel:alarm-1", "schedule:alarm-1", "cancel:alarm-1"),
            enableScheduler.events,
        )

        val disableDataSource = FakeAlarmDataSource()
        val disableScheduler = FakeIosAlarmScheduler()
        val disableStore = IosAlarmStore(disableDataSource, disableScheduler)
        disableStore.save(request())
        disableDataSource.setEnabledResult = false

        assertFailsWith<IllegalStateException> {
            disableStore.setEnabled("alarm-1", false)
        }
        assertEquals(
            listOf("schedule:alarm-1", "cancel:alarm-1", "schedule:alarm-1"),
            disableScheduler.events,
        )
    }

    @Test
    fun mutationRunnerCallsSuccessOnlyAfterCommitAndReportsFailureOnce() = runBlocking {
        var successes = 0
        val errors = mutableListOf<String>()

        runIosAlarmMutation(
            fallbackErrorMessage = "fallback",
            onError = errors::add,
            mutation = {},
            onSuccess = { successes += 1 },
        )
        runIosAlarmMutation(
            fallbackErrorMessage = "fallback",
            onError = errors::add,
            mutation = { error("write failed") },
            onSuccess = { successes += 1 },
        )

        assertEquals(1, successes)
        assertEquals(listOf("write failed"), errors)
    }

    @Test
    fun mutationRunnerRethrowsCancellationWithoutCallbacks() = runBlocking {
        var successes = 0
        val errors = mutableListOf<String>()

        assertFailsWith<CancellationException> {
            runIosAlarmMutation(
                fallbackErrorMessage = "fallback",
                onError = errors::add,
                mutation = { throw CancellationException("cancelled") },
                onSuccess = { successes += 1 },
            )
        }

        assertEquals(0, successes)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun observationReportsOncePerFailureSeriesAndRecoversAfterDelay() = runBlocking {
        var subscriptions = 0
        val reported = mutableListOf<String>()
        val source = flow {
            subscriptions += 1
            when (subscriptions) {
                1 -> error("first series")
                2 -> {
                    emit(listOf(SavedAlarmSchedule(request(), true)))
                    error("second series")
                }
                3 -> error("same second series")
                else -> emit(emptyList())
            }
        }

        val emissions = source.recoveringAlarmObservation(
            onError = { error -> reported += error.message.orEmpty() },
            retryDelayMillis = 0,
        ).take(2).toList()

        assertEquals(listOf(1, 0), emissions.map(List<SavedAlarmSchedule>::size))
        assertEquals(listOf("first series", "second series"), reported)
        assertEquals(4, subscriptions)
    }

    @Test
    fun observationDoesNotRetryCancellation() = runBlocking {
        var subscriptions = 0
        val source: Flow<List<SavedAlarmSchedule>> = flow {
            subscriptions += 1
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            source.recoveringAlarmObservation(
                onError = { error("must not report") },
                retryDelayMillis = 0,
            ).first()
        }
        assertEquals(1, subscriptions)
    }

    private fun request(
        time: String = "07:05",
    ) = AlarmScheduleRequest(
        id = "alarm-1",
        time = time,
        selectedDays = listOf("월", "금"),
        repeatEnabled = true,
        limitMinutes = 12,
        destinationName = "회사",
        destinationAddress = "서울특별시 중구 세종대로 110",
        destinationLatitude = 37.5665,
        destinationLongitude = 126.978,
        targetDistanceKm = 1.2,
        alarmSoundName = "기본 알람음",
        alarmSoundUri = null,
    )
}

private class FakeAlarmDataSource(
    replaceFailure: Throwable? = null,
    private val beforeReplace: suspend (SavedAlarmSchedule) -> Unit = {},
) : AlarmDataSource {
    private val alarms = linkedMapOf<String, SavedAlarmSchedule>()
    private val observed = MutableStateFlow<List<SavedAlarmSchedule>>(emptyList())
    var replaceCalls: Int = 0
        private set
    var replaceFailure: Throwable? = replaceFailure
    var setEnabledResult: Boolean = true

    override fun observeAll(): Flow<List<SavedAlarmSchedule>> = observed

    override suspend fun getAll(): List<SavedAlarmSchedule> = alarms.values.toList()

    override suspend fun getEnabled(): List<SavedAlarmSchedule> =
        alarms.values.filter(SavedAlarmSchedule::enabled)

    override suspend fun getById(id: String): SavedAlarmSchedule? = alarms[id]

    override suspend fun replace(alarm: SavedAlarmSchedule) {
        replaceCalls += 1
        beforeReplace(alarm)
        replaceFailure?.let { throw it }
        alarms[alarm.request.id] = alarm
        observed.value = alarms.values.toList()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val alarm = alarms[id] ?: return false
        if (!setEnabledResult) return false
        alarms[id] = alarm.copy(enabled = enabled)
        observed.value = alarms.values.toList()
        return true
    }

    override suspend fun delete(id: String): Boolean {
        val removed = alarms.remove(id) != null
        if (removed) observed.value = alarms.values.toList()
        return removed
    }

    override suspend fun hasStorageMigration(id: String): Boolean = false

    override suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean = false
}

private class FakeIosAlarmScheduler(
    private var authorizationState: IosAlarmAuthorizationState = IosAlarmAuthorizationState.AUTHORIZED,
    private val requestedAuthorizationState: IosAlarmAuthorizationState = authorizationState,
) : IosAlarmScheduler {
    val events = mutableListOf<String>()
    var scheduleFailure: Throwable? = null
    var authorizationRequests = 0
        private set

    override fun authorizationState(): IosAlarmAuthorizationState = authorizationState

    override fun requestAuthorization(callback: (IosAlarmAuthorizationResult) -> Unit) {
        authorizationRequests += 1
        events += "requestAuthorization"
        authorizationState = requestedAuthorizationState
        callback(IosAlarmAuthorizationResult(state = requestedAuthorizationState))
    }

    override fun schedule(
        request: IosAlarmScheduleDto,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        events += "schedule:${request.alarmId}"
        scheduleFailure?.let { throw it }
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    override fun cancel(
        alarmId: String,
        callback: (IosAlarmOperationResult) -> Unit,
    ) {
        events += "cancel:$alarmId"
        callback(IosAlarmOperationResult(IosAlarmOperationCode.SUCCESS))
    }

    override fun scheduledAlarms(callback: (IosScheduledAlarmsResult) -> Unit) {
        callback(IosScheduledAlarmsResult())
    }
}
