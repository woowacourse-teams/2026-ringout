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
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import kotlin.time.Clock

data class AppBootstrapUiState(
    val isReady: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Dark,
    val destination: AppEntryDestination? = null,
    val isSaving: Boolean = false,
    val onboardingRetryToken: Int = 0,
)

class AppBootstrapViewModel(
    private val repository: AppPreferencesRepository,
    private val clock: Clock = Clock.System,
    private val terms: List<TermDefinition> = currentTerms,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(AppBootstrapUiState())
        private set

    private val scope = coroutineScope ?: viewModelScope
    private val themeWriteMutex = Mutex()
    private var latestRequestedThemeMode: ThemeMode? = null

    init {
        scope.launch {
            repository.bootstrapState
                .retryWhen { cause, _ ->
                    if (cause !is IOException) return@retryWhen false
                    delay(BootstrapReadRetryDelayMillis)
                    true
                }
                .collect { snapshot ->
                    if (latestRequestedThemeMode == null) {
                        latestRequestedThemeMode = snapshot.themeMode
                    }
                    uiState = uiState.copy(
                        isReady = true,
                        themeMode = snapshot.themeMode,
                        destination = determineAppEntryDestination(
                            snapshot.firstLaunchStatus,
                            terms,
                        ),
                    )
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

    fun completeOnboarding() = save(
        block = repository::markOnboardingCompleted,
        onFailure = {
            uiState = uiState.copy(
                onboardingRetryToken = uiState.onboardingRetryToken + 1,
            )
        },
    )

    fun completeTermsAgreement(agreedTermIds: Set<TermId>) {
        val requiredTermIds = terms
            .filter { definition -> definition.type == TermType.REQUIRED }
            .mapTo(mutableSetOf(), TermDefinition::id)
        if (!agreedTermIds.containsAll(requiredTermIds)) return

        save(block = {
            val agreedAtEpochMillis = clock.now().toEpochMilliseconds()
            repository.saveTermConsents(
                terms.map { definition ->
                    val isAgreed = definition.id in agreedTermIds
                    TermConsentRecord(
                        id = definition.id,
                        termName = definition.name,
                        termVersion = definition.version,
                        termType = definition.type,
                        isAgreed = isAgreed,
                        agreedAtEpochMillis = agreedAtEpochMillis.takeIf { isAgreed },
                    )
                },
            )
        })
    }

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
