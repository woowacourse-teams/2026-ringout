package com.joon.ringout.di

import android.content.Context
import com.joon.ringout.data.alarmactivity.AndroidAlarmActivityRecorder
import com.joon.ringout.data.alarmoccurrence.AndroidAlarmOccurrenceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.joon.ringout.data.alarmactivity.RoomAlarmActivityRepository
import com.joon.ringout.analytics.createRoomMembershipAnalytics
import com.joon.ringout.data.room.AnalyticsRoomRepository
import com.joon.ringout.analytics.createProductAnalyticsRecorder
import com.joon.ringout.data.auth.DefaultAuthRepository
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.member.DefaultMemberRepository
import com.joon.ringout.data.room.DefaultRoomRepository
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.preferences.DataStoreAppPreferencesRepository
import com.joon.ringout.data.preferences.AndroidSystemThemeModeReader
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession

class AndroidAppContainer(
    context: Context,
) : AppContainer {
    private val httpClient = getRingoutHttpClient()
    private val tokenStorage = createSecureTokenStorage(context)
    private val database = getRingoutDatabase(context)

    init {
        AndroidAlarmActivityRecorder.get(context).flush()
        AndroidAlarmOccurrenceRuntime.get(context)
    }

    override val authSession = getAuthSession()
    override val termsRepository = com.joon.ringout.data.terms.DefaultTermsRepository(httpClient, tokenStorage, authSession)
    override val networkMonitor = com.joon.ringout.data.connectivity.AndroidNetworkMonitor(context)

    override val appPreferencesRepository =
        DataStoreAppPreferencesRepository(
            getAppPreferencesDataStore(context)
        )

    override val systemThemeModeReader = AndroidSystemThemeModeReader(context)

    override val authRepository = DefaultAuthRepository(
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
        analytics = createRoomMembershipAnalytics(context),
        tokens = tokenStorage,
        session = authSession,
    )
    override val destinationRepository =
        DefaultDestinationRepository(
            dataSource = RoomDestinationDataSource(
                database.destinationDao(),
            ),
        )
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorDestinationRemoteDataSource를 다시 주입한다.

    override val alarmActivityRepository =
        RoomAlarmActivityRepository(
            database.alarmActivityDao(),
            observedRingingOnly = false,
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
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorMissionHistoryRemoteDataSource를 다시 주입한다.

    override val productAnalyticsRecorder =
        createProductAnalyticsRecorder(context)
}
