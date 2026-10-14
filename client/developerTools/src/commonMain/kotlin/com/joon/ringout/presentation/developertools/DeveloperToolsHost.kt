package com.joon.ringout.presentation.developertools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.developertools.component.DeveloperToolsEntry
import com.joon.ringout.presentation.developertools.component.DeveloperToolsPendingContent

@Composable
fun DeveloperToolsHost(
    toolsContent: @Composable (onBack: () -> Unit) -> Unit,
    content: @Composable (extraContent: @Composable (ThemeMode) -> Unit) -> Unit,
) {
    var toolsThemeName by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        // Preserve navigation while hiding the covered app from accessibility services.
        Box(if (toolsThemeName != null) Modifier.clearAndSetSemantics {} else Modifier) {
            content { themeMode ->
                DeveloperToolsEntry(onClick = { toolsThemeName = themeMode.name })
            }
        }
        toolsThemeName?.let { themeName ->
            val owner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                RingoutTheme(ThemeMode.valueOf(themeName)) {
                    toolsContent { toolsThemeName = null }
                }
            }
            DeveloperToolsBackHandler { toolsThemeName = null }
        }
    }
}

@Composable
internal expect fun DeveloperToolsBackHandler(onBack: () -> Unit)

@Preview(showBackground = true)
@Composable
private fun DeveloperToolsHostPreview() {
    RingoutTheme(ThemeMode.Light) {
        DeveloperToolsHost(toolsContent = { DeveloperToolsPendingContent(onBack = it) }) { entry ->
            entry(ThemeMode.Light)
        }
    }
}
