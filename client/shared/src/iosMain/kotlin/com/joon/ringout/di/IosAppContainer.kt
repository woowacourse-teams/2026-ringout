package com.joon.ringout.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.joon.ringout.data.alarmactivity.RoomAlarmActivityRepository
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
import com.joon.ringout.data.preferences.IosSystemThemeModeReader
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession
import com.joon.ringout.platform.IosNativeServices

class IosAppContainer(
    nativeServices: IosNativeServices,
) : AppContainer {
    private val httpClient = getRingoutHttpClient()
    private val tokenStorage = createSecureTokenStorage()
    private val database = getRingoutDatabase()

    override val authSession = getAuthSession()
    override val termsRepository = com.joon.ringout.data.terms.DefaultTermsRepository(httpClient, tokenStorage, authSession)
    override val networkMonitor = com.joon.ringout.data.connectivity.IosNetworkMonitor()

    override val appPreferencesRepository =
        DataStoreAppPreferencesRepository(
            getAppPreferencesDataStore(),
        )

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
    override val roomRepository = DefaultRoomRepository(
        httpClient = httpClient,
        tokenStorage = tokenStorage,
        authSession = authSession,
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
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorMissionHistoryRemoteDataSource를 다시 주입한다.

    override val productAnalyticsRecorder =
        createProductAnalyticsRecorder(
            nativeServices.analyticsTracker(),
        )
}
