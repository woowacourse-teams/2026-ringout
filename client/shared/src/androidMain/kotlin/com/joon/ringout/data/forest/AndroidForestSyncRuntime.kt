package com.joon.ringout.data.forest

import android.content.Context
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.connectivity.AndroidNetworkMonitor
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.data.preferences.getAppPreferencesDataStore
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The UI, alarm service and Worker share one forest snapshot and delivery loop. */
internal class AndroidForestSyncRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authSession = getAuthSession()
    val repository = DefaultForestRepository(
        httpClient = getRingoutHttpClient(),
        tokenStorage = createSecureTokenStorage(context),
        authSession = authSession,
        localProgressStore = DataStoreForestLocalProgressStore(getAppPreferencesDataStore(context)),
        observeSyncedArrivals = getRingoutDatabase(context).alarmOccurrenceSyncDao()::observeSuccessfulArrivalSyncs,
    )
    private val coordinator = ForestSyncCoordinator(repository, authSession, AndroidNetworkMonitor(context))

    init { coordinator.start(scope) }

    fun requestSync() = coordinator.requestSync()

    companion object {
        @Volatile private var instance: AndroidForestSyncRuntime? = null
        fun get(context: Context): AndroidForestSyncRuntime = instance ?: synchronized(this) {
            instance ?: AndroidForestSyncRuntime(context.applicationContext).also { instance = it }
        }
    }
}
