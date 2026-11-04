package com.joon.ringout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.defaultViewModelCreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider

/** Keeps navigation drafts alive when a platform gate temporarily removes App from composition. */
@Composable
fun AppStateHost(sessionIdentity: Any? = null, content: @Composable () -> Unit) {
    var hadSession by rememberSaveable { mutableStateOf(sessionIdentity != null) }
    var authGeneration by rememberSaveable { mutableLongStateOf(0L) }
    // 로그인 성공 이벤트를 처리할 때까지 인증 모델은 유지하고, 로그아웃 시 교체한다.
    if (hadSession && sessionIdentity == null) authGeneration++
    hadSession = sessionIdentity != null
    // 업데이트 차단 중에도 유지하고, 로그인 세션이 바뀌면 기존 서비스 모델을 정리한다.
    val authStores = rememberAppNavigationStoreProvider("auth", authGeneration)
    val serviceStores = rememberAppNavigationStoreProvider("service", sessionIdentity)
    val stores = remember(authStores, serviceStores) {
        mapOf("auth" to authStores, "service" to serviceStores)
    }
    CompositionLocalProvider(LocalAppNavigationStoreProviders provides stores, content = content)
}

internal val LocalAppNavigationStoreProviders =
    staticCompositionLocalOf<Map<String, ViewModelStoreProvider>?> { null }

@Composable
internal fun rememberAppNavigationStoreProvider(
    scopeKey: String,
    scopeIdentity: Any?,
): ViewModelStoreProvider {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "CompositionLocal LocalViewModelStoreOwner not present"
    }
    // Android Intent extras are a new Bundle on each read; keep the creation extras stable.
    val creationExtras = remember(parent) { parent.defaultViewModelCreationExtras }
    return rememberViewModelStoreProvider(
        key = NavigationViewModelScopeKey(scopeKey, scopeIdentity),
        parent = parent,
        defaultCreationExtras = creationExtras,
    )
}

private data class NavigationViewModelScopeKey(
    val scope: String,
    val scopeIdentity: Any?,
)
