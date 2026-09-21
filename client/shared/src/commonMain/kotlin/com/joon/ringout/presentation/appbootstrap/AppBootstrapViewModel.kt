package com.joon.ringout.presentation.appbootstrap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException

data class AppBootstrapUiState(
    val isReady: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Dark,
    val destination: AppEntryDestination? = null,
    val isSaving: Boolean = false,
    val onboardingRetryToken: Int = 0,
)

class AppBootstrapViewModel(
    private val repository: AppPreferencesRepository,
    private val systemThemeModeReader: SystemThemeModeReader,
    coroutineScope: CoroutineScope? = null,
    private val themeInitializationRetryDelayMillis: Long = ThemeInitializationRetryDelayMillis,
) : ViewModel() {
    var uiState by mutableStateOf(AppBootstrapUiState())
        private set

    private val scope = coroutineScope ?: viewModelScope
    private val themeWriteMutex = Mutex()
    private var latestRequestedThemeMode: ThemeMode? = null
    private var resolvedMissingThemeMode: ThemeMode? = null
    private var isThemeInitializationInProgress = false

    init {
        scope.launch {
            repository.bootstrapState
                .retryWhen { cause, _ ->
                    if (cause !is IOException) return@retryWhen false
                    delay(BootstrapReadRetryDelayMillis)
                    true
                }
                .collect { snapshot ->
                    val themeMode = snapshot.themeMode ?: resolveMissingThemeMode()
                    if (latestRequestedThemeMode == null) {
                        latestRequestedThemeMode = themeMode
                    }
                    uiState = uiState.copy(
                        isReady = true,
                        themeMode = themeMode,
                        destination = determineAppEntryDestination(snapshot.firstLaunchStatus),
                    )
                    if (snapshot.themeMode == null) {
                        initializeThemeModeIfMissing(themeMode)
                    }
                }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        if (latestRequestedThemeMode == themeMode) return
        latestRequestedThemeMode = themeMode

        scope.launch {
            themeWriteMutex.withLock {
                try {
                    repository.setThemeMode(themeMode)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    if (latestRequestedThemeMode == themeMode) {
                        latestRequestedThemeMode = uiState.themeMode
                    }
                }
            }
        }
    }

    private fun resolveMissingThemeMode(): ThemeMode =
        resolvedMissingThemeMode ?: systemThemeModeReader.read().also {
            resolvedMissingThemeMode = it
        }

    private fun initializeThemeModeIfMissing(themeMode: ThemeMode) {
        if (isThemeInitializationInProgress) return
        isThemeInitializationInProgress = true

        scope.launch {
            try {
                while (true) {
                    try {
                        themeWriteMutex.withLock {
                            repository.initializeThemeModeIfMissing(themeMode)
                        }
                        return@launch
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: IOException) {
                        delay(themeInitializationRetryDelayMillis)
                    }
                }
            } finally {
                isThemeInitializationInProgress = false
            }
        }
    }

    fun completeOnboarding() = save(
        block = repository::markOnboardingCompleted,
        onFailure = {
            uiState = uiState.copy(
                onboardingRetryToken = uiState.onboardingRetryToken + 1,
            )
        },
    )

    private fun save(
        block: suspend () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        if (uiState.isSaving) return
        uiState = uiState.copy(isSaving = true)
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                onFailure()
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}

private const val BootstrapReadRetryDelayMillis = 1_000L
private const val ThemeInitializationRetryDelayMillis = 1_000L
