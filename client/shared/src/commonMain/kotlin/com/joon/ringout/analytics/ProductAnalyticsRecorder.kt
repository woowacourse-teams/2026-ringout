package com.joon.ringout.analytics

enum class AnalyticsLoginState(
    internal val wireName: String,
) {
    LoggedIn("logged_in"),
    LoggedOut("logged_out"),
}

enum class DestinationSelectionSource(
    internal val wireName: String,
) {
    MapShortcut("map_shortcut"),
    SearchShortcut("search_shortcut"),
}

enum class StampMonthChangeDirection(
    internal val wireName: String,
) {
    Previous("previous"),
    Next("next"),
}

enum class AnalyticsAuthProvider(
    internal val wireName: String,
) {
    Google("google"),
    Kakao("kakao"),
    Apple("apple"),
}

interface ProductAnalyticsRecorder {
    fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    )

    fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    )

    fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    )

    fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    )

    fun recordAccountWithdrawalCompleted()

    fun recordLoginStarted(provider: AnalyticsAuthProvider)

    fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    )

    fun recordSignupCompleted(provider: AnalyticsAuthProvider)
}

internal fun interface ProductAnalyticsUsageStore {
    fun claimDestinationCreation(destinationKey: String): Long?
}

internal class DefaultProductAnalyticsRecorder(
    private val tracker: AnalyticsTracker,
    private val usageStore: ProductAnalyticsUsageStore,
) : ProductAnalyticsRecorder {
    override fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        if (destinationId <= 0L) return@safelyRecord
        val creationIndex = usageStore.claimDestinationCreation(
            destinationKey = "${loginState.wireName}:$destinationId",
        ) ?: return@safelyRecord
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationCreated,
                parameters = mapOf(
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                    AnalyticsParameterName.CreationIndex to
                        AnalyticsParameterValue.Number(creationIndex),
                ),
            ),
        )
    }

    override fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationSelected,
                parameters = mapOf(
                    AnalyticsParameterName.Source to
                        AnalyticsParameterValue.Text(source.wireName),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.StampCalendarViewed,
                parameters = mapOf(
                    AnalyticsParameterName.Year to AnalyticsParameterValue.Number(year.toLong()),
                    AnalyticsParameterName.Month to AnalyticsParameterValue.Number(month.toLong()),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.StampMonthChanged,
                parameters = mapOf(
                    AnalyticsParameterName.Direction to
                        AnalyticsParameterValue.Text(direction.wireName),
                    AnalyticsParameterName.Year to AnalyticsParameterValue.Number(year.toLong()),
                    AnalyticsParameterName.Month to AnalyticsParameterValue.Number(month.toLong()),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordAccountWithdrawalCompleted() = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.AccountWithdrawalCompleted,
                parameters = emptyMap(),
            ),
        )
    }

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.LoginStarted,
                parameters = mapOf(
                    AnalyticsParameterName.Provider to
                        AnalyticsParameterValue.Text(provider.wireName),
                ),
            ),
        )
    }

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.LoginCompleted,
                parameters = mapOf(
                    AnalyticsParameterName.Provider to
                        AnalyticsParameterValue.Text(provider.wireName),
                    AnalyticsParameterName.IsNewUser to
                        AnalyticsParameterValue.Number(isNewUser.toAnalyticsNumber()),
                ),
            ),
        )
    }

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.SignupCompleted,
                parameters = mapOf(
                    AnalyticsParameterName.Provider to
                        AnalyticsParameterValue.Text(provider.wireName),
                ),
            ),
        )
    }

    private inline fun safelyRecord(action: () -> Unit) {
        runCatching(action)
    }
}

private fun Boolean.toAnalyticsNumber(): Long = if (this) 1L else 0L
