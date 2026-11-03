package com.joon.ringout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.defaultViewModelCreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider

/** Keeps navigation drafts alive when a platform gate temporarily removes App from composition. */
@Composable
fun AppStateHost(content: @Composable () -> Unit) {
    val stores = rememberAppNavigationStoreProvider()
    CompositionLocalProvider(LocalAppNavigationStoreProvider provides stores, content = content)
}

internal val LocalAppNavigationStoreProvider = staticCompositionLocalOf<ViewModelStoreProvider?> { null }

@Composable
internal fun rememberAppNavigationStoreProvider(): ViewModelStoreProvider {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "CompositionLocal LocalViewModelStoreOwner not present"
    }
    // Android Intent extras are a new Bundle on each read; keep the creation extras stable.
    val creationExtras = remember(parent) { parent.defaultViewModelCreationExtras }
    return rememberViewModelStoreProvider(
        key = "ringout-navigation",
        parent = parent,
        defaultCreationExtras = creationExtras,
    )
}
