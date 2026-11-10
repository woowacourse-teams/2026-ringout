package com.joon.ringout.presentation.forest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.forest.ForestRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.Clock

internal class ForestViewModel(
    private val repository: ForestRepository,
    private val scope: CoroutineScope? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    fun connect(send: (String) -> Unit, onReady: () -> Unit, onBack: () -> Unit, onError: () -> Unit): ForestConnection =
        ForestConnection(repository, scope ?: viewModelScope, now, send, onReady, onBack, onError)
}

/** One connection per WebView. A reload invalidates every previous page's asynchronous reply. */
internal class ForestConnection(
    private val repository: ForestRepository,
    private val parentScope: CoroutineScope,
    private val now: () -> Long,
    private val send: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onBack: () -> Unit,
    private val onError: () -> Unit,
) {
    private var pageJob = SupervisorJob(parentScope.coroutineContext[Job])
    private var scope = CoroutineScope(parentScope.coroutineContext + pageJob)
    private var mutex = Mutex()
    private var ready = false
    private var revision = 0L
    private var observer: Job? = null
    private var active = true
    private val replies = linkedMapOf<String, Pair<String, String>>()

    fun reset() {
        pageJob.cancel()
        pageJob = SupervisorJob(parentScope.coroutineContext[Job])
        scope = CoroutineScope(parentScope.coroutineContext + pageJob)
        mutex = Mutex()
        observer = null
        replies.clear()
        revision = 0
        ready = false
    }
    fun close() { pageJob.cancel(); ready = false }
    fun setActive(value: Boolean) {
        active = value
        if (!value) { observer?.cancel(); observer = null }
        else if (ready) observe()
    }
    fun back(): Boolean {
        if (!ready) return false
        send(ForestProtocol.message("forest.navigateBack", "native-back"))
        return true
    }
    fun receive(raw: String) {
        if (raw.length > 4096 || !pageJob.isActive) return
        val request = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val id = (request["requestId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return
        val type = (request["type"] as? JsonPrimitive)?.contentOrNull
        val requestVersion = (request["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        val cycle = (request["growthCycle"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        fun error(code: String) = ForestProtocol.message("forest.result", id) {
            put("success", false); put("error", code)
        }
        if (requestVersion != ForestProtocol.Version) { send(error("unsupportedVersion")); return }
        if (type !in setOf("forest.ready", "forest.completeOnboarding", "forest.acknowledgeTree", "forest.back") ||
            (type == "forest.acknowledgeTree" && (cycle == null || cycle !in 1..16))) {
            send(error("invalidRequest")); return
        }
        val fingerprint = "$type:$cycle"
        val pageMutex = mutex
        scope.launch {
            pageMutex.withLock {
                replies[id]?.let { (previous, reply) ->
                    send(if (previous == fingerprint) reply else error("invalidRequest"))
                    return@withLock
                }
                if (type != "forest.ready" && !ready) { send(error("invalidRequest")); return@withLock }
                try {
                    when (type) {
                        "forest.completeOnboarding" -> repository.completeOnboarding(now())
                        "forest.acknowledgeTree" -> {
                            val state = repository.getForest()
                            require(state.isOnboardingCompleted && state.trees.any { it.growthCycle == cycle })
                            repository.acknowledgeTree(checkNotNull(cycle), now())
                        }
                        "forest.back" -> { onBack(); return@withLock }
                    }
                    val state = repository.getForest()
                    ensureActive()
                    val response = ForestProtocol.message(if (type == "forest.ready") "forest.snapshot" else "forest.result", id) {
                        put("success", true)
                        put("revision", ++revision)
                        put("payload", ForestProtocol.snapshot(state))
                    }
                    replies[id] = fingerprint to response
                    if (replies.size > 64) replies.remove(replies.keys.first())
                    send(response)
                    if (type == "forest.ready") {
                        ready = true
                        onReady()
                        observe()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: IllegalArgumentException) { send(error("invalidRequest")) }
                catch (_: Exception) {
                    ensureActive()
                    send(error("storage"))
                    if (type == "forest.ready") onError()
                }
            }
        }
    }
    private fun observe() {
        if (!active || observer?.isActive == true) return
        val pageMutex = mutex
        observer = scope.launch {
            try {
                repository.observeForest().collect {
                    pageMutex.withLock {
                        // Re-read under the command lock so a queued emission cannot roll back a saved result.
                        val state = repository.getForest()
                        ensureActive()
                        send(ForestProtocol.message("forest.snapshot", "update") {
                            put("revision", ++revision)
                            put("payload", ForestProtocol.snapshot(state))
                        })
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ensureActive(); onError() }
        }
    }
}
