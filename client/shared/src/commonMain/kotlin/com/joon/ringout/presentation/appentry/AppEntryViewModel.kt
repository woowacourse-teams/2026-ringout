package com.joon.ringout.presentation.appentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.FirstLaunchRepository
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okio.IOException
import kotlin.time.Clock

data class AppEntryUiState(
    val isLoading: Boolean = true,
    val destination: AppEntryDestination? = null,
    val isSaving: Boolean = false,
    val onboardingRetryToken: Int = 0,
)

class AppEntryViewModel(
    private val repository: FirstLaunchRepository,
    private val clock: Clock = Clock.System,
    private val terms: List<TermDefinition> = currentTerms,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(AppEntryUiState())
        private set

    private val scope = coroutineScope ?: viewModelScope

    init {
        scope.launch {
            repository.status.collect { status ->
                uiState = uiState.copy(
                    isLoading = false,
                    destination = determineAppEntryDestination(status, terms),
                )
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
