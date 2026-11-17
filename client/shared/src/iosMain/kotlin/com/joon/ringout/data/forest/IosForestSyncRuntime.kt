package com.joon.ringout.data.forest

import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.connectivity.IosNetworkMonitor
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Available to arrival recovery even when the forest screen has never been opened. */
internal object IosForestSyncRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val authSession = getAuthSession()
    val repository = DefaultForestRepository(
        httpClient = getRingoutHttpClient(),
        tokenStorage = createSecureTokenStorage(),
        authSession = authSession,
        localProgressStore = DataStoreForestLocalProgressStore(getAppPreferencesDataStore()),
        observeSyncedArrivals = getRingoutDatabase().alarmOccurrenceSyncDao()::observeSuccessfulArrivalSyncs,
    )
    private val coordinator = ForestSyncCoordinator(repository, authSession, IosNetworkMonitor())

    init { coordinator.start(scope) }

    fun requestSync() = coordinator.requestSync()
}
