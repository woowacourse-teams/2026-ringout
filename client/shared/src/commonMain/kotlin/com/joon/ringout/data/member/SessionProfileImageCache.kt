package com.joon.ringout.data.member

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.member.MemberProfileImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 화면이 사라져도 진행 중인 조회를 공유하며, 다른 로그인 세션에는 결과를 전달하지 않는다. */
internal class SessionProfileImageCache(
    private val session: AuthSession,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(CacheState(session.identity.value))

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.identity.collect {
                mutex.withLock { currentState() }
            }
        }
    }

    fun peek(): MemberProfileImage? {
        val cached = state.value
        return cached.image.takeIf {
            cached.sessionIdentity != null && cached.sessionIdentity === session.identity.value && !cached.withdrawn
        }
    }

    suspend fun get(load: suspend () -> MemberProfileImage): MemberProfileImage {
        val identity = session.identity.value
        val request = mutex.withLock {
            ensureCurrentSession(identity)
            val current = currentState()
            check(!current.withdrawn) {
                "로그인이 필요한 기능이에요."
            }
            current.image?.let { return it }
            current.request ?: run {
                val requestId = Any()
                val request = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val image = load()
                        Result.success(mutex.withLock {
                            ensureCurrentSession(current.sessionIdentity)
                            val latest = state.value
                            if (latest.withdrawn) throw CancellationException("탈퇴한 계정의 응답입니다.")
                            state.value = latest.copy(image = image)
                            image
                        })
                    } catch (error: Throwable) {
                        Result.failure<MemberProfileImage>(error)
                    } finally {
                        withContext(NonCancellable) {
                            mutex.withLock {
                                if (state.value.requestId === requestId) {
                                    state.value = state.value.copy(request = null, requestId = null)
                                }
                            }
                        }
                    }
                }
                state.value = current.copy(request = request, requestId = requestId)
                request
            }
        }
        request.await().getOrThrow()
        // await 중 계정이 바뀌었다면 이미 완료된 이전 응답도 전달하지 않는다.
        return mutex.withLock {
            ensureCurrentSession(identity)
            val current = currentState()
            if (current.image == null || current.withdrawn) {
                throw CancellationException("계정 세션이 변경되었습니다.")
            }
            checkNotNull(current.image)
        }
    }

    suspend fun onWithdrawn(identity: Any?) = mutex.withLock {
        ensureCurrentSession(identity)
        state.value.request?.cancel()
        state.value = CacheState(identity, withdrawn = true)
    }

    private fun currentState(): CacheState {
        if (state.value.sessionIdentity !== session.identity.value) {
            state.value.request?.cancel()
            state.value = CacheState(session.identity.value)
        }
        return state.value
    }

    private fun ensureCurrentSession(identity: Any?) {
        if (identity == null || identity !== session.identity.value) {
            throw CancellationException("계정 세션이 변경되었습니다.")
        }
    }

    private data class CacheState(
        val sessionIdentity: Any?,
        val image: MemberProfileImage? = null,
        val request: Deferred<Result<MemberProfileImage>>? = null,
        val requestId: Any? = null,
        val withdrawn: Boolean = false,
    )
}
