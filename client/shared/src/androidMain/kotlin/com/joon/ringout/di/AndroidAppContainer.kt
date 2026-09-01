package com.joon.ringout.di

import android.content.Context
import com.joon.ringout.analytics.createProductAnalyticsRecorder
import com.joon.ringout.data.auth.DefaultAuthRepository
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.member.DefaultMemberRepository
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.preferences.DataStoreAppPreferencesRepository
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession

class AndroidAppContainer(
    context: Context,
) : AppContainer {
    private val httpClient = getRingoutHttpClient()
    private val tokenStorage = createSecureTokenStorage(context)
    private val database = getRingoutDatabase(context)

    override val authSession = getAuthSession()

    override val appPreferencesRepository =
        DataStoreAppPreferencesRepository(
            getAppPreferencesDataStore(context)
        )

    override val authRepository = DefaultAuthRepository(
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
        )
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorDestinationRemoteDataSource를 다시 주입한다.

    override val missionHistoryRepository =
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(database.missionHistoryDao()),
        )
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 KtorMissionHistoryRemoteDataSource를 다시 주입한다.

    override val productAnalyticsRecorder =
        createProductAnalyticsRecorder(context)
}
