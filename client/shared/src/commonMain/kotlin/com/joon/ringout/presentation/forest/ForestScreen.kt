package com.joon.ringout.presentation.forest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.forest.component.ForestRefreshStatus
import com.joon.ringout.presentation.forest.component.ForestSyncError
import com.joon.ringout.presentation.forest.component.ForestStatus
import com.joon.ringout.presentation.navigation.NavigationBackAction
import kotlinx.coroutines.delay

internal class ForestWebController { var evaluate: ((String) -> Unit)? = null }

@Composable
internal fun ForestRoute(viewModel: ForestViewModel, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val endpoint = remember { ForestEndpoint.parse(ForestBuildConfig.WEB_URL) }
    val dataState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(viewModel, lifecycle, endpoint) {
        if (endpoint != null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { if (endpoint != null) viewModel.setActive(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.setActive(false) }
    key(dataState.sessionVersion, attempt) {
        var pageReady by remember { mutableStateOf(false) }
        var receivedReady by remember { mutableStateOf(false) }
        var pageGeneration by remember { mutableIntStateOf(0) }
        var pageError by remember {
            mutableStateOf(if (endpoint == null) "숲 화면 주소가 아직 설정되지 않았어요." else null)
        }
        val web = remember { ForestWebController() }
        val currentBack by rememberUpdatedState(onBackClick)
        val connection = remember {
            viewModel.connect(
                send = { web.evaluate?.invoke(ForestProtocol.deliveryScript(it)) },
                onReady = { pageReady = true },
                onBack = { currentBack() },
                onError = { pageError = "숲 화면에 연결하지 못했어요." },
                onWebReady = { receivedReady = true },
                onReloadRequired = { attempt++ },
            )
        }
        DisposableEffect(connection) {
            connection.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            onDispose { connection.close(); web.evaluate = null }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { connection.setActive(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { connection.setActive(true) }
        NavigationBackAction { if (pageError != null || dataState.errorMessage != null || !connection.back()) currentBack() }
        LaunchedEffect(pageGeneration, receivedReady, pageError) {
            if (!receivedReady && pageError == null && endpoint != null) {
                delay(20_000)
                connection.close()
                pageError = "숲 화면에 연결하지 못했어요. 연결 상태를 확인해 주세요."
            }
        }
        val state = dataState.copy(
            isLoading = pageError == null && dataState.errorMessage == null && (!pageReady || !dataState.hasSnapshot),
            errorMessage = pageError ?: dataState.errorMessage,
        )
        ForestScreen(
            uiState = state,
            onRetry = {
                if (pageError != null) {
                    attempt++
                    if (dataState.errorMessage != null) viewModel.retry()
                } else viewModel.retry()
            },
            onBackClick = onBackClick,
            modifier = modifier,
            onRetryOnboardingSync = viewModel::retryOnboardingSync,
            onRetryTreeSync = viewModel::retryTreeSync,
            onRetryPlantingSync = viewModel::retryPlantingSync,
        ) {
            if (endpoint != null) PlatformForestWebView(
                endpoint = endpoint,
                controller = web,
                onMessage = connection::receive,
                onPageStarted = {
                    connection.reset()
                    pageReady = false
                    receivedReady = false
                    pageError = null
                    pageGeneration++
                },
                onError = { connection.close(); pageReady = false; pageError = it },
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
    onRetryOnboardingSync: () -> Unit = {},
    onRetryTreeSync: () -> Unit = {},
    onRetryPlantingSync: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            content()
            if (uiState.isLoading || uiState.errorMessage != null) {
                ForestStatus(uiState, onRetry, onBackClick, Modifier.fillMaxSize())
            } else {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (uiState.isRefreshing || uiState.refreshErrorMessage != null) {
                        ForestRefreshStatus(uiState, onRetry, Modifier.fillMaxWidth())
                    }
                    uiState.onboardingSyncErrorMessage?.let { message ->
                        ForestSyncError(
                            message = message,
                            isRetrying = uiState.isRetryingOnboardingSync,
                            onRetry = onRetryOnboardingSync,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    uiState.treeSyncErrorMessage?.let { message ->
                        ForestSyncError(
                            message = message,
                            isRetrying = uiState.isRetryingTreeSync,
                            onRetry = onRetryTreeSync,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    uiState.plantingSyncErrorMessage?.let { message ->
                        ForestSyncError(
                            message = message,
                            isRetrying = uiState.isRetryingPlantingSync,
                            onRetry = onRetryPlantingSync,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
@Preview
@Composable
private fun ForestScreenRefreshErrorPreview() {
    RingoutTheme { ForestScreen(ForestUiState(isLoading = false, hasSnapshot = true, refreshErrorMessage = "숲을 새로 불러오지 못했어요."), {}, {}) }
}

@Preview
@Composable
private fun ForestScreenOnboardingSyncErrorPreview() {
    RingoutTheme {
        ForestScreen(
            ForestUiState(
                isLoading = false,
                hasSnapshot = true,
                refreshErrorMessage = "숲을 새로 불러오지 못했어요.",
                onboardingSyncErrorMessage = "시작 기록을 서버에 저장하지 못했어요.",
            ),
            {},
            {},
        )
    }
}

@Preview
@Composable
private fun ForestScreenTreeSyncErrorPreview() {
    RingoutTheme {
        ForestScreen(
            ForestUiState(
                isLoading = false,
                hasSnapshot = true,
                onboardingSyncErrorMessage = "시작 기록을 서버에 저장하지 못했어요.",
                treeSyncErrorMessage = "나무 확인 기록을 서버에 저장하지 못했어요.",
            ),
            {},
            {},
        )
    }
}

@Preview
@Composable
private fun ForestScreenPlantingSyncErrorPreview() {
    RingoutTheme {
        ForestScreen(
            ForestUiState(
                isLoading = false,
                hasSnapshot = true,
                plantingSyncErrorMessage = "나무를 서버에 저장하지 못했어요.",
            ),
            {},
            {},
        )
    }
}
