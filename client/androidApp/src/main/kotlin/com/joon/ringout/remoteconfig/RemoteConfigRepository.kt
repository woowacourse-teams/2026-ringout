package com.joon.ringout.remoteconfig

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MIN_VERSION_CODE_KEY = "android_min_version_code"
internal const val DEFAULT_MIN_VERSION_CODE = 1

internal enum class RemoteConfigValueSource { DEFAULT, REMOTE, INVALID_REMOTE }

internal enum class RemoteConfigFetchStatus { NOT_FETCHED, FETCHING, SUCCESS, FAILURE, THROTTLED }

internal data class RemoteConfigState(
    val minVersionCode: Int = DEFAULT_MIN_VERSION_CODE,
    val valueSource: RemoteConfigValueSource = RemoteConfigValueSource.DEFAULT,
    val lastFetchTimeMillis: Long? = null,
    val fetchStatus: RemoteConfigFetchStatus = RemoteConfigFetchStatus.NOT_FETCHED,
    val listenerError: Boolean = false,
)

internal data class RemoteConfigSnapshot(
    val rawValue: String,
    val isRemote: Boolean,
    val lastFetchTimeMillis: Long?,
    val fetchStatus: RemoteConfigFetchStatus,
)

internal interface RemoteConfigBackend {
    suspend fun setDefaults()
    suspend fun fetch(force: Boolean)
    suspend fun activate()
    fun snapshot(): RemoteConfigSnapshot
    fun listen(onUpdate: () -> Unit, onError: () -> Unit)
}

/** Owns one listener for the lifetime of the application process. */
internal class RemoteConfigRepository(
    private val backend: RemoteConfigBackend,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RemoteConfigState())
    val state: StateFlow<RemoteConfigState> = mutableState.asStateFlow()

    private val initialization: Job = scope.launch {
        mutex.withLock {
            try {
                backend.setDefaults()
                publishSnapshot()
                try {
                    backend.listen(
                        onUpdate = { scope.launch { activateUpdate() } },
                        onError = {
                            Log.w(TAG, "Remote Config real-time listener failed")
                            scope.launch {
                                mutex.withLock {
                                    mutableState.value = mutableState.value.copy(listenerError = true)
                                }
                            }
                        },
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(TAG, "Remote Config listener registration failed", exception)
                    mutableState.value = mutableState.value.copy(listenerError = true)
                }
                fetchAndActivate(force = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Remote Config initialization failed", exception)
                mutableState.value = mutableState.value.copy(fetchStatus = RemoteConfigFetchStatus.FAILURE)
            }
        }
    }

    /** A manual refresh bypasses the local minimum fetch interval; Firebase may still throttle it. */
    fun refresh(): Job = scope.launch {
        initialization.join()
        mutex.withLock { fetchAndActivate(force = true) }
    }

    private suspend fun activateUpdate() {
        mutex.withLock {
            mutableState.value = mutableState.value.copy(listenerError = false)
            try {
                backend.activate()
                publishSnapshot()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Remote Config activation failed", exception)
                publishSnapshot()
            }
        }
    }

    private suspend fun fetchAndActivate(force: Boolean) {
        mutableState.value = mutableState.value.copy(fetchStatus = RemoteConfigFetchStatus.FETCHING)
        try {
            backend.fetch(force)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Remote Config fetch failed", exception)
            val snapshot = backend.snapshot()
            publishSnapshot(
                snapshot,
                if (snapshot.fetchStatus == RemoteConfigFetchStatus.THROTTLED) {
                    RemoteConfigFetchStatus.THROTTLED
                } else {
                    RemoteConfigFetchStatus.FAILURE
                },
            )
            return
        }
        try {
            backend.activate()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Fetch status describes the fetch, even if activation failed.
            Log.w(TAG, "Remote Config activation failed", exception)
        }
        publishSnapshot()
    }

    private fun publishSnapshot(
        snapshot: RemoteConfigSnapshot = backend.snapshot(),
        fetchStatus: RemoteConfigFetchStatus = snapshot.fetchStatus,
    ) {
        val validValue = snapshot.rawValue.toIntOrNull()?.takeIf { it > 0 }
        mutableState.value = RemoteConfigState(
            minVersionCode = validValue ?: DEFAULT_MIN_VERSION_CODE,
            valueSource = when {
                snapshot.isRemote && validValue == null -> RemoteConfigValueSource.INVALID_REMOTE
                snapshot.isRemote -> RemoteConfigValueSource.REMOTE
                else -> RemoteConfigValueSource.DEFAULT
            },
            lastFetchTimeMillis = snapshot.lastFetchTimeMillis,
            fetchStatus = fetchStatus,
            listenerError = mutableState.value.listenerError,
        )
    }

    companion object {
        private const val TAG = "RemoteConfig"

        fun create(): RemoteConfigRepository = RemoteConfigRepository(
            backend = FirebaseRemoteConfigBackend(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
}
