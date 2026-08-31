package com.joon.ringout.di

import com.joon.ringout.analytics.createProductAnalyticsRecorder
import com.joon.ringout.data.auth.DefaultAuthRepository
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.KtorDestinationRemoteDataSource
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.member.DefaultMemberRepository
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.KtorMissionHistoryRemoteDataSource
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.preferences.DataStoreAppPreferencesRepository
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

    override val appPreferencesRepository =
        DataStoreAppPreferencesRepository(
            getAppPreferencesDataStore(),
        )

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
        )

    override val destinationRepository =
        DefaultDestinationRepository(
            dataSource = RoomDestinationDataSource(
                database.destinationDao(),
            ),
            remoteDataSource = KtorDestinationRemoteDataSource(
                httpClient = httpClient,
                tokenStorage = tokenStorage,
            ),
        )

    override val missionHistoryRepository =
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(database.missionHistoryDao()),
            remoteDataSource =
                KtorMissionHistoryRemoteDataSource(
                    httpClient = httpClient,
                    tokenStorage = tokenStorage,
                ),
        )

    override val productAnalyticsRecorder =
        createProductAnalyticsRecorder(
            nativeServices.analyticsTracker(),
        )
}
