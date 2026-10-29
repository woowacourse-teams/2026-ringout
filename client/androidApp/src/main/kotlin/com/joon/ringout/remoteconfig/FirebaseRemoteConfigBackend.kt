package com.joon.ringout.remoteconfig

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class FirebaseRemoteConfigBackend(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : RemoteConfigBackend {
    override suspend fun setDefaults() {
        // Wait for Firebase's persisted active values before reading them.
        remoteConfig.ensureInitialized().awaitCompletion()
        remoteConfig.setDefaultsAsync(mapOf(MIN_VERSION_CODE_KEY to DEFAULT_MIN_VERSION_CODE)).awaitCompletion()
    }

    override suspend fun fetch(force: Boolean) {
        val task = if (force) remoteConfig.fetch(0) else remoteConfig.fetch()
        task.awaitCompletion()
    }

    override suspend fun activate() {
        remoteConfig.activate().awaitCompletion()
    }

    override fun snapshot(): RemoteConfigSnapshot {
        val value = remoteConfig.getValue(MIN_VERSION_CODE_KEY)
        val info = remoteConfig.info
        return RemoteConfigSnapshot(
            rawValue = value.asString(),
            isRemote = value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            lastFetchTimeMillis = info.fetchTimeMillis.takeIf { it > 0 },
            fetchStatus = when (info.lastFetchStatus) {
                FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS -> RemoteConfigFetchStatus.SUCCESS
                FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE -> RemoteConfigFetchStatus.FAILURE
                FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED -> RemoteConfigFetchStatus.THROTTLED
                else -> RemoteConfigFetchStatus.NOT_FETCHED
            },
        )
    }

    override fun listen(onUpdate: () -> Unit, onError: () -> Unit) {
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) = onUpdate()

            override fun onError(error: FirebaseRemoteConfigException) = onError()
        })
    }
}

private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
        }
    }
}
