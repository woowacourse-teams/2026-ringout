package com.joon.ringout.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 네이티브 SavedState 런타임에서 실제 ViewModelStoreProvider의 수명 주기를 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelScopesTest {
    @Test
    fun `경로 조회와 동일 키의 entry owner는 같은 ViewModel을 제공한다`() = runTest {
        val fixture = ScopeFixture(StandardTestDispatcher(testScheduler))
        try {
            val route = AppRoute.Login
            fixture.acquire(route)
            val first = fixture.scopes.get(route, ScopeProbeViewModel::class)
            val second = fixture.scopes.get(route, SecondaryScopeProbeViewModel::class)
            val entryOwner = fixture.provider.getOrCreateOwner(
                key = route.viewModelStoreKey(),
                savedStateRegistryOwner = null,
            )
            val entryProvider = ViewModelProvider.create(entryOwner, fixture.factory)

            assertSame(first, fixture.scopes.get(route, ScopeProbeViewModel::class))
            assertSame(first, entryProvider[ScopeProbeViewModel::class])
            assertSame(second, entryProvider[SecondaryScopeProbeViewModel::class])
            assertNotSame(first, second)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `자식 경로를 제거해도 공유하는 부모 scope는 유지된다`() = runTest {
        val flows = listOf(
            AppRoute.MyPage to AppRoute.NicknameChange,
            AppRoute.Login to AppRoute.TermsAgreement,
            AppRoute.AddAlarm to AppRoute.AlarmSound,
            AppRoute.EditAlarm("alarm-1") to AppRoute.Destination(1L),
        )
        for ((parent, child) in flows) {
            val fixture = ScopeFixture(StandardTestDispatcher(testScheduler))
            try {
                fixture.acquire(parent)
                val childToken = fixture.acquire(child)
                val parentModel = fixture.scopes.get(parent, ScopeProbeViewModel::class)
                val childModel = fixture.scopes.get(child, ScopeProbeViewModel::class)

                fixture.provider.clearKey(child.viewModelStoreKey())
                childToken.close()

                assertEquals(1, childModel.clearCount)
                assertEquals(0, parentModel.clearCount)
                assertSame(parentModel, fixture.scopes.get(parent, ScopeProbeViewModel::class))
                assertFalse(parentModel.work.isCancelled)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `flow 제거는 마지막 보존 token을 닫은 뒤 모든 ViewModel과 작업을 해제한다`() = runTest {
        val fixture = ScopeFixture(StandardTestDispatcher(testScheduler))
        try {
            val route = AppRoute.Login
            val retainedToken = fixture.acquire(route)
            val entryToken = fixture.acquire(route)
            val models = listOf(
                fixture.scopes.get(route, ScopeProbeViewModel::class),
                fixture.scopes.get(route, SecondaryScopeProbeViewModel::class),
            )
            runCurrent()

            fixture.provider.clearKey(route.viewModelStoreKey())
            entryToken.close()

            for (model in models) {
                assertEquals(0, model.clearCount)
                assertTrue(model.work.isActive)
            }

            retainedToken.close()
            runCurrent()

            for (model in models) {
                assertEquals(1, model.clearCount)
                assertTrue(model.work.isCancelled)
                assertTrue(model.workFinished)
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `수정 식별자마다 scope가 분리되고 완전히 나간 같은 알람의 재진입은 새로 생성한다`() = runTest {
        val fixture = ScopeFixture(StandardTestDispatcher(testScheduler))
        try {
            val firstRoute = AppRoute.EditAlarm("alarm/\"one\"")
            val otherRoute = AppRoute.EditAlarm("alarm/two")
            val firstToken = fixture.acquire(firstRoute)
            fixture.acquire(otherRoute)
            val first = fixture.scopes.get(firstRoute, ScopeProbeViewModel::class)
            val other = fixture.scopes.get(otherRoute, ScopeProbeViewModel::class)
            val previousStore = fixture.provider.getOrCreate(firstRoute.viewModelStoreKey())

            assertNotSame(first, other)
            assertSame(first, fixture.scopes.get(firstRoute.copy(), ScopeProbeViewModel::class))
            fixture.provider.clearKey(firstRoute.viewModelStoreKey())
            firstToken.close()
            fixture.acquire(firstRoute)
            val next = fixture.scopes.get(firstRoute, ScopeProbeViewModel::class)

            assertEquals(1, first.clearCount)
            assertNotSame(first, next)
            assertNotSame(previousStore, fixture.provider.getOrCreate(firstRoute.viewModelStoreKey()))
            assertEquals(0, next.clearCount)
            assertEquals(0, other.clearCount)
            assertSame(other, fixture.scopes.get(otherRoute, ScopeProbeViewModel::class))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `화면 token 해제와 같은 부모 store의 provider 재연결은 보존된 scope를 유지한다`() = runTest {
        val fixture = ScopeFixture(StandardTestDispatcher(testScheduler))
        try {
            val route = AppRoute.EditAlarm("alarm-1")
            val retainedToken = fixture.acquire(route)
            val entryToken = fixture.acquire(route)
            val original = fixture.scopes.get(route, ScopeProbeViewModel::class)

            entryToken.close()

            assertSame(original, fixture.scopes.get(route, ScopeProbeViewModel::class))
            assertEquals(0, original.clearCount)
            val reattachedProvider = ViewModelStoreProvider(
                parentStore = fixture.parentStore,
                parentKey = ScopeProviderKey,
                defaultFactory = fixture.factory,
            )
            val reattachedScopes = NavigationViewModelScopes(reattachedProvider, fixture.factory)
            val reattachedToken = fixture.acquire(route, reattachedProvider)
            retainedToken.close()

            assertSame(original, reattachedScopes.get(route, ScopeProbeViewModel::class))
            assertFalse(original.work.isCancelled)
            fixture.parentStore.clear()
            assertEquals(0, original.clearCount)
            reattachedToken.close()
            assertEquals(1, original.clearCount)
            assertTrue(original.work.isCancelled)
        } finally {
            fixture.close()
        }
    }
}

private class ScopeFixture(dispatcher: CoroutineDispatcher) : AutoCloseable {
    val parentStore = ViewModelStore()
    val factory = viewModelFactory {
        initializer { ScopeProbeViewModel(dispatcher) }
        initializer { SecondaryScopeProbeViewModel(dispatcher) }
    }
    val provider = ViewModelStoreProvider(
        parentStore = parentStore,
        parentKey = ScopeProviderKey,
        defaultFactory = factory,
    )
    val scopes = NavigationViewModelScopes(provider, factory)
    private val tokens = mutableListOf<AutoCloseable>()

    fun acquire(
        route: AppRoute,
        ownerProvider: ViewModelStoreProvider = provider,
    ): AutoCloseable {
        val token = ownerProvider.acquireToken(route.viewModelStoreKey())
        var isClosed = false
        return AutoCloseable {
            if (!isClosed) {
                isClosed = true
                token.close()
            }
        }.also(tokens::add)
    }

    override fun close() {
        tokens.forEach(AutoCloseable::close)
        parentStore.clear()
    }
}

private open class ScopeProbeViewModel(dispatcher: CoroutineDispatcher) :
    ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    var clearCount = 0
        private set
    var workFinished = false
        private set
    val work = viewModelScope.launch {
        try {
            awaitCancellation()
        } finally {
            workFinished = true
        }
    }

    override fun onCleared() {
        clearCount++
    }
}

private class SecondaryScopeProbeViewModel(dispatcher: CoroutineDispatcher) :
    ScopeProbeViewModel(dispatcher)

private const val ScopeProviderKey = "navigation-scope-test"
