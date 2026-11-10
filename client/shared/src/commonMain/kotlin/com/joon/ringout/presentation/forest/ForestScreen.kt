package com.joon.ringout.presentation.forest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.forest.component.ForestStatus
import com.joon.ringout.presentation.navigation.NavigationBackAction
import kotlinx.coroutines.delay

internal data class ForestUiState(val isLoading: Boolean = true, val errorMessage: String? = null)
internal class ForestWebController { var evaluate: ((String) -> Unit)? = null }

@Composable
internal fun ForestRoute(viewModel: ForestViewModel, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val endpoint = remember { ForestEndpoint.parse(ForestBuildConfig.WEB_URL) }
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) {
        var state by remember { mutableStateOf(ForestUiState(errorMessage = if (endpoint == null) "숲 화면 주소가 아직 설정되지 않았어요." else null)) }
        val web = remember { ForestWebController() }
        val currentBack by rememberUpdatedState(onBackClick)
        val connection = remember {
            viewModel.connect(
                send = { web.evaluate?.invoke(ForestProtocol.deliveryScript(it)) },
                onReady = { state = ForestUiState(isLoading = false) },
                onBack = { currentBack() },
                onError = { state = ForestUiState(false, "숲 기록을 불러오지 못했어요.") },
            )
        }
        DisposableEffect(connection) { onDispose { connection.close(); web.evaluate = null } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { connection.setActive(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { connection.setActive(true) }
        NavigationBackAction { if (state.errorMessage != null || !connection.back()) currentBack() }
        LaunchedEffect(state.isLoading) {
            if (state.isLoading && endpoint != null) {
                delay(20_000)
                state = ForestUiState(false, "숲 화면에 연결하지 못했어요. 연결 상태를 확인해 주세요.")
            }
        }
        ForestScreen(
            uiState = state,
            onRetry = { attempt++ },
            onBackClick = onBackClick,
            modifier = modifier,
        ) {
            if (endpoint != null) PlatformForestWebView(
                endpoint = endpoint,
                controller = web,
                onMessage = connection::receive,
                onPageStarted = { connection.reset(); state = ForestUiState() },
                onError = { connection.reset(); state = ForestUiState(false, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ForestScreen(
    uiState: ForestUiState,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            content()
            if (uiState.isLoading || uiState.errorMessage != null) {
                ForestStatus(uiState, onRetry, onBackClick, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal expect fun PlatformForestWebView(
    endpoint: ForestEndpoint,
    controller: ForestWebController,
    onMessage: (String) -> Unit,
    onPageStarted: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Preview
@Composable
private fun ForestScreenPreview() { RingoutTheme { ForestScreen(ForestUiState(), {}, {}) } }
@Preview
@Composable
private fun ForestScreenErrorPreview() { RingoutTheme { ForestScreen(ForestUiState(false, "숲 화면에 연결하지 못했어요."), {}, {}) } }
