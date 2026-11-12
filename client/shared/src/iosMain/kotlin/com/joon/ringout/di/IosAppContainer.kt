package com.joon.ringout.di

import com.joon.ringout.alarm.newAlarmId
import com.joon.ringout.data.alarm.AlarmOutboxSyncer
import com.joon.ringout.data.alarm.AlarmSyncCoordinator
import com.joon.ringout.data.alarm.AlarmSyncStore
import com.joon.ringout.data.alarm.KtorAlarmRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.joon.ringout.data.alarmactivity.RoomAlarmActivityRepository
import com.joon.ringout.analytics.createRoomMembershipAnalytics
import com.joon.ringout.data.room.AnalyticsRoomRepository
import com.joon.ringout.analytics.createProductAnalyticsRecorder
import com.joon.ringout.data.auth.DefaultAuthRepository
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceSyncRuntime
import com.joon.ringout.data.alarmoccurrence.DefaultAlarmOccurrenceMonthLoader
import com.joon.ringout.data.alarmoccurrence.captureAlarmOccurrenceAccount
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.database.RoomLocalUserDataCleanup
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.data.destination.DestinationOutboxSyncer
import com.joon.ringout.data.destination.DestinationSyncCoordinator
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.data.destination.KtorDestinationRemoteDataSource
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.outbox.RoomOutboxReader
import com.joon.ringout.data.member.DefaultMemberRepository
import com.joon.ringout.data.room.DefaultRoomRepository
import com.joon.ringout.data.missionhistory.RoomRecordsHistoryRepository
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.push.KtorPushTokenRegistrar
import com.joon.ringout.data.preferences.DataStoreAppPreferencesRepository
import com.joon.ringout.data.preferences.IosSystemThemeModeReader
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.data.preferences.getInstallationPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession
import com.joon.ringout.domain.push.PushPlatform
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness
import com.joon.ringout.domain.outbox.OutboxReader
import com.joon.ringout.domain.push.PushTokenSource
import com.joon.ringout.platform.IosFcmTokenListener
import com.joon.ringout.platform.IosNativeServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosAppContainer(
    nativeServices: IosNativeServices,
    applyAlarmServerChange: suspend (String, suspend () -> Boolean) -> Boolean,
    private val reconcileAlarmReservations: suspend () -> Unit,
) : AppContainer {
    private val httpClient = getRingoutHttpClient()
    private val tokenStorage = createSecureTokenStorage()
    private val database = getRingoutDatabase()
    override val outboxReader: OutboxReader = RoomOutboxReader(
        destinationDao = database.destinationDao(),
        alarmOccurrenceSyncDao = database.alarmOccurrenceSyncDao(),
    )

    override val authSession = getAuthSession()
    override val currentVerifiedAlarmAccountId: suspend () -> String? = {
        captureAlarmOccurrenceAccount(tokenStorage, authSession)?.ownerAccountId
    }
    override val migrateLegacyAlarmData: suspend (String) -> Unit = { accountId ->
        IosAlarmOccurrenceSyncRuntime.migrateLegacyData(accountId)
    }
    override val pushTokenSource: PushTokenSource = IosFcmTokenSource(nativeServices)
    override val pushTokenRegistrationReadiness =
        PushTokenRegistrationReadiness(PushPlatform.Ios)
    override val pushTokenRegistrar = KtorPushTokenRegistrar(httpClient, tokenStorage, authSession)
    override val termsRepository = com.joon.ringout.data.terms.DefaultTermsRepository(httpClient, tokenStorage, authSession)
    override val networkMonitor = com.joon.ringout.data.connectivity.IosNetworkMonitor()
    private val destinationSessionGate = DestinationSyncSessionGate()
    private val destinationAccountProvider = DestinationAccountProvider(
        authSession = authSession,
        currentVerifiedAccountId = currentVerifiedAlarmAccountId,
    )

    override val appPreferencesRepository =
        DataStoreAppPreferencesRepository(
            dataStore = getAppPreferencesDataStore(),
            installationDataStore = getInstallationPreferencesDataStore(),
        )

    override val hasStoredAlarmsForEntryMigration: suspend () -> Boolean = {
        database.alarmDao().getAll().isNotEmpty()
    }

    override val systemThemeModeReader = IosSystemThemeModeReader()

    override val authRepository =
        DefaultAuthRepository(
            authApi = KtorAuthApi(httpClient),
            tokenStorage = tokenStorage,
            authSession = authSession,
        )

    override val memberRepository =
        DefaultMemberRepository(
            httpClient = httpClient,
            tokenStorage = tokenStorage,
            authSession = authSession,
        )
    override val roomRepository: com.joon.ringout.domain.room.RoomRepository = AnalyticsRoomRepository(
        delegate = DefaultRoomRepository(httpClient, tokenStorage, authSession),
        analytics = createRoomMembershipAnalytics(),
        tokens = tokenStorage,
        session = authSession,
    )

    override val destinationRepository =
        DefaultDestinationRepository(
            dataSource = RoomDestinationDataSource(
                database.destinationDao(),
            ),
            accountProvider = destinationAccountProvider,
            sessionGate = destinationSessionGate,
        )

    private val destinationSyncer = DestinationOutboxSyncer(
        dao = database.destinationDao(),
        remoteDataSource = KtorDestinationRemoteDataSource(httpClient, tokenStorage),
        accountProvider = destinationAccountProvider,
        sessionGate = destinationSessionGate,
        authSession = authSession,
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val destinationSyncCoordinator = DestinationSyncCoordinator(
        syncer = destinationSyncer,
        dao = database.destinationDao(),
        accountProvider = destinationAccountProvider,
        authSession = authSession,
        networkMonitor = networkMonitor,
    )

    private val alarmSyncCoordinator = AlarmSyncCoordinator(
        syncer = AlarmOutboxSyncer(
            store = AlarmSyncStore(database, ::newAlarmId),
            remoteDataSource = KtorAlarmRemoteDataSource(httpClient, tokenStorage),
            accountProvider = destinationAccountProvider,
            sessionGate = destinationSessionGate,
            applyServerChange = applyAlarmServerChange,
        ),
        accountProvider = destinationAccountProvider,
        authSession = authSession,
        networkMonitor = networkMonitor,
    )

    init {
        destinationSyncCoordinator.start(applicationScope)
        alarmSyncCoordinator.start(applicationScope)
    }

    override suspend fun prepareAlarmSync(forceRestore: Boolean) {
        val sessionIdentity = authSession.identity.value
        try {
            reconcileAlarmReservations()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("알람 예약을 복구하지 못했습니다: ${error.message}")
        }
        if (authSession.identity.value === sessionIdentity) {
            alarmSyncCoordinator.prepareCurrentSession(forceRestore)
        }
    }

    override fun onAppResumed() {
        destinationSyncCoordinator.requestSync()
        alarmSyncCoordinator.requestSync()
    }

    override val alarmActivityRepository =
        RoomAlarmActivityRepository(
            database.alarmActivityDao(),
            observedRingingOnly = true,
        )

    init {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { alarmActivityRepository.initializeTracking() }
        }
    }

    override val missionHistoryRepository =
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(database.missionHistoryDao()),
        )
    override val recordsHistoryRepository = RoomRecordsHistoryRepository(
        database,
        tokenStorage,
        authSession,
        alarmActivityRepository,
    )
    override val alarmOccurrenceMonthLoader = DefaultAlarmOccurrenceMonthLoader(
        httpClient,
        tokenStorage,
        authSession,
        database.alarmOccurrenceCacheDao(),
        destinationSessionGate,
        applicationScope,
    )
    override val localUserDataCleanup = RoomLocalUserDataCleanup(
        dao = database.localUserDataCleanupDao(),
        destinationSessionGate = destinationSessionGate,
        currentSessionIdentity = { authSession.identity.value },
    )
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorMissionHistoryRemoteDataSource를 다시 주입한다.

    override val arrivalResultRepository =
        com.joon.ringout.data.arrival.RoomArrivalResultRepository(database.arrivalResultDao())

    override val forestRepository =
        com.joon.ringout.data.forest.RoomForestRepository(database.forestDao())

    override val productAnalyticsRecorder =
        createProductAnalyticsRecorder(
            nativeServices.analyticsTracker(),
        )
}

private class IosFcmTokenSource(
    nativeServices: IosNativeServices,
) : PushTokenSource {
    private val mutableLatestFcmToken = MutableStateFlow(nativeServices.latestFcmToken())

    override val latestFcmToken: StateFlow<String?> = mutableLatestFcmToken.asStateFlow()

    init {
        nativeServices.observeFcmToken(
            object : IosFcmTokenListener {
                override fun onTokenChanged(token: String?) {
                    mutableLatestFcmToken.value = token
                }
            },
        )
    }
}
